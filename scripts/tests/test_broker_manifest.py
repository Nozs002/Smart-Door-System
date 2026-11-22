import importlib.util
from pathlib import Path
import json
import unittest
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('broker_supervisor', ROOT / 'infrastructure/mosquitto/config/supervise.py')
supervisor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(supervisor)


class BrokerManifestTests(unittest.TestCase):
    def test_only_auth_refusal_is_valid_proof(self):
        for packet, expected in ((b'\x20\x02\x00\x04', True), (b'\x20\x02\x00\x05', True),
                                 (b'\x20\x02\x00\x00', False), (b'\x20\x02\x00\x03', False)):
            with self.subTest(packet=packet), patch.object(supervisor.socket, 'create_connection') as connect:
                connection = connect.return_value.__enter__.return_value
                connection.recv.return_value = packet
                self.assertEqual(supervisor.authentication_rejected('old-user', 'private-test-password'), expected)
                self.assertTrue(connection.sendall.call_args[0][0].startswith(b'\x10'))

    def test_network_failure_never_becomes_authentication_proof(self):
        with patch.object(supervisor.socket, 'create_connection', side_effect=ConnectionRefusedError):
            with self.assertRaises(ConnectionRefusedError):
                supervisor.authentication_rejected('old-user', 'private-test-password')

    def test_revocation_restarts_broker_and_writes_proof_only_after_rejection(self):
        record = {'deviceId': 1, 'credentialId': '11111111-1111-4111-8111-111111111111',
                  'username': 'device-1-11111111-1111-4111-8111-111111111111',
                  'password': 'test-only-private', 'revokedAt': '2026-11-18T00:00:00'}
        content = json.dumps({'users': [], 'revocations': [record]})
        for rejected in (True, False):
            with self.subTest(rejected=rejected), patch.object(supervisor, 'prepare') as prepare, \
                    patch.object(supervisor, 'stop_broker') as stop, \
                    patch.object(supervisor.subprocess, 'Popen') as start, \
                    patch.object(supervisor, 'authentication_rejected', return_value=rejected), \
                    patch.object(supervisor, 'write_proof') as write:
                previous = MagicMock()
                broker, success = supervisor.apply_manifest(content, previous)
                prepare.assert_called_once_with([])
                stop.assert_called_once_with(previous)
                self.assertIs(broker, start.return_value)
                self.assertEqual(success, rejected)
                proofs = write.call_args[0][0]
                self.assertEqual(len(proofs), 1 if rejected else 0)
                if proofs:
                    self.assertNotIn('password', proofs[0])
                    self.assertTrue(proofs[0]['sessionsDisconnected'])

    def test_legacy_manifest_reloads_without_claiming_session_revocation(self):
        with patch.object(supervisor, 'prepare'), patch.object(supervisor, 'stop_broker') as stop, \
                patch.object(supervisor.signal, 'SIGHUP', 1, create=True):
            broker = MagicMock()
            current, success = supervisor.apply_manifest('[]', broker)
            self.assertIs(current, broker)
            self.assertTrue(success)
            stop.assert_not_called()
            broker.send_signal.assert_called_once()


if __name__ == '__main__':
    unittest.main()

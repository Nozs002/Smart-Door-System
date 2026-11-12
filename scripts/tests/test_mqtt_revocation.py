import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import json

spec = importlib.util.spec_from_file_location("mqtt_setup", Path(__file__).resolve().parents[1] / "setup-mqtt.py")
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class RevocationTests(unittest.TestCase):
    config = {"MQTT_USERNAME": "backend", "MQTT_PASSWORD": "test-only"}
    old = {"username": "old-node", "password": "test-only", "credentialId": "old-id"}

    def test_only_explicit_auth_rejection_is_proof(self):
        for rejection in (134, 135):
            with patch.object(setup, "authentication_result", side_effect=[0, rejection]):
                setup.confirm_rejection(self.config, self.old)
        for result in (0, 128, 136):
            with patch.object(setup, "authentication_result", side_effect=[0, result]):
                with self.assertRaises(RuntimeError):
                    setup.confirm_rejection(self.config, self.old)

    def test_unreachable_broker_is_not_revocation(self):
        with patch.object(setup, "authentication_result", side_effect=OSError("unreachable")), patch.object(setup.time, "sleep"):
            with self.assertRaises(OSError):
                setup.confirm_rejection(self.config, self.old)

    def test_failed_probe_never_confirms_database(self):
        with tempfile.TemporaryDirectory() as directory:
            private = Path(directory) / "private.json"
            private.write_text(json.dumps(self.old), encoding="utf-8")
            with patch.object(setup, "environment", return_value=self.config), \
                 patch.object(setup, "authentication_result", return_value=0), \
                 patch.object(setup, "cli", return_value=private) as cli, \
                 patch.object(setup, "configure"), patch.object(setup, "run"), \
                 patch.object(setup, "confirm_rejection", side_effect=RuntimeError("no rejection")):
                with self.assertRaises(RuntimeError):
                    setup.revoke(1, "test")
                self.assertEqual([call.args[0] for call in cli.call_args_list], ["revoke", "export-broker"])

    def test_success_confirms_exact_identity_after_restart_and_rejection(self):
        with tempfile.TemporaryDirectory() as directory:
            private = Path(directory) / "private.json"
            private.write_text(json.dumps(self.old), encoding="utf-8")
            events = []
            def cli(action, *args):
                events.append(action)
                if action == "confirm-revocation":
                    self.assertEqual(args[-1], "old-id")
                return private
            with patch.object(setup, "environment", return_value=self.config), \
                 patch.object(setup, "authentication_result", return_value=0), \
                 patch.object(setup, "cli", side_effect=cli), \
                 patch.object(setup, "configure", side_effect=lambda _: events.append("configure")), \
                 patch.object(setup, "run", side_effect=lambda _: events.append("restart")), \
                 patch.object(setup, "confirm_rejection", side_effect=lambda *_: events.append("rejected")):
                setup.revoke(1, "test")
            self.assertEqual(events, ["revoke", "export-broker", "configure", "restart", "rejected", "confirm-revocation"])

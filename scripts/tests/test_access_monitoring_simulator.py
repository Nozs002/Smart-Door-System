"""Exercise signed access/telemetry publication through the actual simulator main loop."""
import base64
import contextlib
import hashlib
import hmac
import io
import json
from pathlib import Path
import runpy
import tempfile
import types
import unittest
import uuid
from unittest.mock import patch


class AccessMonitoringSimulatorTests(unittest.TestCase):
    def run_node(self, node_type, options):
        published = []

        class Client:
            def __init__(self, *args, **kwargs): pass
            def username_pw_set(self, *args): pass
            def connect(self, *args, **kwargs): pass
            def subscribe(self, *args, **kwargs): pass
            def publish(self, topic, value, **kwargs):
                published.append((topic, json.loads(value), kwargs))
            def loop_start(self):
                self.on_connect(self, None, None, types.SimpleNamespace(is_failure=False), None)
            def disconnect(self): pass
            def loop_stop(self): pass

        mqtt = types.ModuleType("paho.mqtt.client")
        mqtt.Client = Client
        mqtt.CallbackAPIVersion = types.SimpleNamespace(VERSION2=2)
        paho, package = types.ModuleType("paho"), types.ModuleType("paho.mqtt")
        paho.mqtt, package.client = package, mqtt
        output = io.StringIO()
        with tempfile.TemporaryDirectory() as directory:
            private = Path(directory) / "credential.json"
            private.write_text(json.dumps({"signingKey": base64.b64encode(b"x" * 32).decode(), "nodeType": node_type,
                "doorId": 8, "deviceCode": "TEST-NODE", "deviceId": 1, "credentialId": str(uuid.uuid4()),
                "username": "test-only", "password": "test-only"}))
            module = runpy.run_path(str(Path(__file__).resolve().parents[1] / "device-simulator.py"))
            module["main"].__globals__["ROOT"] = Path(directory)
            with patch.dict("sys.modules", {"paho": paho, "paho.mqtt": package, "paho.mqtt.client": mqtt}), \
                 patch("sys.argv", ["device-simulator.py", str(private), "--duration", "1", *options]), \
                 patch("getpass.getpass", return_value="test-credential-never-print"), contextlib.redirect_stdout(output):
                module["main"]()
        return published, output.getvalue()

    def test_access_retries_keep_event_id_and_are_signed_without_logging_credential(self):
        published, output = self.run_node("NODE_1", ["--access-method", "PIN", "--retry-access"])
        access = [(t, e, o) for t, e, o in published if t.endswith("/access-request")]
        self.assertEqual(len(access), 2)
        payloads = [json.loads(base64.b64decode(e["payload"])) for _, e, _ in access]
        self.assertEqual(payloads[0], payloads[1])
        self.assertEqual(payloads[0]["method"], "PIN")
        self.assertNotIn("test-credential-never-print", output)
        self.assertNotEqual(access[0][1]["nonce"], access[1][1]["nonce"])
        for topic, envelope, options in access:
            canonical = "\n".join([topic, envelope["credentialId"], str(envelope["sentAt"]), envelope["nonce"], envelope["payload"]])
            self.assertEqual(envelope["signature"], hmac.new(b"x" * 32, canonical.encode(), hashlib.sha256).hexdigest())
            self.assertFalse(options["retain"])

    def test_node_two_sends_observed_states_without_deriving_them_from_unlock(self):
        published, _ = self.run_node("NODE_2", ["--humidity", "65", "--door-state", "CLOSED", "--lock-state", "UNKNOWN"])
        readings = [json.loads(base64.b64decode(e["payload"])) for t, e, _ in published if t.endswith("/telemetry")]
        self.assertEqual(len(readings), 1)
        self.assertEqual(readings[0]["humidity"], 65)
        self.assertEqual(readings[0]["doorState"], "CLOSED")
        self.assertEqual(readings[0]["lockState"], "UNKNOWN")
        self.assertIsNone(readings[0]["temperature"])


if __name__ == "__main__":
    unittest.main()

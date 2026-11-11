"""Exercise the simulator's MQTT callback without a live broker or credentials."""
import base64
import contextlib
from datetime import datetime, timedelta, timezone
import io
import json
from pathlib import Path
import runpy
import tempfile
import types
import unittest
import uuid
from unittest.mock import patch


class UnlockSimulatorTests(unittest.TestCase):
    def simulate(self, result="EXECUTED", expired=False, retained=False, action="UNLOCK"):
        published = []
        command_id = str(uuid.uuid4())
        expires = datetime.now(timezone.utc) + timedelta(seconds=-1 if expired else 60)
        payload = json.dumps({"schemaVersion": 1, "commandId": command_id, "action": action, "expiresAt": expires.isoformat()}).encode()

        class Client:
            def __init__(self, *args, **kwargs): pass
            def username_pw_set(self, *args): pass
            def connect(self, *args, **kwargs): pass
            def subscribe(self, *args, **kwargs): pass
            def publish(self, topic, value, **kwargs):
                published.append((topic, json.loads(value)))
            def loop_start(self):
                self.on_connect(self, None, None, types.SimpleNamespace(is_failure=False), None)
                message = types.SimpleNamespace(topic="test/commands/" + ("close" if action == "CLOSE" else "unlock"), payload=payload, retain=retained)
                self.on_message(self, None, message)
                self.on_message(self, None, message)
            def disconnect(self): pass
            def loop_stop(self): pass

        mqtt = types.ModuleType("paho.mqtt.client")
        mqtt.Client = Client
        mqtt.CallbackAPIVersion = types.SimpleNamespace(VERSION2=2)
        paho = types.ModuleType("paho")
        package = types.ModuleType("paho.mqtt")
        paho.mqtt = package
        package.client = mqtt
        with tempfile.TemporaryDirectory() as directory:
            credential = Path(directory) / "credential.json"
            credential.write_text(json.dumps({"signingKey": base64.b64encode(b"x" * 32).decode(), "nodeType": "NODE_2",
                "doorId": 1, "deviceCode": "SIMULATOR", "deviceId": 1, "credentialId": str(uuid.uuid4()),
                "username": "test", "password": "test-only"}))
            module = runpy.run_path(str(Path(__file__).resolve().parents[1] / "device-simulator.py"))
            module["main"].__globals__["ROOT"] = Path(directory)
            with patch.dict("sys.modules", {"paho": paho, "paho.mqtt": package, "paho.mqtt.client": mqtt}), \
                 patch("sys.argv", ["device-simulator.py", str(credential), "--duration", "1", "--unlock-result", result, "--close-result", result]), \
                 contextlib.redirect_stdout(io.StringIO()):
                module["main"]()
            state_file = Path(directory) / ".local/simulator-1.local.json"
            state = json.loads(state_file.read_text()) if state_file.exists() else {}
        acks = [json.loads(base64.b64decode(value["payload"])) for topic, value in published if topic.endswith("/command-ack")]
        return acks, state, command_id

    def test_duplicate_delivery_reuses_one_persisted_result(self):
        acks, state, command_id = self.simulate()
        self.assertEqual(len(acks), 2)
        self.assertEqual(acks[0], acks[1])
        self.assertEqual(state["unlockResults"], {command_id: "EXECUTED"})

    def test_rejected_and_silent_modes(self):
        acks, _, _ = self.simulate(result="REJECTED")
        self.assertEqual(acks[0]["result"], "REJECTED")
        self.assertEqual(self.simulate(result="silent")[0], [])

    def test_expired_or_retained_commands_are_never_executed(self):
        self.assertEqual(self.simulate(expired=True)[0], [])
        self.assertEqual(self.simulate(retained=True)[0], [])

    def test_close_is_idempotent_and_respects_expiry_retained_and_rejection(self):
        acks, state, command_id = self.simulate(action="CLOSE")
        self.assertEqual(acks[0], acks[1])
        self.assertEqual(state["unlockResults"][command_id], "EXECUTED")
        self.assertEqual(self.simulate(action="CLOSE", expired=True)[0], [])
        self.assertEqual(self.simulate(action="CLOSE", retained=True)[0], [])
        self.assertEqual(self.simulate(action="CLOSE", result="REJECTED")[0][0]["result"], "REJECTED")


if __name__ == "__main__":
    unittest.main()

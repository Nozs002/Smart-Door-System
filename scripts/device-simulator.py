"""Device simulator: snapshots, signed events and simulated Node 2 unlock ACKs. No physical relay."""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
from pathlib import Path
import signal
import getpass
import math
import ssl
import sys
import threading
import time
import uuid
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]


def simulated_alert(config: dict, *, temperature=None, open_seconds=None, failures=False):
    """Evaluate simulated sensors against the applied configuration, never physical hardware."""
    if temperature is not None and config.get("fireAlarmEnabled", True) and temperature > config["temperatureThreshold"]:
        return {"type": "HIGH_TEMPERATURE", "observedTemperature": temperature}
    timeout = config.get("openTimeoutSeconds")
    if open_seconds is not None and config.get("doorOpenAlertEnabled", False) and timeout is not None and open_seconds > timeout:
        return {"type": "DOOR_OPEN_TOO_LONG"}
    if failures:
        return {"type": "TOO_MANY_FAILURES"}
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("credential", type=Path, help="Private file produced by setup-mqtt.py issue.")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=1884, help="Docker host MQTT port (MQTT_HOST_PORT); Broker internal port stays 1883.")
    parser.add_argument("--tls", action="store_true")
    parser.add_argument("--cafile", type=Path)
    parser.add_argument("--duration", type=int, default=120)
    parser.add_argument("--temperature", type=float, help="Emit one Node 2 HIGH_TEMPERATURE event after connecting; activate the Node first.")
    parser.add_argument("--door-open-seconds", type=int, help="Simulate Node 2 door-open duration against applied timeout.")
    parser.add_argument("--failures", action="store_true", help="Emit one Node 1 TOO_MANY_FAILURES test event; activate the Node first.")
    parser.add_argument("--event-id", help="Repeat an event ID to verify alert deduplication (keep the same payload).")
    parser.add_argument("--unlock-result", choices=("EXECUTED", "REJECTED", "silent"), default="EXECUTED", help="Node 2 simulated outcome; silent tests timeouts.")
    parser.add_argument("--access-method", choices=("RFID", "PIN"), help="Node 1: read credential interactively; it is never printed or passed in command arguments.")
    parser.add_argument("--attempts", type=int, default=1, help="Number of distinct access attempts, separated by --attempt-interval.")
    parser.add_argument("--attempt-interval", type=float, default=2)
    parser.add_argument("--retry-access", action="store_true", help="Publish each access attempt twice with the same eventId to test idempotency.")
    parser.add_argument("--humidity", type=float)
    parser.add_argument("--door-state", choices=("OPEN", "CLOSED", "UNKNOWN"), default="UNKNOWN")
    parser.add_argument("--lock-state", choices=("LOCKED", "UNLOCKED", "UNKNOWN"), default="UNKNOWN")
    args = parser.parse_args()
    try:
        import paho.mqtt.client as mqtt
    except ImportError:
        raise RuntimeError("Install simulator dependencies: python -m pip install -r scripts/simulator-requirements.txt")
    credential = json.loads(args.credential.read_text(encoding="utf-8"))
    signing_key = base64.b64decode(credential["signingKey"], validate=True)
    if len(signing_key) < 32:
        raise RuntimeError("Invalid private device signing key.")
    if args.temperature is not None and credential["nodeType"] != "NODE_2":
        parser.error("--temperature requires a Node 2 credential")
    if args.door_open_seconds is not None and (credential["nodeType"] != "NODE_2" or args.door_open_seconds < 0):
        parser.error("--door-open-seconds requires Node 2 and a nonnegative duration")
    if args.failures and credential["nodeType"] != "NODE_1":
        parser.error("--failures requires a Node 1 credential")
    if args.access_method and credential["nodeType"] != "NODE_1":
        parser.error("--access-method requires Node 1")
    if not 1 <= args.attempts <= 100 or not math.isfinite(args.attempt_interval) or args.attempt_interval < 1:
        parser.error("attempts must be 1..100; attempt-interval must be finite and >=1 second")
    if args.temperature is not None and (not math.isfinite(args.temperature) or not -40 <= args.temperature <= 125):
        parser.error("temperature must be -40..125")
    if args.humidity is not None and (not math.isfinite(args.humidity) or not 0 <= args.humidity <= 100):
        parser.error("humidity must be 0..100")
    if credential["nodeType"] != "NODE_2" and (args.humidity is not None or args.door_state != "UNKNOWN" or args.lock_state != "UNKNOWN"):
        parser.error("telemetry requires Node 2")
    access_value = getpass.getpass("Credential (hidden): ") if args.access_method else None
    prefix = f'smart-door/v1/doors/{credential["doorId"]}/devices/{credential["deviceCode"]}'
    configuration_ready = threading.Event()
    connected = threading.Event()
    stopping = threading.Event()
    state_lock = threading.Lock()
    state_file = ROOT / ".local" / ("simulator-" + str(credential["deviceId"]) + ".local.json")
    state = {"credentialId": credential["credentialId"], "configuration": 0, "credentials": 0}
    if state_file.exists():
        stored = json.loads(state_file.read_text(encoding="utf-8"))
        if stored.get("credentialId") == credential["credentialId"]:
            state.update(stored)
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="sim-" + credential["credentialId"], clean_session=False)
    client.username_pw_set(credential["username"], credential["password"])
    if args.tls:
        client.tls_set(ca_certs=str(args.cafile) if args.cafile else None, cert_reqs=ssl.CERT_REQUIRED)
    elif args.cafile:
        parser.error("--cafile requires --tls")

    def publish(kind: str, payload: dict):
        topic = prefix + "/events/" + kind
        encoded = base64.b64encode(json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8")).decode("ascii")
        envelope = {"credentialId": credential["credentialId"], "sentAt": int(time.time()), "nonce": uuid.uuid4().hex, "payload": encoded}
        canonical = "\n".join([topic, envelope["credentialId"], str(envelope["sentAt"]), envelope["nonce"], encoded])
        envelope["signature"] = hmac.new(signing_key, canonical.encode("utf-8"), hashlib.sha256).hexdigest()
        return client.publish(topic, json.dumps(envelope, separators=(",", ":")), qos=1, retain=False)

    def on_connect(client, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            print("Broker rejected device connection; refresh credential/ACL.", flush=True)
            return
        client.subscribe(prefix + "/commands/+", qos=1)
        connected.set()
        print("Connected:", credential["deviceCode"], credential["nodeType"], "door", credential["doorId"], flush=True)

    def on_message(client, userdata, message):
        kind = message.topic.rsplit("/", 1)[-1]
        if kind in ("access-result", "alarm"):
            try:
                value = json.loads(message.payload)
                expires = datetime.fromisoformat(value["expiresAt"].replace("Z", "+00:00"))
                if message.retain or value.get("schemaVersion") != 1 or expires <= datetime.now(timezone.utc):
                    return
                if kind == "access-result" and credential["nodeType"] == "NODE_1":
                    print("Access result:", value.get("result"), value.get("reason"), "locked until", value.get("lockedUntil"), flush=True)
                elif kind == "alarm" and credential["nodeType"] == "NODE_2":
                    event_id = str(uuid.UUID(value["eventId"]))
                    with state_lock:
                        alarms = state.setdefault("alarmEvents", [])
                        if event_id in alarms:
                            return
                        alarms.append(event_id)
                        state_file.parent.mkdir(parents=True, exist_ok=True)
                        state_file.write_text(json.dumps(state), encoding="utf-8")
                        state_file.chmod(0o600)
                    print("Simulated alarm (no physical buzzer).", flush=True)
            except (ValueError, TypeError, KeyError):
                print("Rejected malformed access/alarm message.", flush=True)
            return
        if kind == "unlock":
            if credential["nodeType"] != "NODE_2" or message.retain:
                return
            try:
                value = json.loads(message.payload)
                command_id = str(uuid.UUID(value["commandId"]))
                expires = datetime.fromisoformat(value["expiresAt"].replace("Z", "+00:00"))
                if value.get("schemaVersion") != 1 or value.get("action") != "UNLOCK" or expires <= datetime.now(timezone.utc):
                    return
                with state_lock:
                    history = state.setdefault("unlockResults", {})
                    if command_id not in history:
                        history[command_id] = args.unlock_result
                        state_file.parent.mkdir(parents=True, exist_ok=True)
                        state_file.write_text(json.dumps(state), encoding="utf-8")
                        state_file.chmod(0o600)
                    result = history[command_id]
                if result != "silent":
                    publish("command-ack", {"schemaVersion": 1, "commandId": command_id, "result": result})
                print("Simulated unlock:", command_id, result, flush=True)
            except (ValueError, TypeError, KeyError):
                print("Rejected malformed unlock command.", flush=True)
            return
        if kind not in ("configuration", "credentials"):
            return
        try:
            value = json.loads(message.payload)
            version = value["version"]
            if value.get("schemaVersion") != 1 or not isinstance(version, int) or version < 1:
                raise ValueError("Invalid schema/version")
            if kind == "credentials":
                if credential["nodeType"] != "NODE_1":
                    return
                if not isinstance(value.get("pinVerifier"), str) or not value["pinVerifier"].startswith("$2"):
                    print("Credentials waiting: set the door PIN in App before setup ACK.", flush=True)
                    return
                if not isinstance(value.get("cards"), list):
                    raise ValueError("Missing card snapshot")
            else:
                if not all(key in value for key in ("maxFailedAttempts", "lockoutSeconds", "temperatureThreshold")):
                    raise ValueError("Incomplete configuration")
            with state_lock:
                if version < state[kind]:
                    return
                state[kind] = version
                if kind == "configuration":
                    state["appliedConfiguration"] = value
                state_file.parent.mkdir(parents=True, exist_ok=True)
                state_file.write_text(json.dumps(state), encoding="utf-8")
                state_file.chmod(0o600)
            if kind == "configuration":
                configuration_ready.set()
            publish("sync-ack", {"eventId": uuid.uuid4().hex, "kind": kind.upper(), "version": version})
            print("Applied and ACK:", kind, "version", version, flush=True)
        except (ValueError, TypeError, KeyError):
            print("Rejected malformed snapshot; no ACK sent.", flush=True)

    def on_disconnect(client, userdata, flags, reason_code, properties):
        connected.clear()

    client.on_connect, client.on_message, client.on_disconnect = on_connect, on_message, on_disconnect
    signal.signal(signal.SIGINT, lambda *_: stopping.set())
    client.connect(args.host, args.port, keepalive=30)
    client.loop_start()
    try:
        if not connected.wait(15):
            raise RuntimeError("Device did not connect to Broker within 15s.")
        if args.temperature is not None or args.door_open_seconds is not None or args.failures:
            if not configuration_ready.wait(15):
                raise RuntimeError("No configuration received; use App configuration sync/retry.")
            with state_lock:
                payload = simulated_alert(state["appliedConfiguration"], temperature=args.temperature,
                                          open_seconds=args.door_open_seconds, failures=args.failures)
            if payload is None:
                print("No alert: disabled or sensor did not exceed configured threshold.", flush=True)
            else:
                payload["eventId"] = args.event_id or uuid.uuid4().hex
                result = publish("alert", payload)
                result.wait_for_publish(timeout=10)
                if not result.is_published():
                    raise RuntimeError("Broker did not acknowledge the test alert.")
                print("Test alert delivered to Broker; check Backend/App for business acceptance.", flush=True)
        deadline, next_heartbeat, next_attempt, attempts_sent = time.monotonic() + max(1, args.duration), 0.0, 0.0, 0
        # Heartbeat first so Node 2 is ready before a separate Node 1 requests access.
        while time.monotonic() < deadline and not stopping.is_set():
            if connected.is_set() and time.monotonic() >= next_heartbeat:
                publish("heartbeat", {"firmwareVersion": "person1-4-simulator-1"})
                if credential["nodeType"] == "NODE_2":
                    publish("telemetry", {"schemaVersion": 1, "eventId": str(uuid.uuid4()),
                        "observedAt": datetime.now(timezone.utc).isoformat(), "temperature": args.temperature,
                        "humidity": args.humidity, "doorState": args.door_state, "lockState": args.lock_state})
                next_heartbeat = time.monotonic() + 15
            if connected.is_set() and access_value is not None and attempts_sent < args.attempts and time.monotonic() >= next_attempt:
                request = {"schemaVersion": 1, "eventId": str(uuid.uuid4()), "method": args.access_method,
                    "value": access_value, "occurredAt": datetime.now(timezone.utc).isoformat()}
                publish("access-request", request)
                if args.retry_access:
                    publish("access-request", request)
                attempts_sent += 1
                next_attempt = time.monotonic() + args.attempt_interval
            stopping.wait(0.25)
    finally:
        client.disconnect()
        client.loop_stop()


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError) as error:
        print("Simulator failed:", type(error).__name__, str(error), file=sys.stderr)
        sys.exit(1)

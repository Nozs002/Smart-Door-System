"""Person 1/4 device simulator: apply snapshots, sign ACK/heartbeat and emit test alerts. No lock control."""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
from pathlib import Path
import signal
import ssl
import sys
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("credential", type=Path, help="Private file produced by setup-mqtt.py issue.")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=1884, help="Docker host MQTT port (MQTT_HOST_PORT); Broker internal port stays 1883.")
    parser.add_argument("--tls", action="store_true")
    parser.add_argument("--cafile", type=Path)
    parser.add_argument("--duration", type=int, default=120)
    parser.add_argument("--temperature", type=float, help="Emit one Node 2 HIGH_TEMPERATURE event after connecting; activate the Node first.")
    parser.add_argument("--failures", action="store_true", help="Emit one Node 1 TOO_MANY_FAILURES test event; activate the Node first.")
    parser.add_argument("--event-id", help="Repeat an event ID to verify alert deduplication (keep the same payload).")
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
    if args.failures and credential["nodeType"] != "NODE_1":
        parser.error("--failures requires a Node 1 credential")
    prefix = f'smart-door/v1/doors/{credential["doorId"]}/devices/{credential["deviceCode"]}'
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
                state_file.parent.mkdir(parents=True, exist_ok=True)
                state_file.write_text(json.dumps(state), encoding="utf-8")
                state_file.chmod(0o600)
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
        if args.temperature is not None or args.failures:
            payload = {"eventId": args.event_id or uuid.uuid4().hex,
                       "type": "HIGH_TEMPERATURE" if args.temperature is not None else "TOO_MANY_FAILURES"}
            if args.temperature is not None:
                payload["observedTemperature"] = args.temperature
            result = publish("alert", payload)
            result.wait_for_publish(timeout=10)
            if not result.is_published():
                raise RuntimeError("Broker did not acknowledge the test alert.")
            print("Test alert delivered to Broker; check Backend/App for business acceptance.", flush=True)
        deadline, next_heartbeat = time.monotonic() + max(1, args.duration), 0.0
        while time.monotonic() < deadline and not stopping.is_set():
            if connected.is_set() and time.monotonic() >= next_heartbeat:
                publish("heartbeat", {"firmwareVersion": "person1-4-simulator-1"})
                next_heartbeat = time.monotonic() + 15
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

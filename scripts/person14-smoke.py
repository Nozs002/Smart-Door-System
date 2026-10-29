"""Exercise Person 1/4 against running Backend/MySQL; --mqtt includes two authenticated simulated Nodes.

Creates isolated, named local fixtures. Never changes an existing customer's password or deletes data.
Test credentials and device exports stay in ignored .local/.
"""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import hmac
import importlib.util
import json
from pathlib import Path
import re
import secrets
import subprocess
import sys
import threading
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import uuid

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / ".local"


def api(base, method, path, payload=None, token=None, expected=200):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = Request(base + path, data=body, headers=headers, method=method)
    try:
        with urlopen(request, timeout=20) as response:
            status, raw = response.status, response.read()
    except HTTPError as error:
        status, raw = error.code, error.read()
    if status != expected:
        raise AssertionError(f"{method} {path}: HTTP {status}, expected {expected}")
    value = json.loads(raw) if raw else {}
    return value.get("data")


def wait_for(label, test, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        value = test()
        if value:
            print("PASS", label, flush=True)
            return value
        time.sleep(0.5)
    raise AssertionError("Timed out: " + label)


class SimulatedNode:
    def __init__(self, mqtt, credential, host, port):
        self.credential = credential
        self.snapshots = {}
        self.connected = threading.Event()
        self.prefix = f'smart-door/v1/doors/{credential["doorId"]}/devices/{credential["deviceCode"]}'
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="smoke-" + credential["credentialId"], clean_session=True)
        self.client.username_pw_set(credential["username"], credential["password"])
        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message
        self.client.connect(host, port, 30)
        self.client.loop_start()
        if not self.connected.wait(15):
            self.close()
            raise AssertionError("Device Broker authentication failed")

    def on_connect(self, client, userdata, flags, reason_code, properties):
        if not reason_code.is_failure:
            client.subscribe(self.prefix + "/commands/+", qos=1)
            self.connected.set()

    def on_message(self, client, userdata, message):
        kind = message.topic.rsplit("/", 1)[-1]
        if kind not in ("configuration", "credentials"):
            return
        snapshot = json.loads(message.payload)
        if kind == "credentials" and not snapshot.get("pinVerifier"):
            return
        previous = self.snapshots.get(kind, {}).get("version", 0)
        if snapshot["version"] < previous:
            return
        self.snapshots[kind] = snapshot
        self.publish("sync-ack", {"eventId": uuid.uuid4().hex, "kind": kind.upper(), "version": snapshot["version"]})

    def publish(self, kind, payload):
        topic = self.prefix + "/events/" + kind
        encoded = base64.b64encode(json.dumps(payload, separators=(",", ":")).encode()).decode()
        envelope = {"credentialId": self.credential["credentialId"], "sentAt": int(time.time()), "nonce": uuid.uuid4().hex, "payload": encoded}
        canonical = "\n".join([topic, envelope["credentialId"], str(envelope["sentAt"]), envelope["nonce"], encoded])
        envelope["signature"] = hmac.new(base64.b64decode(self.credential["signingKey"]), canonical.encode(), hashlib.sha256).hexdigest()
        return self.client.publish(topic, json.dumps(envelope), qos=1)

    def close(self):
        self.client.disconnect()
        self.client.loop_stop()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--mqtt", action="store_true")
    parser.add_argument("--mqtt-host", default="localhost")
    parser.add_argument("--mqtt-port", type=int, default=1884)
    args = parser.parse_args()
    mqtt = None
    if args.mqtt:
        try:
            import paho.mqtt.client as mqtt
        except ImportError:
            parser.error("Install scripts/simulator-requirements.txt in a local virtual environment first.")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3)
    folder = LOCAL / "smoke" / stamp
    folder.mkdir(parents=True, exist_ok=False)
    username, outsider = "p14-" + stamp, "p14-other-" + stamp
    password, new_password = "Test-" + secrets.token_urlsafe(18), "Next-" + secrets.token_urlsafe(18)
    pin, uid = str(secrets.randbelow(900000) + 100000), secrets.token_hex(4).upper()
    base = args.base_url.rstrip("/")
    request = lambda method, path, data=None, token=None, expected=200: api(base, method, path, data, token, expected)
    for name in (username, outsider):
        request("POST", "/api/v1/auth/register", {"displayName": "Local Person 1/4 test", "username": name, "password": password}, expected=201)
    login = lambda name, secret: request("POST", "/api/v1/auth/login", {"username": name, "password": secret})["accessToken"]
    token, other_token = login(username, password), login(outsider, password)
    request("GET", "/api/v1/accounts/me", token=token)
    request("GET", "/api/v1/accounts/me", expected=401)
    print("PASS registration, login and authenticated profile", flush=True)
    node_codes = ["P14-" + stamp + "-N1", "P14-" + stamp + "-N2"]
    result = subprocess.run(["docker", "compose", "run", "--rm", "--no-deps", "backend",
        "--spring.profiles.active=local,provision", "--app.settings-mqtt.enabled=false", "--app.outbox.enabled=false",
        "--tool.action=provision", "--tool.name=E2E-Person14-" + stamp,
        "--tool.node1=" + node_codes[0], "--tool.node2=" + node_codes[1]],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
    private_log = folder / "provision.local.log"
    private_log.write_text(result.stdout + result.stderr, encoding="utf-8")
    private_log.chmod(0o600)
    door_match = re.search(r"Door ID: (\d+)", result.stdout)
    code_match = re.search(r"Activation code \(displayed once\): ([^\r\n]+)", result.stdout)
    if result.returncode or not door_match or not code_match:
        raise RuntimeError("Provision failed; inspect " + str(private_log.relative_to(ROOT)))
    door_id = int(door_match.group(1))
    door_path = f"/api/v1/doors/{door_id}"
    claim = request("POST", "/api/v1/doors/claim", {"activationCode": code_match.group(1).strip()}, token)
    assert claim["id"] == door_id
    request("GET", door_path, token=other_token, expected=404)
    holder = request("POST", "/api/v1/cardholders", {"displayName": "E2E cardholder"}, token, 201)
    grant = {"cardholderId": holder["id"], "uid": uid}
    card = request("POST", door_path + "/rfid-credentials", grant, token, 201)
    request("POST", door_path + "/rfid-credentials", grant, token, 409)
    request("PUT", door_path + "/pin", {"pin": pin}, other_token, 404)
    request("PUT", door_path + "/pin", {"pin": pin}, token)
    status = request("GET", door_path + "/pin", token=token)
    assert status["configured"] and "pinHash" not in status and "pin" not in status
    request("PATCH", f'/api/v1/cardholders/{holder["id"]}', {"displayName": "E2E cardholder", "status": "DISABLED"}, token)
    request("PATCH", f'/api/v1/cardholders/{holder["id"]}', {"displayName": "E2E cardholder", "status": "ACTIVE"}, token)
    cards = request("GET", door_path + "/rfid-credentials", token=token)["items"]
    assert cards[0]["status"] == "REVOKED", "Re-enabling a holder must not silently restore cards"
    request("POST", door_path + "/rfid-credentials", grant, token, 201)
    print("PASS claim, owner isolation, Cardholder, RFID, PIN and explicit reissue", flush=True)
    config = request("GET", door_path + "/configuration", token=token)
    desired = {"maxFailedAttempts": 5, "lockoutSeconds": 120, "temperatureThreshold": 55, "openTimeoutSeconds": 30}
    config = request("PUT", door_path + "/configuration", desired, token)
    retried = request("POST", door_path + "/sync/retry", token=token)
    assert (retried["version"], retried["credentialsVersion"]) == (config["version"], config["credentialsVersion"])
    print("PASS desired-state versions and retry without version inflation", flush=True)
    devices = request("GET", door_path + "/devices", token=token)
    report = {"createdAt": stamp, "baseUrl": base, "username": username, "password": password,
              "doorId": door_id, "devices": devices, "testPin": pin, "testUid": uid,
              "deviceCredentialFiles": [], "mqttTested": False}
    report_file = folder / "report.local.json"
    def save_report():
        report_file.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        report_file.chmod(0o600)
    save_report()
    clients = []
    try:
        if args.mqtt:
            spec = importlib.util.spec_from_file_location("local_mqtt_setup", ROOT / "scripts" / "setup-mqtt.py")
            setup = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(setup)
            private_files = []
            for device in devices:
                output = setup.cli("issue", "smoke/" + stamp + "/device-" + str(device["id"]) + ".local.json", device["id"])
                private_files.append(output)
            export = setup.cli("export-broker", "smoke/" + stamp + "/broker-users.local.json")
            setup.configure(export)
            setup.run(["docker", "compose", "restart", "mosquitto"])
            time.sleep(3)
            report["deviceCredentialFiles"] = [str(path.relative_to(ROOT)) for path in private_files]
            save_report()
            for path in private_files:
                credential = json.loads(path.read_text(encoding="utf-8"))
                clients.append(SimulatedNode(mqtt, credential, args.mqtt_host, args.mqtt_port))
            def synced():
                state = request("GET", door_path + "/sync-status", token=token)
                return state if all(d["configState"] == "SYNCED" and d["credentialsState"] in ("SYNCED", "NOT_APPLICABLE") for d in state["devices"]) else None
            wait_for("signed setup ACK from both PROVISIONED Nodes", synced)
            node1 = next(client for client in clients if client.credential["nodeType"] == "NODE_1")
            node2 = next(client for client in clients if client.credential["nodeType"] == "NODE_2")
            snapshot = node1.snapshots["credentials"]
            assert snapshot["pinVerifier"].startswith("$2") and snapshot["cards"][0]["uid"] == uid
            for device in devices:
                request("POST", door_path + f'/devices/{device["id"]}/lifecycle', {"action": "ACTIVATE"}, token)
            for client in clients:
                client.publish("heartbeat", {"firmwareVersion": "p14-smoke-1"})
            assert request("GET", door_path, token=token)["status"] == "ACTIVE"
            print("PASS Node activation with proven snapshots (no physical lock command)", flush=True)
            for _ in range(3):
                desired["temperatureThreshold"] += 1
                request("PUT", door_path + "/configuration", desired, token)
            wait_for("latest desired state wins retained retries", synced)
            node1.publish("alert", {"eventId": "wrong-role-" + stamp, "type": "HIGH_TEMPERATURE", "observedTemperature": 80})
            event_id = "p14-temp-" + stamp
            event = {"eventId": event_id, "type": "HIGH_TEMPERATURE", "observedTemperature": 80}
            node2.publish("alert", event)
            alerts_path = "/api/v1/alerts?doorId=" + str(door_id)
            alert = wait_for("signed temperature alert saved", lambda: (request("GET", alerts_path, token=token)["items"] or [None])[0])
            node2.publish("alert", event)
            time.sleep(1)
            alerts = request("GET", alerts_path, token=token)
            assert alerts["totalElements"] == 1, "Duplicate or wrong-role event created another alert"
            notifications = request("GET", "/api/v1/notifications", token=token)
            assert notifications["totalElements"] == 1
            assert request("GET", "/api/v1/notifications", token=other_token)["totalElements"] == 0
            notice = notifications["items"][0]
            request("POST", f'/api/v1/notifications/{notice["id"]}/read', token=token)
            assert request("GET", f'/api/v1/alerts/{alert["id"]}', token=token)["status"] == "ACTIVE"
            for target in ("ACKNOWLEDGED", "RESOLVED"):
                request("PATCH", f'/api/v1/alerts/{alert["id"]}/status', {"status": target}, token)
            request("PATCH", f'/api/v1/alerts/{alert["id"]}/status', {"status": "ACKNOWLEDGED"}, token, 400)
            request("GET", f'/api/v1/alerts/{alert["id"]}', token=other_token, expected=404)
            print("PASS alert deduplication, role checks, owner inbox, read and resolve rules", flush=True)
            delivery = request("GET", door_path + "/delivery-status?size=100", token=token)
            assert all("payload" not in row for row in delivery["items"])
            report["mqttTested"] = True
            report["alertId"] = alert["id"]
        second_token = login(username, password)
        request("POST", "/api/v1/auth/logout", token=second_token)
        request("GET", "/api/v1/accounts/me", token=second_token, expected=401)
        request("GET", "/api/v1/accounts/me", token=token)
        request("POST", "/api/v1/accounts/me/password", {"currentPassword": password, "newPassword": new_password}, token)
        report["password"] = new_password
        save_report()
        request("GET", "/api/v1/accounts/me", token=token, expected=401)
        request("POST", "/api/v1/auth/login", {"username": username, "password": password}, expected=401)
        new_token = login(username, new_password)
        request("GET", "/api/v1/accounts/me", token=new_token)
        print("PASS logout revokes only current JWT; password change revokes every old JWT", flush=True)
        report["passed"] = True
        save_report()
        print("PASS Person 1/4 smoke test; private test account/report:", report_file.relative_to(ROOT), flush=True)
    finally:
        for client in clients:
            client.close()


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, RuntimeError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print("FAIL", type(error).__name__, str(error), file=sys.stderr)
        sys.exit(1)

"""Test Broker revocation on an isolated temporary Mosquitto, never the application Broker/DB."""
from pathlib import Path
import importlib.util
import json
import secrets
import subprocess
import threading
import time
import uuid
import paho.mqtt.client as mqtt

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("mqtt_setup", ROOT / "scripts/setup-mqtt.py")
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


def docker(*args):
    result = subprocess.run(["docker", *args], cwd=ROOT, check=True, capture_output=True,
                            text=True, encoding="utf-8", timeout=120)
    return result.stdout.strip()


def main():
    name = "smartdoor-revocation-check-" + secrets.token_hex(4)
    folder = ROOT / ".local" / name
    folder.mkdir(parents=True)
    config = {"MQTT_USERNAME": "test-backend", "MQTT_PASSWORD": secrets.token_urlsafe(32), "MQTT_TLS": "false"}
    old = {"deviceId": 1, "doorId": 1, "deviceCode": "TEST-OLD-N1",
           "username": "device-1-" + str(uuid.uuid4()), "password": secrets.token_urlsafe(32)}
    export = folder / "users.local.json"
    export.write_text(json.dumps([old]), encoding="utf-8")
    export.chmod(0o600)
    setup.SECURITY = folder
    setup.environment = lambda: config
    setup.configure(export)
    (folder / "mosquitto.conf").write_text(
        "listener 1883\nallow_anonymous false\npassword_file /security/passwords\n"
        "acl_file /security/acl\npersistence false\n", encoding="utf-8")
    client = None
    try:
        docker("run", "-d", "--name", name, "-p", "127.0.0.1::1883", "--mount",
               "type=bind,source=" + str(folder) + ",target=/security,readonly",
               "eclipse-mosquitto:2", "mosquitto", "-c", "/security/mosquitto.conf")
        config["MQTT_HOST_PORT"] = docker("port", name, "1883/tcp").rsplit(":", 1)[1]
        for attempt in range(15):
            try:
                if setup.authentication_result(config, old["username"], old["password"]) != 0:
                    raise RuntimeError("Initial device identity was rejected before revocation")
                break
            except OSError:
                if attempt == 14:
                    raise
                time.sleep(1)
        connected, disconnected = threading.Event(), threading.Event()
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="test-existing-session",
                             protocol=mqtt.MQTTv311, reconnect_on_failure=False)
        client.username_pw_set(old["username"], old["password"])
        client.on_connect = lambda *_: connected.set()
        client.on_disconnect = lambda *_: disconnected.set()
        client.connect("127.0.0.1", int(config["MQTT_HOST_PORT"]), 10)
        client.loop_start()
        if not connected.wait(8):
            raise RuntimeError("Old session was not established for the test")
        export.write_text("[]", encoding="utf-8")
        setup.configure(export)
        docker("restart", name)
        # Docker may allocate a new host port when restarting an ephemeral-port container.
        config["MQTT_HOST_PORT"] = docker("port", name, "1883/tcp").rsplit(":", 1)[1]
        if not disconnected.wait(8):
            raise RuntimeError("Restart did not disconnect the previous session")
        setup.confirm_rejection(config, old)
        print("PASS isolated Broker: old identity initially accepted; restart drops its session; old key explicitly rejected; backend identity still accepted.")
    finally:
        if client is not None:
            client.disconnect()
            client.loop_stop()
        logs = subprocess.run(["docker", "logs", name], cwd=ROOT, capture_output=True,
                              text=True, encoding="utf-8", timeout=30)
        (folder / "broker.local.log").write_text(logs.stdout + logs.stderr, encoding="utf-8")
        # Remove only this randomly named test container. No application volume is touched.
        subprocess.run(["docker", "rm", "-f", name], cwd=ROOT, capture_output=True, timeout=30)


if __name__ == "__main__":
    main()

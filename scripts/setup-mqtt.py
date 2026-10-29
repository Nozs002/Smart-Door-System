"""Prepare private local MQTT credentials/ACL; never print secrets or commit .local/."""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / ".local"
SECURITY = LOCAL / "mosquitto"


def environment() -> dict[str, str]:
    result = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip().strip("\"'")
    return result


def run(command: list[str]) -> None:
    subprocess.run(command, cwd=ROOT, check=True, timeout=180)


def init() -> None:
    if not (ROOT / ".env").exists():
        shutil.copyfile(ROOT / ".env.example", ROOT / ".env")
    config = environment()
    updates = {}
    if not config.get("DEVICE_MASTER_KEY"):
        updates["DEVICE_MASTER_KEY"] = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
    try:
        if len(base64.b64decode(config.get("DEVICE_MASTER_KEY") or updates["DEVICE_MASTER_KEY"], validate=True)) < 32:
            raise ValueError()
    except (ValueError, KeyError):
        raise RuntimeError("Existing DEVICE_MASTER_KEY is invalid; no key was overwritten.")
    for key, value in {"MQTT_SETTINGS_ENABLED": "true", "MQTT_TLS": "false", "MYSQL_HOST_PORT": "3307", "MQTT_HOST_PORT": "1884"}.items():
        if not config.get(key):
            updates[key] = value
    if updates:
        backups = LOCAL / "backups"
        backups.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        backup = backups / ("env-before-mqtt-" + stamp + ".local")
        shutil.copyfile(ROOT / ".env", backup)
        backup.chmod(0o600)
        lines = (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines()
        for key, value in updates.items():
            pattern = re.compile(r"^\s*" + re.escape(key) + r"\s*=")
            lines = [line for line in lines if not pattern.match(line)]
            lines.append(key + "=" + value)
        (ROOT / ".env").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("Local server key configured; existing secrets preserved.")
    if (SECURITY / "passwords").exists() and (SECURITY / "acl").exists():
        print("Existing Broker credentials/ACL preserved; use configure to refresh device users.")
    else:
        configure(None)


def configure(devices_file: Path | None) -> None:
    config = environment()
    username, password = config.get("MQTT_USERNAME", ""), config.get("MQTT_PASSWORD", "")
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,100}", username) or username.startswith("device-"):
        raise RuntimeError("MQTT_USERNAME must be a simple unique backend username without device- prefix.")
    if not password or any(char in password for char in "\r\n\0"):
        raise RuntimeError("MQTT_PASSWORD is missing or invalid.")
    devices = [] if devices_file is None else json.loads(devices_file.read_text(encoding="utf-8"))
    SECURITY.mkdir(parents=True, exist_ok=True)
    plaintext = SECURITY / "passwords.next"
    lines = [username + ":" + password]
    acl = ["user " + username, "topic read smart-door/v1/doors/+/devices/+/events/+",
           "topic write smart-door/v1/doors/+/devices/+/commands/+", ""]
    seen = {username}
    for device in devices:
        user, secret = device["username"], device["password"]
        code, door_id = device["deviceCode"], device["doorId"]
        if not re.fullmatch(r"device-[0-9]+-[a-f0-9-]{36}", user) or user in seen:
            raise RuntimeError("Invalid or duplicate exported device username.")
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,99}", code) or not isinstance(door_id, int) or door_id < 1:
            raise RuntimeError("Device identity cannot safely form an MQTT topic.")
        if not re.fullmatch(r"[A-Za-z0-9_-]+", secret):
            raise RuntimeError("Invalid exported device password.")
        seen.add(user)
        lines.append(user + ":" + secret)
        prefix = f"smart-door/v1/doors/{door_id}/devices/{code}"
        acl += ["user " + user, "topic read " + prefix + "/commands/+",
                "topic write " + prefix + "/events/sync-ack",
                "topic write " + prefix + "/events/heartbeat",
                "topic write " + prefix + "/events/alert", ""]
    try:
        plaintext.write_text("\n".join(lines) + "\n", encoding="utf-8")
        plaintext.chmod(0o600)
        run(["docker", "run", "--rm", "--user", "0", "--mount",
             "type=bind,source=" + str(SECURITY) + ",target=/security",
             "eclipse-mosquitto:2", "sh", "-c",
             "set -eu\ncp /security/passwords.next /tmp/passwords\nchmod 0600 /tmp/passwords\n"
             "mosquitto_passwd -U /tmp/passwords\ncat /tmp/passwords > /security/passwords.next"])
        plaintext.replace(SECURITY / "passwords")
        (SECURITY / "passwords").chmod(0o644)
        next_acl = SECURITY / "acl.next"
        next_acl.write_text("\n".join(acl) + "\n", encoding="utf-8")
        next_acl.replace(SECURITY / "acl")
        (SECURITY / "acl").chmod(0o644)
    finally:
        plaintext.unlink(missing_ok=True)
    print("Broker password hashes and ACL prepared for", len(devices), "devices.")


def cli(action: str, output: str, device_id: int | None = None) -> Path:
    LOCAL.mkdir(parents=True, exist_ok=True)
    command = ["docker", "compose", "run", "--rm", "--no-deps", "--user", "0",
               "--volume", str(LOCAL) + ":/run/local", "backend",
               "--spring.profiles.active=local,device-security", "--app.settings-mqtt.enabled=false",
               "--app.outbox.enabled=false", "--security.action=" + action,
               "--security.output=/run/local/" + output]
    if device_id is not None:
        command.append("--security.device-id=" + str(device_id))
    run(command)
    return LOCAL / output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    commands.add_parser("init", help="Generate a missing master key, preserve .env values, prepare backend Broker user.")
    issue = commands.add_parser("issue", help="Issue/rotate a provisioned device identity; save private export locally.")
    issue.add_argument("device_id", type=int)
    configure_parser = commands.add_parser("configure", help="Export current identities, update Broker users and restart Broker.")
    configure_parser.add_argument("--devices", type=Path, help="Use an existing private broker export instead of invoking CLI.")
    args = parser.parse_args()
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3)
    if args.action == "init":
        init()
    elif args.action == "issue":
        if args.device_id < 1:
            parser.error("device_id must be positive")
        output = cli("issue", "device-" + str(args.device_id) + "-" + stamp + ".local.json", args.device_id)
        print("Private device export:", output.relative_to(ROOT))
        print("Run: python scripts/setup-mqtt.py configure")
    else:
        export = args.devices or cli("export-broker", "broker-users-" + stamp + ".local.json")
        configure(export)
        run(["docker", "compose", "restart", "mosquitto"])
        print("Broker restarted: previous sessions were disconnected and current ACL is active.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print("MQTT setup failed:", type(error).__name__, str(error), file=sys.stderr)
        sys.exit(1)

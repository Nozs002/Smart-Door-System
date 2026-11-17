"""Apply Backend device manifests and reload Mosquitto without manual provisioning commands."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import time

ROOT = Path('/mosquitto/security')
MANIFEST = Path('/device-manifest/broker-users.json')


def prepare(users):
    username, password = os.environ['MQTT_USERNAME'], os.environ['MQTT_PASSWORD']
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,100}', username) or not password or any(c in password for c in '\r\n\0'):
        raise ValueError('Invalid backend MQTT identity')
    lines = [username + ':' + password]
    acl = ['user ' + username, 'topic read smart-door/v1/doors/+/devices/+/events/+',
           'topic write smart-door/v1/doors/+/devices/+/commands/+', '']
    seen = {username}
    for user in users:
        name, secret, code, door = user['username'], user['password'], user['deviceCode'], user['doorId']
        if not re.fullmatch(r'device-[0-9]+-[a-f0-9-]{36}', name) or name in seen or not re.fullmatch(r'[A-Za-z0-9_-]+', secret):
            raise ValueError('Invalid device identity')
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,99}', code) or type(door) is not int or door < 1:
            raise ValueError('Invalid topic identity')
        seen.add(name)
        lines.append(name + ':' + secret)
        prefix = f'smart-door/v1/doors/{door}/devices/{code}'
        acl += ['user ' + name, 'topic read ' + prefix + '/commands/+', 'topic write ' + prefix + '/events/+', '']
    temporary = ROOT / 'passwords.next'
    try:
        temporary.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        temporary.chmod(0o600)
        subprocess.run(['mosquitto_passwd', '-U', str(temporary)], check=True, capture_output=True)
        next_acl = ROOT / 'acl.next'
        next_acl.write_text('\n'.join(acl) + '\n', encoding='utf-8')
        for file in (temporary, next_acl):
            shutil.chown(file, 'mosquitto', 'mosquitto')
            file.chmod(0o600)
        temporary.replace(ROOT / 'passwords')
        next_acl.replace(ROOT / 'acl')
    finally:
        temporary.unlink(missing_ok=True)


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    # Keep currently issued users during startup until Backend publishes a full manifest.
    for name in ('passwords', 'acl'):
        source = Path('/mosquitto/bootstrap') / name
        if not (ROOT / name).exists() and source.exists():
            shutil.copyfile(source, ROOT / name)
            shutil.chown(ROOT / name, 'mosquitto', 'mosquitto')
            (ROOT / name).chmod(0o600)
    if not (ROOT / 'passwords').exists():
        prepare([])
    broker = subprocess.Popen(['mosquitto', '-c', '/mosquitto/config/mosquitto.conf'])
    stopping = False
    def stop(*_):
        nonlocal stopping
        stopping = True
        broker.terminate()
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    previous = None
    while not stopping and broker.poll() is None:
        try:
            if MANIFEST.exists():
                content = MANIFEST.read_bytes()
                digest = hashlib.sha256(content).digest()
                if digest != previous:
                    prepare(json.loads(content))
                    broker.send_signal(signal.SIGHUP)
                    previous = digest
                    print('Applied device MQTT identities and ACL.', flush=True)
        except (OSError, ValueError, KeyError, subprocess.SubprocessError):
            print('Device manifest could not be applied. Retrying.', flush=True)
        time.sleep(2)
    return broker.wait()


if __name__ == '__main__':
    raise SystemExit(main())

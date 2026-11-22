"""Apply Backend device manifests and reload Mosquitto without manual provisioning commands."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import struct
import subprocess
import time
import uuid

ROOT = Path('/mosquitto/security')
MANIFEST = Path('/device-manifest/broker-users.json')
PROOF = Path('/revocation-proof/revocations.json')


def mqtt_field(value):
    encoded = value.encode('utf-8')
    if len(encoded) > 65535:
        raise ValueError('Invalid MQTT field length')
    return struct.pack('!H', len(encoded)) + encoded


def authentication_rejected(username, password, host='127.0.0.1', port=1883):
    """CONNECT only; require CONNACK auth refusal, not a network/protocol failure."""
    body = b'\x00\x04MQTT\x04\xc2\x00\x0a' + mqtt_field('revoke-' + uuid.uuid4().hex)
    body += mqtt_field(username) + mqtt_field(password)
    remaining, value = bytearray(), len(body)
    while True:
        digit, value = value % 128, value // 128
        remaining.append(digit | (128 if value else 0))
        if not value:
            break
    with socket.create_connection((host, port), timeout=3) as connection:
        connection.settimeout(3)
        connection.sendall(b'\x10' + remaining + body)
        response = b''
        while len(response) < 4:
            chunk = connection.recv(4 - len(response))
            if not chunk:
                return False
            response += chunk
        return response[:3] == b'\x20\x02\x00' and response[3] in (4, 5)


def stop_broker(broker):
    broker.terminate()
    try:
        broker.wait(timeout=10)
    except subprocess.TimeoutExpired:
        broker.kill()
        broker.wait(timeout=5)


def write_proof(records):
    PROOF.parent.mkdir(parents=True, exist_ok=True)
    temporary = PROOF.with_suffix('.next')
    temporary.write_text(json.dumps(records), encoding='utf-8')
    # No secrets in this file. Backend mounts the volume read-only as uid 10001.
    temporary.chmod(0o644)
    temporary.replace(PROOF)


def apply_manifest(content, broker):
    manifest = json.loads(content)
    # V13 array manifests remain accepted during a rolling local upgrade.
    users = manifest if isinstance(manifest, list) else manifest['users']
    revocations = [] if isinstance(manifest, list) else manifest.get('revocations', [])
    if not isinstance(users, list) or not isinstance(revocations, list):
        raise ValueError('Invalid manifest')
    for record in revocations:
        if type(record.get('deviceId')) is not int or record['deviceId'] < 1:
            raise ValueError('Invalid revocation device')
        if not re.fullmatch(r'[a-f0-9-]{36}', record.get('credentialId', '')) or not isinstance(record.get('revokedAt'), str):
            raise ValueError('Invalid revocation epoch')
        expected = 'device-' + str(record['deviceId']) + '-' + record['credentialId']
        if record.get('username') != expected or not re.fullmatch(r'[A-Za-z0-9_-]+', record.get('password', '')):
            raise ValueError('Invalid revocation identity')
    prepare(users)
    if not revocations:
        broker.send_signal(signal.SIGHUP)
        return broker, True
    # A reload alone cannot prove existing sessions were disconnected.
    stop_broker(broker)
    broker = subprocess.Popen(['mosquitto', '-c', '/mosquitto/config/mosquitto.conf'])
    proofs = []
    for record in revocations:
        rejected = False
        for attempt in range(15):
            try:
                rejected = authentication_rejected(record['username'], record['password'])
                break  # Accepted CONNECT is a failed proof; never equate it to revocation.
            except OSError:
                if broker.poll() is not None:
                    break
                time.sleep(0.2)
        if rejected:
            proofs.append({key: record[key] for key in ('deviceId', 'credentialId', 'revokedAt')} |
                          {'sessionsDisconnected': True, 'authenticationRejected': True})
    write_proof(proofs)
    return broker, len(proofs) == len(revocations)


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
                    broker, proved = apply_manifest(content, broker)
                    if proved:
                        previous = digest
                    print('Applied MQTT manifest; pending revocation proofs are checked.', flush=True)
        except (OSError, ValueError, KeyError, subprocess.SubprocessError):
            print('Device manifest could not be applied. Retrying.', flush=True)
        time.sleep(2)
    return broker.wait()


if __name__ == '__main__':
    raise SystemExit(main())

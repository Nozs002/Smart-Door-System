"""Exercise technician -> provision -> claim -> enrollment -> MQTT setup -> activation locally.

Creates isolated demo accounts/boards/door and preserves them for inspection.
Device simulators provide protocol checks. Real Wi-Fi/GPIO still require ESP32s.
Private exports and subprocess logs remain under ignored .local/.
"""
import importlib.util
import json
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('mqtt_setup', ROOT / 'scripts/setup-mqtt.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)
BASE = 'http://127.0.0.1:8080'


def request(method, path, body=None, token=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            status, result = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status = error.code
        result = json.loads(error.read())
    if status != expected:
        raise RuntimeError(f'{method} {path}: expected {expected}, got {status}')
    return result.get('data') if status < 400 else result


def main():
    for attempt in range(30):
        try:
            request('GET', '/actuator/health')
            break
        except (OSError, RuntimeError):
            if attempt == 29:
                raise
            time.sleep(2)
    env = setup.environment()
    tech = request('POST', '/api/v1/auth/login', {'username': env['TECHNICIAN_USERNAME'], 'password': env['TECHNICIAN_PASSWORD']})['accessToken']
    stamp = uuid.uuid4().hex[:12]
    folder = ROOT / '.local' / ('onboarding-smoke-' + stamp)
    folder.mkdir(parents=True)
    owner_password = secrets.token_urlsafe(18)
    owner_name = 'onboarding-' + stamp
    request('POST', '/api/v1/auth/register', {'username': owner_name, 'password': owner_password, 'displayName': 'Onboarding smoke'}, expected=201)
    owner = request('POST', '/api/v1/auth/login', {'username': owner_name, 'password': owner_password})['accessToken']
    request('POST', '/api/v1/technician/inventory', {'nodeType': 'NODE_1'}, owner, expected=403)
    units, factory = [], []
    for role in ('NODE_1', 'NODE_2'):
        unit = request('POST', '/api/v1/technician/inventory', {'nodeType': role}, tech)
        units.append(unit)
        factory.append(request('GET', '/api/v1/technician/inventory/' + unit['unitCode'] + '/factory', token=tech))
    body = {'requestId': str(uuid.uuid4()), 'name': 'Onboarding smoke ' + stamp, 'location': 'Protocol test',
            'node1Code': units[0]['unitCode'], 'node2Code': units[1]['unitCode']}
    kit = request('POST', '/api/v1/technician/kits', body, tech)
    if request('POST', '/api/v1/technician/kits', body, tech)['doorId'] != kit['doorId']:
        raise RuntimeError('Provision retry created another door')
    bootstrap = re.search(r'BOOTSTRAP_SECRET\[\] = "([^"]+)"', factory[0]['firmwareConfig']).group(1)
    request('POST', '/api/v1/device-enrollment', {'unitCode': units[0]['unitCode'], 'bootstrapSecret': bootstrap}, expected=409)
    request('POST', '/api/v1/device-enrollment', {'unitCode': units[0]['unitCode'], 'bootstrapSecret': 'invalid'}, expected=401)
    door_id = kit['doorId']
    request('POST', '/api/v1/doors/claim', {'activationCode': kit['activationCode']}, owner)
    request('GET', f'/api/v1/doors/{door_id}/setup-nodes', token=tech, expected=404)
    if len(request('GET', f'/api/v1/doors/{door_id}/setup-nodes', token=owner)) != 2:
        raise RuntimeError('Missing owner setup metadata')
    request('POST', f'/api/v1/doors/{door_id}/complete-setup', token=owner, expected=409)
    request('PUT', f'/api/v1/doors/{door_id}/pin', {'pin': '123456'}, owner)
    processes, logs = [], []
    try:
        # Allow the automatic manifest pipeline to update Broker before connecting simulators.
        time.sleep(6)
        for index, unit in enumerate(units):
            secret = re.search(r'BOOTSTRAP_SECRET\[\] = "([^"]+)"', factory[index]['firmwareConfig']).group(1)
            credential = request('POST', '/api/v1/device-enrollment', {'unitCode': unit['unitCode'], 'bootstrapSecret': secret})
            path = folder / f'node-{index + 1}.local.json'
            path.write_text(json.dumps(credential), encoding='utf-8')
            path.chmod(0o600)
            log = (folder / f'node-{index + 1}.local.log').open('w', encoding='utf-8')
            logs.append(log)
            processes.append(subprocess.Popen([sys.executable, str(ROOT / 'scripts/device-simulator.py'), str(path),
                '--host', '127.0.0.1', '--port', env.get('MQTT_HOST_PORT', '1884'), '--duration', '120'],
                cwd=ROOT, stdout=log, stderr=log))
        request('POST', f'/api/v1/doors/{door_id}/sync/retry', token=owner)
        for attempt in range(40):
            try:
                result = request('POST', f'/api/v1/doors/{door_id}/complete-setup', token=owner)
                if result['status'] != 'ACTIVE':
                    raise RuntimeError('Completion did not activate the door')
                break
            except RuntimeError:
                if attempt == 39 or any(process.poll() is not None for process in processes):
                    raise
                time.sleep(2)
        result = request('POST', f'/api/v1/doors/{door_id}/complete-setup', token=owner)
        if result['status'] != 'ACTIVE':
            raise RuntimeError('Completion retry changed the door')
        print(f'PASS technician/claim/enrollment/automatic Broker/ACK/activation/idempotency. Test Door {door_id}; private logs under {folder.relative_to(ROOT)}')
    finally:
        for process in processes:
            if process.poll() is None:
                process.terminate()
            process.wait(timeout=10)
        for log in logs:
            log.close()


if __name__ == '__main__':
    main()

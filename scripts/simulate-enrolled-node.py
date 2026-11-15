"""Run a simulated Node using its factory header, through the real enrollment API.

Export/save the header before claiming the Door. No credential rotation or DB
edits are performed. Private transport credentials are saved under .local/.
"""
import argparse
import getpass
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def inventory_code(value):
    try:
        return str(uuid.UUID(value.strip()))
    except ValueError:
        raise ValueError('Use the UUID unitCode shown under NODE_1/NODE_2 (for example 11111111-1111-4111-8111-111111111111). The 16-character setup code is for Wi-Fi setup.') from None


def prepare_unit(code, enrollment_url, username=None, password=None):
    code = inventory_code(code)
    spec = importlib.util.spec_from_file_location('mqtt_setup', ROOT / 'scripts/setup-mqtt.py')
    setup = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(setup)
    env = setup.environment()
    username = username if username is not None else env.get('TECHNICIAN_USERNAME', '')
    password = password if password is not None else env.get('TECHNICIAN_PASSWORD', '')
    if not username or not password:
        raise ValueError('Provide technician credentials in .env or use --username NAME --ask-password.')
    base = enrollment_url.removesuffix('/api/v1/device-enrollment')
    login = urllib.request.Request(base + '/api/v1/auth/login', data=json.dumps({
        'username': username, 'password': password,
    }).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    try:
        with urllib.request.urlopen(login, timeout=20) as response:
            token = json.load(response)['data']['accessToken']
    except urllib.error.HTTPError as error:
        if error.code == 401:
            raise RuntimeError('Technician login failed (HTTP 401). Enter the current App password with --ask-password, or update the login credentials used by this tool in .env.') from None
        raise RuntimeError('Technician login failed (HTTP ' + str(error.code) + ').') from None
    try:
        request = urllib.request.Request(base + '/api/v1/technician/inventory/' + code + '/factory',
                                         headers={'Authorization': 'Bearer ' + token})
        with urllib.request.urlopen(request, timeout=20) as response:
            config = json.load(response)['data']['firmwareConfig']
    except urllib.error.HTTPError as error:
        if error.code == 403:
            raise RuntimeError('Factory export denied (HTTP 403). Use the technician who registered this board and export before Door claim.') from None
        if error.code == 404:
            raise RuntimeError('Board not found (HTTP 404). Check the unitCode shown in the App inventory.') from None
        raise RuntimeError('Factory export failed (HTTP ' + str(error.code) + '). Export before claim, using the technician who registered this board.') from None
    folder = ROOT / '.local' / 'onboarding-headers'
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / (code + '.local.h')
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
        output.write(config)
    print('Private factory header:', path.relative_to(ROOT), flush=True)
    print('After claiming the Door, run this tool again with that file path.', flush=True)


def read_identity(path):
    text = path.read_text(encoding='utf-8-sig')
    values = {}
    for name in ('UNIT_CODE', 'BOOTSTRAP_SECRET'):
        match = re.search(r'\b' + name + r'\s*\[\s*\]\s*=\s*"([A-Za-z0-9_-]+)"\s*;', text)
        if not match:
            raise ValueError('The factory header is missing ' + name + '.')
        values[name] = match.group(1)
    return values


def enroll(identity, url):
    body = json.dumps({'unitCode': identity['UNIT_CODE'], 'bootstrapSecret': identity['BOOTSTRAP_SECRET']}).encode()
    request = urllib.request.Request(url, data=body, headers={'Content-Type': 'application/json'}, method='POST')
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 409:
            raise RuntimeError('Enrollment pending: create the kit and claim its Door in the App first.') from None
        if error.code == 401:
            raise RuntimeError('Enrollment rejected: check that the header belongs to this server/device.') from None
        raise RuntimeError('Enrollment failed with HTTP ' + str(error.code) + '.') from None
    credential = result.get('data')
    fields = ('deviceId', 'doorId', 'deviceCode', 'nodeType', 'credentialId', 'username', 'password', 'signingKey', 'mqttPort')
    if not isinstance(credential, dict) or any(field not in credential for field in fields):
        raise RuntimeError('Enrollment response is missing required device fields.')
    return credential


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('factory_header', nargs='?', type=Path, help='Private .local.h file exported before Door claim.')
    parser.add_argument('--prepare-unit', type=inventory_code, help='Export the UUID unitCode before claim, using technician credentials in .env.')
    parser.add_argument('--username', help='Technician username for --prepare-unit (defaults to .env).')
    parser.add_argument('--ask-password', action='store_true', help='Prompt privately for the current technician App password.')
    parser.add_argument('--enrollment-url', default='http://127.0.0.1:8080/api/v1/device-enrollment')
    parser.add_argument('--host', default='127.0.0.1', help='Broker address reachable from this computer.')
    parser.add_argument('--duration', type=int, default=600)
    parser.add_argument('--enroll-only', action='store_true', help='Verify enrollment/save private export without connecting MQTT.')
    args = parser.parse_args()
    if bool(args.prepare_unit) == bool(args.factory_header):
        parser.error('Provide either a factory header path or --prepare-unit CODE.')
    if args.prepare_unit:
        password = getpass.getpass('Current technician password (hidden): ') if args.ask_password else None
        prepare_unit(args.prepare_unit, args.enrollment_url, args.username, password)
        return 0
    if args.ask_password or args.username:
        parser.error('--username and --ask-password apply to --prepare-unit.')
    if args.duration < 1:
        parser.error('duration must be positive')
    identity = read_identity(args.factory_header)
    credential = enroll(identity, args.enrollment_url)
    folder = ROOT / '.local' / 'enrolled-simulators'
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / ('node-' + str(credential['deviceId']) + '.local.json')
    # Keep any already existing export; use a new file after credential rotation.
    if path.exists():
        path = folder / ('node-' + str(credential['deviceId']) + '-' + str(credential['credentialId']) + '.local.json')
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
        json.dump(credential, output)
    print('Enrolled', credential['nodeType'], 'for Door', credential['doorId'], flush=True)
    print('Private export:', path.relative_to(ROOT), flush=True)
    if args.enroll_only:
        return 0
    print('Starting simulated heartbeat and setup ACKs. This does not test physical hardware.', flush=True)
    return subprocess.call([sys.executable, str(ROOT / 'scripts/device-simulator.py'), str(path),
                            '--host', args.host, '--port', str(credential['mqttPort']),
                            '--duration', str(args.duration)], cwd=ROOT)


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print('Simulation stopped.', file=sys.stderr)
        raise SystemExit(130)
    except (OSError, ValueError, RuntimeError) as error:
        print('Cannot simulate enrolled Node:', str(error), file=sys.stderr)
        raise SystemExit(1)

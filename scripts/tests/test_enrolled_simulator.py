import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('enrolled_simulator', Path(__file__).resolve().parents[1] / 'simulate-enrolled-node.py')
simulator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(simulator)


class EnrolledSimulatorTests(unittest.TestCase):
    def test_inventory_code_rejects_wifi_setup_code_before_network_access(self):
        with patch.object(simulator.urllib.request, 'urlopen') as transport:
            with self.assertRaisesRegex(ValueError, 'UUID unitCode'):
                simulator.prepare_unit('test_setup_16chr', 'http://localhost/api/v1/device-enrollment')
            transport.assert_not_called()

    def test_login_rejection_is_reported_as_login_failure(self):
        error = urllib.error.HTTPError('http://localhost', 401, 'Unauthorized', {}, None)
        with patch.object(simulator.urllib.request, 'urlopen', side_effect=error) as transport:
            with self.assertRaisesRegex(RuntimeError, 'Technician login failed'):
                simulator.prepare_unit('11111111-1111-4111-8111-111111111111',
                    'http://localhost/api/v1/device-enrollment', username='technician', password='private-test')
            self.assertEqual(transport.call_count, 1)

    def test_factory_denial_is_distinct_from_login_failure(self):
        login = io.BytesIO(json.dumps({'data': {'accessToken': 'test-token'}}).encode())
        error = urllib.error.HTTPError('http://localhost', 403, 'Forbidden', {}, None)
        with patch.object(simulator.urllib.request, 'urlopen', side_effect=[login, error]):
            with self.assertRaisesRegex(RuntimeError, 'Factory export denied'):
                simulator.prepare_unit('11111111-1111-4111-8111-111111111111',
                    'http://localhost/api/v1/device-enrollment', username='technician', password='private-test')

    def test_reads_the_header_format_generated_by_factory_api(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'node.local.h'
            path.write_text('#pragma once\nnamespace EnrollmentConfig {\n'
                            'constexpr char UNIT_CODE[] = "11111111-1111-4111-8111-111111111111";\n'
                            'constexpr char BOOTSTRAP_SECRET[] = "private_test_bootstrap-secret";\n}\n', encoding='utf-8-sig')
            identity = simulator.read_identity(path)
            self.assertEqual(identity['UNIT_CODE'], '11111111-1111-4111-8111-111111111111')
            self.assertEqual(identity['BOOTSTRAP_SECRET'], 'private_test_bootstrap-secret')

    def test_enrollment_uses_factory_identity_and_preserves_returned_transport(self):
        credential = dict(deviceId=1, doorId=5, deviceCode='unit', nodeType='NODE_1',
                          credentialId='id', username='user', password='test', signingKey='test', mqttPort=1884)
        response = io.BytesIO(json.dumps({'data': credential}).encode())
        with patch.object(simulator.urllib.request, 'urlopen', return_value=response) as transport:
            result = simulator.enroll({'UNIT_CODE': 'unit', 'BOOTSTRAP_SECRET': 'private-test'}, 'http://localhost/api/v1/device-enrollment')
        self.assertEqual(result, credential)
        request = transport.call_args.args[0]
        self.assertEqual(json.loads(request.data), {'unitCode': 'unit', 'bootstrapSecret': 'private-test'})

    def test_unclaimed_door_error_explains_the_required_step(self):
        error = urllib.error.HTTPError('http://localhost', 409, 'Conflict', {}, None)
        with patch.object(simulator.urllib.request, 'urlopen', side_effect=error):
            with self.assertRaisesRegex(RuntimeError, 'claim its Door'):
                simulator.enroll({'UNIT_CODE': 'unit', 'BOOTSTRAP_SECRET': 'private-test'}, 'http://localhost')

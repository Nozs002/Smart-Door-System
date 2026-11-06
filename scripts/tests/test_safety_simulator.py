"""Sensor thresholds in the Node 2 simulator."""
import runpy
from pathlib import Path
import unittest

alert = runpy.run_path(str(Path(__file__).resolve().parents[1] / "device-simulator.py"))["simulated_alert"]

class SafetySimulatorTests(unittest.TestCase):
    def test_fire_threshold_and_disabled_flag(self):
        config = {"temperatureThreshold": 50, "fireAlarmEnabled": True}
        self.assertIsNone(alert(config, temperature=50))
        self.assertEqual(alert(config, temperature=51)["type"], "HIGH_TEMPERATURE")
        config["fireAlarmEnabled"] = False
        self.assertIsNone(alert(config, temperature=80))

    def test_open_duration_requires_enabled_flag_and_timeout(self):
        config = {"doorOpenAlertEnabled": True, "openTimeoutSeconds": 30}
        self.assertIsNone(alert(config, open_seconds=30))
        self.assertEqual(alert(config, open_seconds=31)["type"], "DOOR_OPEN_TOO_LONG")
        config["doorOpenAlertEnabled"] = False
        self.assertIsNone(alert(config, open_seconds=60))
        config["doorOpenAlertEnabled"] = True
        config["openTimeoutSeconds"] = None
        self.assertIsNone(alert(config, open_seconds=60))

if __name__ == "__main__":
    unittest.main()

import tempfile
import unittest
from pathlib import Path

from generate_catalog import app_from_apk


class GenerateCatalogTest(unittest.TestCase):
    def test_app_from_apk_emits_numeric_version_code(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            apk_path = Path(temp_dir) / "com.example.app_42.apk"
            apk_path.write_bytes(b"test-apk")

            app = app_from_apk(apk_path, "http://10.0.2.2:8080")

            self.assertIsNotNone(app)
            self.assertEqual(42, app["versionCode"])


if __name__ == "__main__":
    unittest.main()

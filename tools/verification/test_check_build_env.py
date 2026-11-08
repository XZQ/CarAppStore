import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import check_build_env as envcheck


class BuildEnvCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in envcheck.WRAPPER_FILES:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("")
        self.sdk = self.root / "sdk"
        (self.sdk / "platforms" / f"android-{envcheck.REQUIRED_COMPILE_SDK}").mkdir(parents=True)
        (self.sdk / "build-tools" / "36.0.0").mkdir(parents=True)
        (self.sdk / "platform-tools").mkdir()
        self.jdk = self.root / "jdk"
        (self.jdk / "bin").mkdir(parents=True)
        (self.jdk / "bin" / ("java.exe" if os.name == "nt" else "java")).write_text("")
        self.env = {"ANDROID_HOME": str(self.sdk), "JAVA_HOME": str(self.jdk)}
        self.probe = lambda env: (str(self.jdk / "bin" / "java"), 'openjdk version "21.0.11" 2026-10-28')

    def check(self, env=None, probe=None):
        return envcheck.check_environment(
            self.root,
            env=self.env if env is None else env,
            java_probe=self.probe if probe is None else probe,
        )

    def statuses(self, report, name):
        return [item.status for item in report.checks if item.name == name]

    def test_complete_environment_passes(self):
        report = self.check()
        self.assertFalse(report.failed)
        self.assertEqual(self.statuses(report, "SDK Platform"), [envcheck.OK])
        self.assertEqual(self.statuses(report, "java 版本"), [envcheck.OK])

    def test_missing_java_is_a_failure(self):
        probe = lambda env: (None, "")
        report = self.check(probe=probe)
        self.assertTrue(report.failed)
        self.assertEqual(self.statuses(report, "java 可执行文件"), [envcheck.FAIL])
        self.assertIn("安装 JDK 21", envcheck.render(report))

    def test_java_8_is_rejected(self):
        probe = lambda env: (str(self.jdk / "bin" / "java"), 'java version "1.8.0_292"')
        report = self.check(probe=probe)
        self.assertTrue(report.failed)
        self.assertEqual(self.statuses(report, "java 版本"), [envcheck.FAIL])

    def test_unparseable_version_is_rejected(self):
        report = self.check(probe=lambda env: (str(self.jdk / "bin" / "java"), "not a jvm"))
        self.assertTrue(report.failed)

    def test_java_21_is_the_verified_baseline(self):
        report = self.check()
        self.assertFalse(report.failed)
        self.assertEqual(self.statuses(report, "JDK 基线"), [])

    def test_java_17_is_rejected(self):
        probe = lambda env: (str(self.jdk / "bin" / "java"), 'openjdk version "17.0.9" 2023-10-17')
        report = self.check(probe=probe)
        self.assertTrue(report.failed)
        self.assertEqual(self.statuses(report, "java 版本"), [envcheck.FAIL])

    def test_newer_jdk_warns_but_passes(self):
        probe = lambda env: (str(self.jdk / "bin" / "java"), 'openjdk version "25.0.1" 2026-01-20')
        report = self.check(probe=probe)
        self.assertFalse(report.failed)
        self.assertEqual(self.statuses(report, "JDK 基线"), [envcheck.WARN])

    def test_missing_sdk_root_is_a_failure(self):
        report = self.check(env={"JAVA_HOME": str(self.jdk)})
        self.assertTrue(report.failed)
        self.assertEqual(self.statuses(report, "Android SDK 路径"), [envcheck.FAIL])
        self.assertEqual(self.statuses(report, "local.properties"), [envcheck.WARN])

    def test_nonexistent_sdk_directory_is_a_failure(self):
        report = self.check(env={"ANDROID_HOME": str(self.root / "absent"), "JAVA_HOME": str(self.jdk)})
        self.assertTrue(report.failed)

    def test_missing_compile_sdk_platform_is_a_failure(self):
        (self.sdk / "platforms" / f"android-{envcheck.REQUIRED_COMPILE_SDK}").rmdir()
        report = self.check()
        self.assertTrue(report.failed)
        self.assertEqual(self.statuses(report, "SDK Platform"), [envcheck.FAIL])
        self.assertIn("sdkmanager", envcheck.render(report))

    def test_missing_build_tools_is_only_a_warning(self):
        (self.sdk / "build-tools" / "36.0.0").rmdir()
        (self.sdk / "build-tools").rmdir()
        report = self.check()
        self.assertFalse(report.failed)
        self.assertEqual(self.statuses(report, "SDK Build Tools"), [envcheck.WARN])

    def test_local_properties_can_supply_escaped_windows_sdk_path(self):
        escaped = str(self.sdk).replace("\\", "\\\\").replace(":", "\\:")
        (self.root / "local.properties").write_text(
            "# generated locally, never committed\nsdk.dir=" + escaped + "\n", encoding="utf-8"
        )
        report = self.check(env={"JAVA_HOME": str(self.jdk)})
        self.assertFalse(report.failed)
        self.assertEqual(self.statuses(report, "local.properties"), [envcheck.OK])
        self.assertEqual(envcheck.unescape_property(escaped), str(self.sdk))

    def test_local_properties_ignores_comments_and_other_keys(self):
        (self.root / "local.properties").write_text(
            "# sdk.dir=/wrong\nfoo=bar\n", encoding="utf-8"
        )
        self.assertIsNone(envcheck.read_sdk_dir_from_local_properties(self.root))

    def test_environment_variable_wins_over_local_properties(self):
        (self.root / "local.properties").write_text("sdk.dir=/does/not/exist\n", encoding="utf-8")
        report = self.check()
        self.assertFalse(report.failed)

    def test_missing_wrapper_files_are_a_failure(self):
        (self.root / "gradlew.bat").unlink()
        report = self.check()
        self.assertTrue(report.failed)
        self.assertEqual(self.statuses(report, "Gradle wrapper"), [envcheck.FAIL])

    def test_json_report_shape_and_exit_code(self):
        report = envcheck.Report([
            envcheck.Check("sample", envcheck.FAIL, "missing", "install it"),
        ])
        output = io.StringIO()
        with mock.patch.object(envcheck, "check_environment", return_value=report):
            with contextlib.redirect_stdout(output):
                code = envcheck.main(["--repo-root", str(self.root), "--json"])
        self.assertEqual(code, 1)
        payload = json.loads(output.getvalue())
        self.assertTrue(payload["failed"])
        self.assertEqual(payload["checks"][0]["fix"], "install it")


class JavaVersionParsingTest(unittest.TestCase):
    def test_modern_and_legacy_formats(self):
        cases = {
            'openjdk version "17.0.9" 2023-10-17': 17,
            'java version "21.0.11" 2026-10-28': 21,
            'java version "1.8.0_292"': 8,
            'openjdk version "11.0.20" 2023-07-18': 11,
            "": None,
            "no version here": None,
        }
        for text, expected in cases.items():
            with self.subTest(text=text):
                self.assertEqual(envcheck.parse_java_major(text), expected)


if __name__ == "__main__":
    unittest.main()

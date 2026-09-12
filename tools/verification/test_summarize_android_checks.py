import tempfile
import unittest
from pathlib import Path

from summarize_android_checks import summarize


class AndroidSummaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        module = self.root / "app"
        (module / "src/test").mkdir(parents=True)
        (module / "build.gradle.kts").write_text("")
        (module / "src/test/ExampleTest.kt").write_text("class ExampleTest")
        for variant in ("debug", "release"):
            tests = module / f"build/test-results/test{variant.title()}UnitTest"
            tests.mkdir(parents=True)
            (tests / "TEST-example.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
            lint = module / "build/reports"
            lint.mkdir(parents=True, exist_ok=True)
            (lint / f"lint-results-{variant}.xml").write_text('<issues><issue severity="Warning"/></issues>')

    def test_missing_release_results_cannot_pass(self):
        (self.root / "app/build/test-results/testReleaseUnitTest/TEST-example.xml").unlink()
        self.assertFalse(summarize(self.root)["passed"])

    def test_failed_tests_cannot_pass(self):
        (self.root / "app/build/test-results/testDebugUnitTest/TEST-example.xml").write_text(
            '<testsuite tests="2" failures="1" errors="0" skipped="0"/>')
        self.assertFalse(summarize(self.root)["passed"])

    def test_complete_reports_count_warnings_without_claiming_device_proof(self):
        result = summarize(self.root)
        self.assertTrue(result["passed"])
        self.assertEqual(2, result["variants"]["release"]["tests"])
        self.assertEqual(1, result["variants"]["debug"]["lint_warnings"])

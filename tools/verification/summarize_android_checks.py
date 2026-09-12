"""Summarize JUnit and Android Lint XML; missing or failing gates never count as PASS."""

import argparse
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path


def summarize(root: Path) -> dict:
    result = {"scope": "Local JVM/Robolectric and static analysis; no device or production proof", "variants": {}}
    modules = sorted(path.parent for path in root.glob("*/build.gradle.kts"))
    for variant in ("debug", "release"):
        totals = dict(suites=0, tests=0, failures=0, errors=0, skipped=0, lint_reports=0, lint_errors=0, lint_warnings=0)
        missing = []
        for module in modules:
            test_task = f"test{variant.title()}UnitTest"
            reports = sorted(module.glob(f"build/test-results/{test_task}/TEST-*.xml"))
            source_sets = ("test", f"test{variant.title()}")
            has_tests = any(any((module / "src" / source).rglob(f"*.{extension}"))
                            for source in source_sets for extension in ("kt", "java"))
            if has_tests and not reports:
                missing.append(f"{module.name}:{test_task}")
            for report in reports:
                suite = ET.parse(report).getroot()
                totals["suites"] += 1
                for key in ("tests", "failures", "errors", "skipped"):
                    totals[key] += int(suite.get(key, "0"))
            lint = module / f"build/reports/lint-results-{variant}.xml"
            if lint.is_file():
                totals["lint_reports"] += 1
                for issue in ET.parse(lint).getroot().findall("issue"):
                    if issue.get("severity") in ("Error", "Fatal"):
                        totals["lint_errors"] += 1
                    elif issue.get("severity") == "Warning":
                        totals["lint_warnings"] += 1
            else:
                missing.append(f"{module.name}:lint{variant.title()}")
        passed = bool(modules) and totals["tests"] > 0 and not missing and not any(
            totals[key] for key in ("failures", "errors", "lint_errors"))
        result["variants"][variant] = {**totals, "missing": missing, "passed": passed}
    result["passed"] = all(item["passed"] for item in result["variants"].values())
    return result


def markdown(result: dict) -> str:
    lines = ["# Android verification", "", f"Commit: `{result['commit']}`", "", result["scope"], "",
             "| Variant | Tests | Failures / errors | Skipped | Lint errors / warnings | Result |",
             "| --- | ---: | --- | ---: | --- | --- |"]
    missing = []
    for variant, item in result["variants"].items():
        lines.append(f"| {variant} | {item['tests']} | {item['failures']} / {item['errors']} | "
                     f"{item['skipped']} | {item['lint_errors']} / {item['lint_warnings']} | "
                     f"{'PASS' if item['passed'] else 'FAIL / INCOMPLETE'} |")
        missing.extend(item["missing"])
    if missing:
        lines.extend(["", "Missing: " + ", ".join(missing), ""])
    lines.extend(["", "Lint counts are report entries and may repeat shared-module findings.", ""])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output-dir", type=Path, default=Path("build/verification"))
    args = parser.parse_args()
    root = args.root.resolve()
    result = summarize(root)
    result["commit"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    (output / "android-checks.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    (output / "android-checks.md").write_text(markdown(result), encoding="utf-8")
    print(markdown(result))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

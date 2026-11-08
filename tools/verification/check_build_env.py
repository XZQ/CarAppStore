"""Diagnose the local Android build toolchain without installing anything.

The check only reads environment variables, repository files and ``java -version``.
It never writes to the repository, never downloads an SDK and never runs Gradle.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

MIN_JAVA_MAJOR = 21
BASELINE_JAVA_MAJOR = 21
REQUIRED_COMPILE_SDK = 36

OK = "OK"
WARN = "WARN"
FAIL = "FAIL"

WRAPPER_FILES = (
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
)


@dataclass
class Check:
    name: str
    status: str
    detail: str
    fix: str = ""


@dataclass
class Report:
    checks: list[Check] = field(default_factory=list)

    @property
    def failed(self) -> bool:
        return any(item.status == FAIL for item in self.checks)

    def as_dict(self) -> dict:
        return {
            "failed": self.failed,
            "checks": [
                {"name": item.name, "status": item.status, "detail": item.detail, "fix": item.fix}
                for item in self.checks
            ],
        }


def parse_java_major(output: str) -> int | None:
    """Return the Java major version from `java -version` output, or None."""
    match = re.search(r'version\s+"(\d+)(?:\.(\d+))?', output)
    if match is None:
        return None
    major = int(match.group(1))
    if major == 1:
        return int(match.group(2) or 0) or None
    return major


def unescape_property(value: str) -> str:
    """Undo Java properties escaping, e.g. ``C\\:\\\\Android\\\\Sdk``."""
    return re.sub(r"\\(.)", r"\1", value)


def read_sdk_dir_from_local_properties(root: Path) -> str | None:
    path = root / "local.properties"
    if not path.is_file():
        return None
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")) or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        if key.strip() == "sdk.dir":
            return unescape_property(value.strip()) or None
    return None


def resolve_sdk_root(env: dict, local_sdk_dir: str | None) -> str | None:
    for candidate in (env.get("ANDROID_HOME"), env.get("ANDROID_SDK_ROOT"), local_sdk_dir):
        if candidate and candidate.strip():
            return candidate.strip()
    return None


def default_java_probe(env: dict) -> tuple[str | None, str]:
    """Locate java and collect its version output; never raises on a broken setup."""
    candidates: list[Path] = []
    java_home = env.get("JAVA_HOME", "").strip()
    if java_home:
        executable = "java.exe" if os.name == "nt" else "java"
        candidates.append(Path(java_home) / "bin" / executable)
    from_path = shutil.which("java")
    if from_path:
        candidates.append(Path(from_path))
    for candidate in candidates:
        if not candidate.is_file():
            continue
        try:
            completed = subprocess.run(
                [str(candidate), "-version"],
                capture_output=True,
                text=True,
                timeout=15,
                check=False,
            )
        except (OSError, subprocess.SubprocessError):
            continue
        return str(candidate), (completed.stdout or "") + (completed.stderr or "")
    return None, ""


def check_environment(root: Path, env: dict | None = None, java_probe=None) -> Report:
    root = Path(root)
    env = dict(os.environ if env is None else env)
    java_probe = java_probe or default_java_probe
    report = Report()

    missing_wrapper = [name for name in WRAPPER_FILES if not (root / name).is_file()]
    report.checks.append(Check(
        "Gradle wrapper",
        FAIL if missing_wrapper else OK,
        "缺少 " + ", ".join(missing_wrapper) if missing_wrapper else "gradlew / gradlew.bat / wrapper jar 完整",
        "从仓库重新拉取 Gradle wrapper，不要手写 gradlew 脚本" if missing_wrapper else "",
    ))

    java_path, version_output = java_probe(env)
    if java_path is None:
        report.checks.append(Check(
            "java 可执行文件", FAIL, "未找到 java，或无法执行 java -version",
            "安装 JDK 21 并设置 JAVA_HOME，例如 Windows: $env:JAVA_HOME='<jdk-21>'；macOS/Linux: export JAVA_HOME=<jdk-21>",
        ))
    else:
        major = parse_java_major(version_output)
        if major is None:
            report.checks.append(Check(
                "java 版本", FAIL, f"无法从 {java_path} 解析版本号",
                "确认 java -version 输出包含 version \"21.x\" 之类的版本串",
            ))
        elif major < MIN_JAVA_MAJOR:
            report.checks.append(Check(
                "java 版本", FAIL, f"检测到 Java {major}，AGP 8.13.2 至少需要 Java {MIN_JAVA_MAJOR}",
                "安装 JDK 21 并让 JAVA_HOME / PATH 指向它",
            ))
        else:
            report.checks.append(Check("java 版本", OK, f"Java {major}（{java_path}）"))
            if major != BASELINE_JAVA_MAJOR:
                report.checks.append(Check(
                    "JDK 基线", WARN, f"工程以 JDK {BASELINE_JAVA_MAJOR} 为验证基线，当前为 Java {major}",
                    "Gradle 8.14 最高支持 Java 24；若构建异常，改用 JDK 21 (JBR/Temurin) 复现",
                ))

    java_home = env.get("JAVA_HOME", "").strip()
    if not java_home:
        report.checks.append(Check(
            "JAVA_HOME", WARN, "未设置；Gradle 将退而使用 PATH 中的 java",
            "建议显式设置 JAVA_HOME 指向 JDK 21 根目录",
        ))
    elif not (Path(java_home) / "bin").is_dir():
        report.checks.append(Check(
            "JAVA_HOME", WARN, f"指向的目录不含 bin/：{java_home}",
            "JAVA_HOME 必须指向 JDK 根目录，而不是 bin 目录",
        ))
    else:
        report.checks.append(Check("JAVA_HOME", OK, java_home))

    local_sdk_dir = read_sdk_dir_from_local_properties(root)
    sdk_value = resolve_sdk_root(env, local_sdk_dir)
    if sdk_value is None:
        report.checks.append(Check(
            "Android SDK 路径", FAIL, "ANDROID_HOME / ANDROID_SDK_ROOT / local.properties 均未提供 SDK 路径",
            "安装 Android SDK 后设置 ANDROID_HOME，或在仓库根创建 local.properties 写入 sdk.dir=<SDK 路径>",
        ))
    elif not Path(sdk_value).is_dir():
        report.checks.append(Check(
            "Android SDK 路径", FAIL, f"SDK 目录不存在：{sdk_value}",
            "修正 ANDROID_HOME / sdk.dir，或安装 Android SDK 到该路径",
        ))
    else:
        sdk = Path(sdk_value)
        report.checks.append(Check("Android SDK 路径", OK, str(sdk)))
        platform = sdk / "platforms" / f"android-{REQUIRED_COMPILE_SDK}"
        if platform.is_dir():
            report.checks.append(Check(
                "SDK Platform", OK, f"platforms;android-{REQUIRED_COMPILE_SDK} 已安装",
            ))
        else:
            report.checks.append(Check(
                "SDK Platform", FAIL, f"缺少 platforms/android-{REQUIRED_COMPILE_SDK}（compileSdk = {REQUIRED_COMPILE_SDK}）",
                f'sdkmanager "platforms;android-{REQUIRED_COMPILE_SDK}" "build-tools;36.0.0"',
            ))
        for name, folder in (("SDK Build Tools", "build-tools"), ("SDK Platform Tools", "platform-tools")):
            if (sdk / folder).is_dir():
                report.checks.append(Check(name, OK, f"{folder}/ 已安装"))
            else:
                report.checks.append(Check(
                    name, WARN, f"未找到 {folder}/",
                    f'sdkmanager "build-tools;36.0.0"' if folder == "build-tools" else 'sdkmanager "platform-tools"',
                ))

    has_local_properties = (root / "local.properties").is_file()
    if has_local_properties:
        report.checks.append(Check("local.properties", OK, "已存在（已在 .gitignore 中，不会提交）"))
    elif resolve_sdk_root(env, None) is not None:
        report.checks.append(Check(
            "local.properties", OK, "未创建；SDK 路径由 ANDROID_HOME / ANDROID_SDK_ROOT 提供（CI 常见）",
        ))
    else:
        report.checks.append(Check(
            "local.properties", WARN, "缺失且环境变量未提供 SDK 路径",
            "在仓库根创建 local.properties 并写入 sdk.dir=<SDK 绝对路径>",
        ))

    return report


def render(report: Report) -> str:
    lines = [
        f"CarAppStore 构建环境自检 · JDK >= {MIN_JAVA_MAJOR} / Android SDK Platform {REQUIRED_COMPILE_SDK}",
        "",
    ]
    for item in report.checks:
        lines.append(f"[{item.status:<4}] {item.name}: {item.detail}")
    failures = sum(item.status == FAIL for item in report.checks)
    warnings = sum(item.status == WARN for item in report.checks)
    lines.append("")
    if report.failed:
        lines.append(f"结果：{failures} 项失败、{warnings} 项提示。修复后重新运行本检查再执行 gradlew。")
        fixes = [item for item in report.checks if item.status == FAIL and item.fix]
        if fixes:
            lines.append("")
            lines.append("修复建议：")
            for item in fixes:
                lines.append(f"- {item.name}: {item.fix}")
    else:
        lines.append(f"结果：无失败项（{warnings} 项提示）。可以运行 gradlew 执行验证门禁。")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="Repository root (defaults to two directories above this script).",
    )
    parser.add_argument("--json", action="store_true", help="Print a machine-readable report.")
    args = parser.parse_args(argv)

    report = check_environment(args.repo_root, env=dict(os.environ))
    if args.json:
        print(json.dumps(report.as_dict(), ensure_ascii=False, indent=2))
    else:
        print(render(report))
    return 1 if report.failed else 0


if __name__ == "__main__":
    sys.exit(main())

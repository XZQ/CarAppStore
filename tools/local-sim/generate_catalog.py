#!/usr/bin/env python3
"""Generate LOCAL_SIM catalog metadata from ignored APK assets."""

import argparse
import hashlib
import json
import re
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(64 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def app_from_apk(path: Path, base_url: str) -> dict | None:
    match = re.match(r"^(.+?)_(\d+)\.apk$", path.name)
    if match is None:
        return None
    package_name, version_code = match.groups()
    name = package_name.rsplit(".", 1)[-1].replace("_", " ").title()
    return {
        "appId": package_name,
        "packageName": package_name,
        "name": name,
        "description": f"{name} is an open-source app in the LOCAL_SIM catalog.",
        "versionName": version_code,
        "versionCode": int(version_code),
        "category": "工具",
        "editorialTag": "LOCAL_SIM",
        "iconText": name[:1] or "?",
        "heroText": name,
        "iconUrl": "",
        "bannerUrl": "",
        "screenshotUrls": [],
        "recommendedReason": "本地模拟目录中的开源应用",
        "searchKeywords": [package_name.rsplit(".", 1)[-1], "工具"],
        "developerName": "Open source community",
        "ratingText": "",
        "sizeText": f"{path.stat().st_size // 1024} KB",
        "lastUpdatedText": "LOCAL_SIM",
        "compatibilitySummary": "",
        "permissionsSummary": "",
        "updateSummary": "",
        "latestVersion": version_code,
        "apkUrl": f"{base_url.rstrip('/')}/{path.name}",
        "checksumType": "SHA-256",
        "checksumValue": sha256(path),
        "sourcePolicy": "DIRECT_HTTP",
        "listingState": "ACTIVE",
        "rolloutPercent": 100,
        "allowedChannels": [],
        "blockedChannels": [],
        "rollbackVersion": "",
        "hasUpgrade": False,
        "changelog": "LOCAL_SIM generated catalog",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset-dir", default="local-apks")
    parser.add_argument("--base-url", default="http://10.0.2.2:8080")
    parser.add_argument("--raw-output", default="data/src/main/res/raw/app_store_catalog.json")
    args = parser.parse_args()
    asset_dir = Path(args.asset_dir)
    apps = [app for apk in sorted(asset_dir.glob("*.apk")) if (app := app_from_apk(apk, args.base_url)) is not None]
    if not apps:
        raise SystemExit(f"No APK files found in {asset_dir}")
    catalog = json.dumps({"apps": apps}, ensure_ascii=False, indent=2) + "\n"
    (asset_dir / "catalog.json").write_text(catalog, encoding="utf-8")
    Path(args.raw_output).write_text(catalog, encoding="utf-8")
    print(f"Generated {len(apps)} apps")


if __name__ == "__main__":
    main()

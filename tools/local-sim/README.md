# LOCAL_SIM tools

APK binaries and `catalog.json` remain in ignored `local-apks/`; this directory contains the tracked tooling needed to recreate them.

1. Place legal test APKs named `<packageName>_<versionCode>.apk` in `local-apks/`.
2. Run `python tools/local-sim/generate_catalog.py` from the repository root.
3. Run `python tools/local-sim/serve.py` from the repository root.
4. Select LOCAL_SIM in the debug developer settings. The app restarts to rebuild its environment; emulator traffic uses `http://10.0.2.2:8080`.

The debug manifest allows cleartext only for `10.0.2.2`. Release builds retain the platform default and do not permit the local HTTP server.

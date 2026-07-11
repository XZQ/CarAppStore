# CarAppStore Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current CarAppStore checkout buildable, testable, locally reproducible, and operationally honest for download, installation, upgrade, and device state.

**Architecture:** Preserve the existing 13-module MVVM architecture. Fix each defect at its boundary: test construction in data, equality policy in common UI, debug networking and environment lifecycle in app/data, installation capabilities in core/feature, system facts in data, and terminal waiting in business. Production keeps fail-closed parking and HTTPS defaults; local simulation is debug-only.

**Tech Stack:** Kotlin 2.0.0, AGP 8.13.2, Android API 26–34, Coroutines, ViewBinding, Robolectric, JUnit4, JSON persistence.

## Global Constraints

- Keep `Activity + FragmentManager` navigation and manual `AppContainer`; do not add Hilt, Navigation, or dependencies.
- Keep production traffic HTTPS-only and production vehicle policy fail-closed.
- Use `apply_patch` for edits and retain existing user modifications in `.gitignore`, `AppPrimaryActionExecutor`, and upgrade feature files.
- Every production behavior change starts with a failing unit or Robolectric test.
- Do not commit while unrelated user changes are present; provide a precise staged-file handoff instead.

---

### Task 1: Restore the compile and Lint baseline

**Files:**
- Modify: `data/src/main/java/com/xzq/appstore/data/datasource/remote/AppCatalogSource.kt`
- Modify: `data/src/test/java/com/xzq/appstore/data/datasource/remote/AppCatalogSourceTest.kt`
- Move/Modify: `business/src/main/java/com/xzq/appstore/common/base/AppIdDiffCallback.kt`
- Modify: all feature adapters that create `AppIdDiffCallback`
- Test: `data/src/test/java/com/xzq/appstore/data/datasource/remote/AppCatalogSourceTest.kt`

**Interfaces:** `ResilientAppCatalogSource` accepts a default no-op grayscale-header provider. `AppIdDiffCallback<T>` accepts an explicit `(T, T) -> Boolean` content comparator.

- [x] Write a test that verifies a default `ResilientAppCatalogSource` request does not add a grayscale header.
- [x] Run `:data:testDebugUnitTest --tests '*AppCatalogSourceTest*'` and observe the pre-fix constructor compilation failure.
- [x] Add the no-op provider default and explicit content comparator wiring.
- [x] Run the focused data tests and `:business:lintDebug`; both pass.

### Task 2: Make LOCAL_SIM reproducible and debug-only

**Files:**
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `tools/local-sim/serve.py`
- Create: `tools/local-sim/generate_catalog.py`
- Create: `tools/local-sim/README.md`
- Modify: `docs/32-本地验证指南.md`

**Interfaces:** Debug builds permit cleartext only for local simulation. Tracked tools generate catalog metadata and serve ignored `local-apks/` artifacts with HTTP Range support.

- [ ] Add a manifest-overlay test/inspection that confirms the release manifest has no cleartext override.
- [x] Copy the existing local server and catalog generator into the tracked tools directory without adding APK binaries.
- [x] Document deterministic artifact preparation and emulator address usage.
- [x] Run the generator help command; Range support is implemented by the tracked server and remains for device-side verification with real ignored APK assets.

### Task 3: Apply environment changes to live dependencies

**Files:**
- Modify: `app/src/main/java/com/xzq/appstore/app/App.kt`
- Modify: `app/src/main/java/com/xzq/appstore/app/AppContainer.kt`
- Modify: `app/src/main/java/com/xzq/appstore/app/MainActivity.kt`
- Modify: `business/src/main/java/com/xzq/appstore/common/base/AppServices.kt`
- Modify: `common/src/main/java/com/xzq/appstore/common/navigation/MainNavigator.kt`
- Modify: `feature-debug/src/main/java/com/xzq/appstore/feature/debug/DeveloperSettingsFragment.kt`
- Test: `data/src/test/java/com/xzq/appstore/data/downloadenv/LocalDownloadEnvironmentProviderTest.kt`

**Interfaces:** `AppServices` exposes a single environment change request; `MainNavigator` restarts the task after `App` replaces the AppContainer. Feature Debug never writes through an unrelated in-memory provider.

- [ ] Write a failing provider test showing persisted environment wins after provider recreation.
- [x] Add a container replacement entry point and navigator restart method.
- [x] Route developer environment buttons through the shared controller and restart into the new container.
- [ ] Run the provider and relevant Robolectric tests.

### Task 4: Close installation permission and local vehicle paths

**Files:**
- Create: `core/src/main/java/com/xzq/appstore/core/installer/InstallPermissionGateway.kt`
- Modify: `core/src/main/java/com/xzq/appstore/core/installer/PackageInstaller.kt`
- Modify: `core/src/main/java/com/xzq/appstore/core/installer/RealPackageInstaller.kt`
- Modify: `core/src/main/java/com/xzq/appstore/core/installer/PackageInstallerSessionAdapter.kt`
- Modify: `app/src/main/java/com/xzq/appstore/app/AppContainer.kt`
- Modify: `feature-downloadmanager/src/main/java/com/xzq/appstore/feature/downloadmanager/DownloadManagerFragment.kt`
- Modify: `feature-installcenter/src/main/java/com/xzq/appstore/feature/installcenter/InstallCenterFragment.kt`
- Modify: `feature-upgrade/src/main/java/com/xzq/appstore/feature/upgrade/UpgradeFragment.kt`
- Test: `core/src/test/java/com/xzq/appstore/core/installer/RealPackageInstallerTest.kt`

**Interfaces:** `InstallPermissionGateway.canRequestInstalls()` distinguishes missing user permission from unsupported Session APIs; `openSettingsIntent()` gives the feature layer the correct system settings route.

- [ ] Add failing tests for permission-required install failure and settings intent shape.
- [x] Add `InstallFailureCode.PERMISSION_REQUIRED` and preflight it before session creation.
- [x] Replace empty permission-banner callbacks with settings intents.
- [x] Select a parked-only local simulation vehicle provider in debug LOCAL_SIM; production retains `StaticVehicleStateSignalProvider(parkingMode=false)` unless OEM broadcast config is supplied.
- [x] Run core and business install tests.

### Task 5: Use PackageManager as installed-app truth

**Files:**
- Modify: `data/src/main/java/com/xzq/appstore/data/datasource/system/AppSystemDataSource.kt`
- Modify: `data/src/main/java/com/xzq/appstore/data/repository/RealAppRepository.kt`
- Test: `data/src/test/java/com/xzq/appstore/data/repository/RealAppRepositoryTest.kt`

**Interfaces:** Repository derives managed installed apps from catalog package names queried through `AppSystemDataSource`, while JSON remains task/cache storage and fallback if catalog access fails.

- [x] Write a failing Robolectric test where the local mirror says installed but PackageManager does not.
- [x] Merge catalog package names with local fallback and return PackageManager versions when available.
- [x] Update `isInstalled(appId)` to resolve the catalog package and query PackageManager before fallback.
- [x] Run data repository and system datasource tests.

### Task 6: Bound upgrade waiting and preserve terminal errors

**Files:**
- Modify: `business/src/main/java/com/xzq/appstore/domain/upgrade/DefaultUpgradeManager.kt`
- Modify: `business/src/main/java/com/xzq/appstore/domain/text/BusinessText.kt`
- Test: `business/src/test/java/com/xzq/appstore/domain/upgrade/DefaultUpgradeManagerTest.kt`

**Interfaces:** A private `awaitTerminalState` helper receives timeout and polling values through constructor defaults, emits a stable timeout message, and prevents batch upgrade from waiting indefinitely.

- [x] Write failing tests for download timeout and install timeout using short injected polling and timeout values.
- [x] Replace unbounded loops with `withTimeoutOrNull`-based terminal waiting.
- [x] Preserve existing pause/cancel/failure mapping and batch-stop semantics.
- [x] Run focused upgrade tests.

### Task 7: Synchronize documentation and verify the release boundary

**Files:**
- Modify: `README.md`
- Modify: `docs/21-当前项目状态与接手指南.md`
- Modify: `docs/28-商用化剩余事项.md`
- Modify: `docs/29-换机接手与当前进度总览.md`
- Modify: `docs/31-UI补全与下载联调计划.md`
- Modify: `docs/32-本地验证指南.md`

- [x] Update Kotlin/version/module-count/build-status claims using fresh verification output.
- [x] State the difference between implemented installation code, debug local simulation, and production OEM/permission requirements.
- [x] List ignored APK assets and tracked local-sim tooling.
- [x] Run `testDebugUnitTest`, `lintDebug`, `:app:assembleDebug`, and `:app:assembleRelease` with JDK 17.
- [ ] Confirm `HEAD == origin/main` only if no commit is made; otherwise report the exact local diff and artifact paths.

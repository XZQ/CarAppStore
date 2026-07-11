# Install Trust and Download Path Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent untrusted catalog identifiers from escaping download storage and prevent unverified APK identities from reaching `PackageInstaller.Session`.

**Architecture:** Extend the catalog and `InstallRequest` with expected package identity metadata, validate it through a pure core validator plus an Android PackageManager reader, and reconcile the installed package after the platform callback. Validate identifiers at the catalog boundary and again at `DownloadStore` with canonical containment.

**Tech Stack:** Kotlin 2.0, Android PackageManager API 26–34, JUnit4, Robolectric, Gradle 8.14.3, Python 3 local-sim tooling.

## Global Constraints

- Preserve the existing `app -> business -> data/core` dependencies and manual `AppContainer` assembly.
- Do not add libraries, Hilt, Dagger, Navigation, databases, or new frameworks.
- Maximum Kotlin line width is 160 characters.
- Release, DEV, and TEST require positive `versionCode` and at least one signer digest.
- Only `BuildConfig.DEBUG + LOCAL_SIM` may allow missing `versionCode` or signer digests; package name matching is always strict.
- Use tests before production changes and observe each targeted test fail for the intended reason.
- Preserve existing user changes in `DownloadSourceResolver.kt`, `DownloadStore.kt`, and `SimulatedFileDownloader.kt`.
- Do not create commits or push unless the user explicitly requests it after implementation.

---

### Task 1: Validate catalog identifiers and package identity metadata

**Files:**
- Create: `data/src/main/java/com/xzq/appstore/data/datasource/remote/AppCatalogValidator.kt`
- Modify: `data/src/main/java/com/xzq/appstore/data/datasource/remote/AppCatalogJsonParser.kt`
- Modify: `data/src/main/java/com/xzq/appstore/data/datasource/remote/AppCatalogDto.kt`
- Modify: `data/src/main/java/com/xzq/appstore/data/model/AppDetail.kt`
- Test: `data/src/test/java/com/xzq/appstore/data/datasource/remote/AppCatalogJsonParserTest.kt`

**Interfaces:**
- Produces: `AppDetail.versionCode: Long` and `AppDetail.signerCertificateSha256: List<String>`.
- Produces: `AppCatalogValidator.validate(items: List<AppCatalogItemResponse>): List<AppCatalogItemResponse>`.

- [x] **Step 1: Write failing parser tests**

Add tests that parse valid `versionCode` and signer values and reject traversal IDs, illegal package names, duplicate IDs/packages, and malformed signer digests:

```kotlin
@Test
fun `parseResponse 会解析版本代码并规范化签名摘要`() {
    val item = AppCatalogJsonParser.parseResponse(identityCatalog()).apps.single()
    assertEquals(230L, item.versionCode)
    assertEquals(listOf(TEST_SIGNER.lowercase()), item.signerCertificateSha256)
}

@Test(expected = IllegalArgumentException::class)
fun `parseResponse 拒绝路径穿越 appId`() {
    AppCatalogJsonParser.parseResponse(identityCatalog(appId = "../../escape"))
}

@Test(expected = IllegalArgumentException::class)
fun `parseResponse 拒绝非法包名`() {
    AppCatalogJsonParser.parseResponse(identityCatalog(packageName = "../package"))
}

@Test(expected = IllegalArgumentException::class)
fun `parseResponse 拒绝重复 appId`() {
    AppCatalogJsonParser.parseResponse(twoItemCatalog(secondAppId = "nav.map"))
}

@Test(expected = IllegalArgumentException::class)
fun `parseResponse 拒绝重复 packageName`() {
    AppCatalogJsonParser.parseResponse(twoItemCatalog(secondPackageName = "com.nio.map"))
}

@Test(expected = IllegalArgumentException::class)
fun `parseResponse 拒绝非法签名摘要`() {
    AppCatalogJsonParser.parseResponse(identityCatalog(signer = "not-a-sha256"))
}
```

- [x] **Step 2: Run the parser tests and verify RED**

Run:

```powershell
$env:JAVA_HOME="C:\Users\XZQ\.jdks\jbr-17.0.14"
.\gradlew.bat :data:testDebugUnitTest --tests "*AppCatalogJsonParserTest" --no-daemon
```

Expected: compilation failures for missing `versionCode`/`signerCertificateSha256`, followed by assertion failures until validation exists.

- [x] **Step 3: Implement the catalog validator and mappings**

Add fields to DTO and model with backward-compatible defaults:

```kotlin
val versionCode: Long = 0L,
val signerCertificateSha256: List<String> = emptyList(),
```

Implement validation:

```kotlin
object AppCatalogValidator {
    private val appIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val packageNamePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val signerPattern = Regex("[A-Fa-f0-9]{64}")

    fun validate(items: List<AppCatalogItemResponse>): List<AppCatalogItemResponse> {
        items.forEach { item ->
            require(appIdPattern.matches(item.appId)) { "非法目录 appId: ${item.appId}" }
            require(packageNamePattern.matches(item.packageName)) { "非法目录 packageName: ${item.packageName}" }
            require(item.versionName.isNotBlank() && item.versionName.length <= 128) { "非法目录 versionName: ${item.appId}" }
            require(item.versionCode >= 0L) { "非法目录 versionCode: ${item.appId}" }
            require(item.signerCertificateSha256.all(signerPattern::matches)) { "非法目录签名摘要: ${item.appId}" }
        }
        require(items.map { it.appId }.distinct().size == items.size) { "目录包含重复 appId" }
        require(items.map { it.packageName }.distinct().size == items.size) { "目录包含重复 packageName" }
        return items
    }
}
```

Normalize signer lists during parsing with `trim().lowercase()` and `distinct()`, call `AppCatalogValidator.validate()` before returning the response, and map both fields into `AppDetail`.

- [x] **Step 4: Run parser tests and verify GREEN**

Run the Step 2 command. Expected: all `AppCatalogJsonParserTest` tests pass.

---

### Task 2: Contain download task paths

**Files:**
- Modify: `core/src/main/java/com/xzq/appstore/core/downloader/DownloadStore.kt`
- Test: `core/src/test/java/com/xzq/appstore/core/downloader/DownloadStoreTest.kt`

**Interfaces:**
- Preserves: `DownloadStore.getTaskTempDir(taskId: String): File`.
- Adds behavior: illegal task IDs throw `IllegalArgumentException` before any directory is created.

- [x] **Step 1: Write failing path tests**

```kotlin
@Test
fun `getTaskTempDir 合法任务目录始终位于 temp 根目录内`() {
    val baseDir = Files.createTempDirectory("download-store-path-test").toFile()
    val taskDir = DownloadStore(baseDir).getTaskTempDir("download-com.example.app")
    assertTrue(taskDir.canonicalFile.toPath().startsWith(File(baseDir, "temp").canonicalFile.toPath()))
}

@Test(expected = IllegalArgumentException::class)
fun `getTaskTempDir 拒绝路径穿越任务标识`() {
    DownloadStore(Files.createTempDirectory("download-store-traversal-test").toFile()).getTaskTempDir("download-safe/../../escape")
}

@Test(expected = IllegalArgumentException::class)
fun `getTaskTempDir 拒绝反斜杠任务标识`() {
    DownloadStore(Files.createTempDirectory("download-store-backslash-test").toFile()).getTaskTempDir("download-safe\\..\\escape")
}

@Test(expected = IllegalArgumentException::class)
fun `getTaskTempDir 拒绝超长任务标识`() {
    DownloadStore(Files.createTempDirectory("download-store-long-id-test").toFile()).getTaskTempDir("a".repeat(161))
}
```

- [x] **Step 2: Run DownloadStore tests and verify RED**

Run:

```powershell
$env:JAVA_HOME="C:\Users\XZQ\.jdks\jbr-17.0.14"
.\gradlew.bat :core:testDebugUnitTest --tests "*DownloadStoreTest" --no-daemon
```

Expected: traversal and backslash tests do not throw before the fix.

- [x] **Step 3: Implement task ID and canonical containment checks**

```kotlin
private val taskIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")

fun getTaskTempDir(taskId: String): File {
    require(taskIdPattern.matches(taskId)) { "非法下载任务标识: $taskId" }
    val tempRoot = File(baseDir, "temp").canonicalFile
    val taskDir = File(tempRoot, taskId).canonicalFile
    require(taskDir.toPath().startsWith(tempRoot.toPath())) { "下载任务目录越界: $taskId" }
    if (!taskDir.exists()) {
        taskDir.mkdirs()
    }
    return taskDir
}
```

Keep the user's existing constructor and 160-column formatting changes.

- [x] **Step 4: Run DownloadStore tests and verify GREEN**

Run the Step 2 command. Expected: all `DownloadStoreTest` tests pass.

---

### Task 3: Add pure APK identity validation and Android metadata reading

**Files:**
- Create: `core/src/main/java/com/xzq/appstore/core/installer/ApkVerifier.kt`
- Create: `core/src/main/java/com/xzq/appstore/core/installer/AndroidPackageIdentityVerifier.kt`
- Create: `core/src/test/java/com/xzq/appstore/core/installer/ApkIdentityValidatorTest.kt`

**Interfaces:**
- Produces: `ApkIdentity`, `ExpectedApkIdentity`, `ApkVerificationPolicy`, `ApkVerificationResult`, `ApkVerifier`, and `InstalledPackageInspector`.
- Produces: `AndroidPackageIdentityVerifier(context: Context)` implementing both Android-facing interfaces.

- [x] **Step 1: Write failing pure validator tests**

Cover exact package matching, strict `versionCode`, LOCAL_SIM version-name fallback, strict missing signer, signer mismatch, signer intersection, and digest case normalization:

```kotlin
@Test
fun `validate 包名不一致时拒绝`() {
    val result = ApkIdentityValidator.validate(actual(packageName = "com.attacker.app"), expected(), strictPolicy)
    assertRejected(InstallFailureCode.APK_PACKAGE_MISMATCH, result)
}

@Test
fun `validate 严格策略缺少版本代码时拒绝`() {
    val result = ApkIdentityValidator.validate(actual(), expected(versionCode = 0L), strictPolicy)
    assertRejected(InstallFailureCode.APK_VERSION_MISSING, result)
}

@Test
fun `validate 本地策略缺少版本代码时比较版本名`() {
    assertTrue(ApkIdentityValidator.validate(actual(versionName = "1.2.3"), expected(versionCode = 0L, versionName = "1.2.3"), localPolicy) is ApkVerificationResult.Verified)
}

@Test
fun `validate 签名白名单与实际签名有交集时通过`() {
    val result = ApkIdentityValidator.validate(actual(signers = setOf(OTHER_SIGNER, TRUSTED_SIGNER.uppercase())), expected(signers = setOf(TRUSTED_SIGNER)), strictPolicy)
    assertTrue(result is ApkVerificationResult.Verified)
}
```

- [x] **Step 2: Run validator tests and verify RED**

Run:

```powershell
$env:JAVA_HOME="C:\Users\XZQ\.jdks\jbr-17.0.14"
.\gradlew.bat :core:testDebugUnitTest --tests "*ApkIdentityValidatorTest" --no-daemon
```

Expected: test compilation fails because the validator types do not exist.

- [x] **Step 3: Implement pure validation**

The validator must compare package first, then version, then signer requirements and return the first specific rejection. If `expected.versionCode > 0`, compare only `versionCode`; otherwise strict policy returns `APK_VERSION_MISSING` and local policy compares `versionName`. Normalize both signer sets with `trim().lowercase()` before testing intersection.

- [x] **Step 4: Implement Android PackageManager reading**

Use `GET_SIGNING_CERTIFICATES` plus `SigningInfo` on API 28+, and `GET_SIGNATURES` on API 26–27. Compute SHA-256 from `Signature.toByteArray()`. Return `null` for unreadable archive or missing installed package; `ApkVerifier.verify()` converts an unreadable archive to `APK_INVALID`.

- [x] **Step 5: Run validator and existing core tests**

Run:

```powershell
$env:JAVA_HOME="C:\Users\XZQ\.jdks\jbr-17.0.14"
.\gradlew.bat :core:testDebugUnitTest --no-daemon
```

Expected: all core unit tests pass.

---

### Task 4: Enforce verification before and after PackageInstaller

**Files:**
- Modify: `core/src/main/java/com/xzq/appstore/core/installer/PackageInstaller.kt`
- Modify: `core/src/main/java/com/xzq/appstore/core/installer/InstallerText.kt`
- Modify: `core/src/main/java/com/xzq/appstore/core/installer/InstallSessionStatus.kt`
- Modify: `core/src/main/java/com/xzq/appstore/core/installer/RealPackageInstaller.kt`
- Test: `core/src/test/java/com/xzq/appstore/core/installer/RealPackageInstallerTest.kt`

**Interfaces:**
- Extends: `InstallRequest` with `targetVersionCode: Long = 0L` and `signerCertificateSha256: Set<String> = emptySet()` after `apkFile`.
- Extends: `InstallFailureCode` with the eight identity failure codes from the design.

- [x] **Step 1: Write failing installer tests**

Add fake verifier and installed-package inspector. Verify a rejected APK never calls `createSession`; verify callback package mismatch, missing installed package, installed version mismatch, and success using PackageManager version.

```kotlin
@Test
fun `install APK 身份校验失败时不创建系统会话`() = runBlocking {
    val adapter = RecordingSessionAdapter()
    val installer = createInstaller(adapter = adapter, verificationResult = ApkVerificationResult.Rejected(InstallFailureCode.APK_SIGNER_MISMATCH))
    val events = installAndCollect(installer)
    assertEquals(InstallFailureCode.APK_SIGNER_MISMATCH, (events.single() as InstallEvent.Failed).code)
    assertEquals(0, adapter.createSessionCalls)
}

@Test
fun `install 成功事件使用系统实际版本`() = runBlocking {
    val installer = createInstaller(installedIdentity = verifiedIdentity(versionName = "1.0.1"))
    val events = installAndCollect(installer)
    assertEquals("1.0.1", (events.last() as InstallEvent.Success).installedVersion)
}
```

- [x] **Step 2: Run installer tests and verify RED**

Run:

```powershell
$env:JAVA_HOME="C:\Users\XZQ\.jdks\jbr-17.0.14"
.\gradlew.bat :core:testDebugUnitTest --tests "*RealPackageInstallerTest" --no-daemon
```

Expected: compilation failures for the new constructor dependencies and failure codes.

- [x] **Step 3: Implement preflight and post-install verification**

Inject `ApkVerifier`, `InstalledPackageInspector`, and `ApkVerificationPolicy` into `RealPackageInstaller`. Build `ExpectedApkIdentity` from the request, return immediately on rejection, and retain the verified archive identity for the post-install comparison.

After a successful platform callback:

```kotlin
if (!commit.installedPackageName.isNullOrBlank() && commit.installedPackageName != request.packageName) {
    return@withContext failInstalledVerification(sessionId, InstallFailureCode.INSTALLED_PACKAGE_MISMATCH, onEvent)
}
val installed = installedPackageInspector.getInstalledIdentity(request.packageName)
    ?: return@withContext failInstalledVerification(sessionId, InstallFailureCode.INSTALLED_PACKAGE_NOT_FOUND, onEvent)
if (installed.packageName != request.packageName) {
    return@withContext failInstalledVerification(sessionId, InstallFailureCode.INSTALLED_PACKAGE_MISMATCH, onEvent)
}
if (installed.versionCode != verified.identity.versionCode) {
    return@withContext failInstalledVerification(sessionId, InstallFailureCode.INSTALLED_VERSION_MISMATCH, onEvent)
}
```

Persist post-install failures with `InstallSessionStatus.FAILED_VERIFY_INSTALLED`. Only then persist `CALLBACK_SUCCESS` and emit the installed PackageManager version.

- [x] **Step 4: Run installer tests and verify GREEN**

Run the Step 2 command. Expected: all `RealPackageInstallerTest` tests pass.

---

### Task 5: Wire business metadata, strict policy, and local-sim version codes

**Files:**
- Modify: `business/src/main/java/com/xzq/appstore/domain/install/DefaultInstallManager.kt`
- Modify: `business/src/test/java/com/xzq/appstore/domain/install/DefaultInstallManagerTest.kt`
- Modify: `app/src/main/java/com/xzq/appstore/app/AppContainer.kt`
- Modify: `tools/local-sim/generate_catalog.py`
- Create: `tools/local-sim/test_generate_catalog.py`
- Modify: `data/src/main/res/raw/app_store_catalog.json`

**Interfaces:**
- `DefaultInstallManager` transfers `AppDetail.versionCode` and normalized signer values into `InstallRequest`.
- `AppContainer` uses strict verification except for Debug+LOCAL_SIM.

- [x] **Step 1: Write failing business and local-sim tests**

Add a manager assertion:

```kotlin
assertEquals(100L, installer.capturedRequest?.targetVersionCode)
assertEquals(setOf(TEST_SIGNER), installer.capturedRequest?.signerCertificateSha256)
```

Add a validation-failure scenario and assert all APK trust failures clear the downloaded APK reference. Add a Python unittest that creates `com.example.app_42.apk`, invokes `app_from_apk`, and asserts `versionCode == 42`.

- [x] **Step 2: Run tests and verify RED**

Run:

```powershell
$env:JAVA_HOME="C:\Users\XZQ\.jdks\jbr-17.0.14"
.\gradlew.bat :business:testDebugUnitTest --tests "*DefaultInstallManagerTest" --no-daemon
python tools/local-sim/test_generate_catalog.py
```

Expected: missing request fields or assertions fail, and generated catalog has no `versionCode`.

- [x] **Step 3: Wire request metadata and failure cleanup**

Pass named `targetVersionCode` and `signerCertificateSha256` values into `InstallRequest`. Extract an `invalidatesDownloadedApk()` helper covering `APK_MISSING`, `APK_INVALID`, `APK_PACKAGE_MISMATCH`, `APK_VERSION_MISSING`, `APK_VERSION_MISMATCH`, `APK_SIGNER_MISSING`, and `APK_SIGNER_MISMATCH`.

- [x] **Step 4: Wire AppContainer verification policy**

Create one lazy `AndroidPackageIdentityVerifier` and pass it as both verifier and installed inspector. Compute:

```kotlin
val allowMissingTrustMetadata = BuildConfig.DEBUG && downloadEnvConfig.environment == DownloadEnvironment.LOCAL_SIM
ApkVerificationPolicy(requireVersionCode = !allowMissingTrustMetadata, requireSignerCertificate = !allowMissingTrustMetadata)
```

- [x] **Step 5: Update local-sim metadata**

Emit `"versionCode": int(version_code)` in `generate_catalog.py`, add the Python regression test, and add the known version codes from the APK filenames to the tracked resource catalog. Do not add APK binaries or signer secrets.

- [x] **Step 6: Run business, parser, core, and Python tests**

Run the Step 2 commands plus `:data:testDebugUnitTest --tests "*AppCatalogJsonParserTest"` and `:core:testDebugUnitTest`. Expected: all pass.

---

### Task 6: Full verification and handoff

**Files:**
- Modify if behavior changed: `README.md`
- Modify if behavior changed: `docs/21-当前项目状态与接手指南.md`
- Modify: `docs/superpowers/specs/2026-07-11-install-trust-and-download-path-security-design.md`
- Modify: `docs/superpowers/plans/2026-07-11-install-trust-and-download-path-security.md`

- [x] **Step 1: Run the full project verification**

Run:

```powershell
$env:JAVA_HOME="C:\Users\XZQ\.jdks\jbr-17.0.14"
.\gradlew.bat testDebugUnitTest lintDebug :app:assembleDebug :app:assembleRelease --no-daemon
```

Expected: `BUILD SUCCESSFUL` with zero test and lint failures.

- [x] **Step 2: Run the Python local-sim regression test**

Run:

```powershell
python tools/local-sim/test_generate_catalog.py
```

Expected: one passing unittest.

- [x] **Step 3: Inspect release manifest and repository diff**

Run:

```powershell
rg -n "usesCleartextTraffic|networkSecurityConfig" app/build/intermediates/merged_manifests/release
git diff --check
git status --short --branch
git diff --stat
```

Expected: no release cleartext match, no whitespace errors, and only intended source/test/doc changes plus the three pre-existing formatting changes.

- [x] **Step 4: Update status documentation accurately**

Record the exact verification date and commands, remove stale “uncommitted baseline” wording, and distinguish completed local security hardening from still-pending backend signer metadata and real-device validation.

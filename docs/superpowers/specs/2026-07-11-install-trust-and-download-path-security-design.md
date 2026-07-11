# 安装信任链与下载路径安全设计

## 目标

第一阶段只关闭两个生产阻断问题：

1. APK 在进入 `PackageInstaller.Session` 前必须验证包名、版本和签名证书；安装成功后必须用 PackageManager 的实际结果收口状态。
2. 远端目录中的 `appId`、`packageName` 和派生下载任务 ID 不得造成路径穿越、文件名碰撞或越界写入。

本阶段不处理 Release 默认环境、下载元数据并发、安装进程死亡恢复、CI 和真机矩阵；这些保持为后续独立工作。

## 设计原则

- 远端目录、缓存目录和 APK 都是不可信输入。
- PackageManager 是包身份和最终安装事实的权威来源。
- Release、DEV、TEST 默认失败关闭；只有 `BuildConfig.DEBUG + LOCAL_SIM` 可以显式放宽缺失的签名摘要和 `versionCode`。
- 包名始终严格校验；任何环境都不能放宽包名匹配。
- 安全校验失败时不得创建安装 Session，不得静默替换非法标识符，也不得继续使用可疑 APK。
- 沿用现有 `app -> business -> data/core` 分层和 `AppContainer` 手动装配，不引入新依赖。

## 目录契约

目录项增加以下字段：

- `versionCode: Long`：与 APK manifest 中的版本代码一致；值大于 `0` 时必须精确匹配。
- `signerCertificateSha256: List<String>`：允许的 APK 签名证书 SHA-256 摘要，使用 64 位小写或大写十六进制字符串；列表支持证书轮换。

目录解析规则：

- `appId` 必须匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`。
- `packageName` 必须是至少包含一个点的 Android 风格包名，每段以字母开头，后续只允许字母、数字和下划线。
- `versionName` 必须非空且不超过 128 个字符。
- `versionCode` 不得小于 `0`；`0` 表示旧目录未提供。
- 每个签名摘要必须是 64 位十六进制字符串，规范化为小写并去重。
- 同一目录内 `appId` 和 `packageName` 都必须唯一。
- 任意条目非法时，整个目录解析失败，由既有 `HTTP -> 缓存 -> 资源` 链路回退，避免只过滤坏条目后产生不完整或冲突目录。

## 安装信任链

### 核心类型

`core.installer` 新增以下边界：

- `ApkIdentity`：实际包名、`versionCode`、`versionName`、签名证书 SHA-256 集合。
- `ExpectedApkIdentity`：目录声明的预期包名、版本和允许签名集合。
- `ApkVerificationPolicy`：是否强制要求 `versionCode` 和签名摘要。
- `ApkVerifier`：读取并校验 APK，返回成功身份或结构化拒绝结果。
- `InstalledPackageInspector`：安装成功回调后查询系统中实际安装的包身份。
- `ApkIdentityValidator`：不依赖 Android 框架的纯校验器，负责包名、版本和签名集合比较。
- `AndroidPackageIdentityVerifier`：使用 PackageManager 读取 APK archive 和已安装包信息，并复用纯校验器。

Android 兼容策略：

- API 28 及以上使用 `GET_SIGNING_CERTIFICATES`、`SigningInfo` 和 `longVersionCode`。
- API 26–27 使用已废弃但仍可用的 `GET_SIGNATURES` 和 `versionCode`，在局部使用 `@Suppress("DEPRECATION")`。
- 证书摘要基于 `Signature.toByteArray()` 的 SHA-256，统一转为小写十六进制。
- 单签名包读取签名历史以兼容 Android APK Signature Scheme 的合法密钥轮换；多签名包读取当前内容签名集合。

### 安装流程

1. `DefaultInstallManager` 从 `AppDetail` 取得预期包名、`versionName`、`versionCode` 和签名摘要，构造 `InstallRequest`。
2. `RealPackageInstaller` 先检查文件存在且非空，再调用 `ApkVerifier`。
3. 校验器按顺序验证 APK 可解析、包名、版本代码或回退版本名、签名要求和签名交集。
4. 校验失败时发送明确的 `InstallEvent.Failed`，不检查安装权限、不创建 Session。
5. 校验成功后才执行权限检查、Session 创建、写入和提交。
6. 平台成功回调中的包名若存在，必须与预期包名一致。
7. 成功回调后通过 `InstalledPackageInspector` 查询 PackageManager；查询不到、包名不一致或版本与已验证 APK 不一致时，安装任务以事实校验失败收口。
8. 只有系统事实确认成功后，才发送 `InstallEvent.Success`，其中版本号来自 PackageManager，而不是目录或请求参数。

### 环境策略

`AppContainer` 根据当前构建和下载环境装配策略：

- `BuildConfig.DEBUG && environment == LOCAL_SIM`：允许目录缺少 `versionCode` 和签名摘要；缺少 `versionCode` 时精确比较 `versionName`，包名仍严格匹配。
- 其他所有组合：必须提供正数 `versionCode` 和至少一个签名摘要，否则安装前失败。

该放宽只影响元数据缺失，不允许已经提供但不匹配的版本或签名通过。

## 下载路径安全

目录入口和文件系统边界同时防护：

1. `AppCatalogJsonParser` 在生成领域模型前验证 `appId` 和 `packageName`，并拒绝重复标识。
2. `DefaultDownloadManager` 继续使用 `download-$appId` 作为可诊断任务 ID；由于 `appId` 已受白名单约束，不需要静默哈希或替换。
3. `DownloadStore.getTaskTempDir()` 再次验证 `taskId` 匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,159}`。
4. `DownloadStore` 使用 canonical file/path 解析 `temp` 根目录和任务目录，并确认任务目录仍位于 `temp` 根目录内后才创建。
5. 非法任务 ID 抛出 `IllegalArgumentException`，错误信息只包含被拒绝的任务 ID，不暴露其他本地路径。

双层防护确保即使本地持久化数据损坏或未来调用方绕过目录解析，也不能越界创建分片或 `meta.json`。

## 错误模型

`InstallFailureCode` 增加可区分的失败码：

- `APK_PACKAGE_MISMATCH`
- `APK_VERSION_MISSING`
- `APK_VERSION_MISMATCH`
- `APK_SIGNER_MISSING`
- `APK_SIGNER_MISMATCH`
- `INSTALLED_PACKAGE_MISMATCH`
- `INSTALLED_PACKAGE_NOT_FOUND`
- `INSTALLED_VERSION_MISMATCH`

`DefaultInstallManager` 将 APK 解析、包名、版本和签名失败都视为不可复用下载产物：清理 APK 引用并把下载状态置为失败；安装后系统事实不一致不删除 APK，保留证据和安装会话记录供诊断。

日志和打点只记录失败码、`appId` 和包名，不记录签名白名单、认证头或本地绝对路径。

## 测试设计

所有生产行为遵循测试先行：

- `ApkIdentityValidatorTest`：覆盖包名不匹配、缺失/不匹配版本、LOCAL_SIM 版本名回退、缺失/不匹配签名、签名轮换交集和大小写规范化。
- `RealPackageInstallerTest`：覆盖验证失败不创建 Session、平台回调包名不匹配、PackageManager 查询不到、安装版本不一致，以及成功事件使用系统实际版本。
- `AppCatalogJsonParserTest`：覆盖 `../` appId、非法包名、重复 appId、重复 packageName、非法签名摘要和合法新字段解析。
- `DownloadStoreTest`：覆盖 `../`、斜杠、反斜杠、超长任务 ID 被拒绝，以及合法任务目录 canonical path 位于根目录内。
- `DefaultInstallManagerTest`：验证新字段正确进入 `InstallRequest`，APK 信任失败会清理下载产物。

相关测试通过后运行：

```powershell
./gradlew.bat testDebugUnitTest lintDebug :app:assembleDebug :app:assembleRelease --no-daemon
```

最后检查 `git diff --check`，并确认现有三个格式改动没有被回退或混入无关重构。

## 完成标准

- 非法目录标识无法到达下载文件系统。
- 非法任务 ID 无法在下载根目录内外创建任何任务目录。
- 未验证 APK 无法创建 PackageInstaller Session。
- 严格环境缺少版本代码或签名摘要时失败关闭。
- LOCAL_SIM 只放宽缺失元数据，不放宽实际不匹配。
- 安装成功状态来自 PackageManager 的实际包名和版本。
- 相关单测、全量单测、Lint、Debug 和 Release 构建全部通过。

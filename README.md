<p align="center">
  <h1 align="center">CarAppStore</h1>
  <p align="center">
    <b>跨平台应用分发与管理 App · Android 客户端</b><br/>
    面向手机、平板、桌面级大屏和可选车载形态复用应用目录、下载、安装、升级、策略与任务中心
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform"/>
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen.svg" alt="API"/>
  <img src="https://img.shields.io/badge/Kotlin-Coroutines-blue.svg" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Architecture-MVVM-orange.svg" alt="Architecture"/>
  <img src="https://img.shields.io/badge/DI-AppContainer-lightgrey.svg" alt="DI"/>
</p>

## 简介

CarAppStore 是一个跨平台应用分发与管理 App。当前仓库承载它的 Android/Kotlin 客户端实现，核心链路包括应用目录、首页推荐、搜索、详情、我的应用、下载、安装、升级、状态归约、策略门控、应用管理和任务中心。

项目已经完成多终端 UI 壳层和主要页面开发，不再只是静态 Demo。当前工程可生成 Debug/Release APK，页面可跑通目录、状态中心、策略门控、任务中心和下载/安装/升级主链路；真实联网目录、APK 下载源和生产环境验收仍是下一步接入重点。

产品定位与代码边界以 [docs/00-产品定位与平台边界.md](docs/00-产品定位与平台边界.md) 为准：Android 是当前实现平台，车载 OEM 能力是可选适配器，不是产品定义或通用版本的发布前提。

## 当前同步基线

- 当前主分支：`main`
- 已同步远端：`origin/main`
- 当前工作区验证：2026-07-16 已在 JDK 17 下通过 `testDebugUnitTest`、`compileDebugKotlin`、`lintDebug`、`:app:assembleDebug` 与 `:app:assembleRelease`；当前包含未提交的 Release 环境隔离、跨实例存储锁、安装会话对账和 CI 改动，提交基线请以 Git 历史为准。
- 换机接手总览：[docs/29-换机接手与当前进度总览.md](docs/29-换机接手与当前进度总览.md)

实际最新状态请以 `git status --short --branch` 和 `git log --oneline -5` 为准。

## 当前效果

| 多终端 UI | 首页与应用卡片 |
| --- | --- |
| <img src="docs/设计/01_多设备应用商店界面展示_3x高清.jpg" alt="多终端应用商店 UI" width="420"/> | <img src="docs/设计/05_抖音应用商店展示界面合集_3x高清.jpg" alt="应用商店首页与卡片" width="420"/> |

| 下载管理 | 升级管理 |
| --- | --- |
| <img src="docs/设计/06_下载管理界面设计合集_3x高清.jpg" alt="下载管理界面" width="420"/> | <img src="docs/设计/07_应用更新界面设计展示_3x高清.jpg" alt="应用升级界面" width="420"/> |

完整设计参考见 [docs/设计](docs/设计)，多终端布局说明见 [docs/27-多端UI框架设计.md](docs/27-多端UI框架设计.md)。

## 架构图

| 图 | 说明 |
| --- | --- |
| [项目模块架构图](docs/architecture-project-modules.svg) | `app / feature-* / business / data / core / common` 分层与依赖方向 |
| [七个业务模块关系图](docs/architecture-business-modules.svg) | 下载、安装、升级、应用管理、状态中心、策略中心、Repository 的关系 |
| [主链路流程图](docs/architecture-main-flow.svg) | 从页面事件到策略判断、状态更新、Repository 和 core 执行能力的闭环 |

PNG 版本位于同名 `.png` 文件，见 [架构图索引](docs/architecture-diagrams.md)。

## 核心设计

- **MVVM + 单向数据流**：`View -> Event -> ViewModel -> State -> View`
- **手动依赖装配**：使用 `AppContainer`，不引入 Hilt/Dagger
- **固定页面导航**：使用 `Activity + FragmentManager`，不引入 Navigation
- **业务边界清晰**：下载、安装、升级、应用管理、状态中心、策略中心、Repository 各自收口职责
- **安全与可恢复性优先**：策略门控、任务恢复、失败可见性和系统边界治理优先于运营功能
- **跨平台核心、平台能力适配**：业务边界和状态语义保持通用，Android 系统能力与可选 OEM 能力收口在桥接层
- **多终端 UI 适配**：手机、平板和桌面级大屏/横屏窗口共享同一组页面和业务状态，车载横屏按需复用扩展布局

## 模块一览

| 模块 | 职责 |
| --- | --- |
| `app` | Application、MainActivity、Fragment 导航、AppContainer 依赖装配和系统桥接 |
| `common` | 基类、共享 UI、导航接口、通用结果类型和共享资源 |
| `core` | 真实文件下载器、系统安装会话、JSON 存储、日志与打点基础能力 |
| `data` | Repository、远端目录、本地结构化存储、系统数据源和模型映射 |
| `business` | 七个业务域的流程编排、策略拦截、状态归约和页面聚合 |
| `feature-*` | 首页、详情、搜索、我的应用、下载中心、安装中心、升级中心、开发者设置 |

## 已具备能力

- HTTP 下载、Range 断点续传、分片下载、合并校验、暂停/取消和失败恢复
- `PackageInstaller.Session` 安装链路、用户确认页分发、安装会话持久化
- `VersionedJsonStore` 统一 JSON 存储，支持 schema 版本、迁移、并发写锁和原子写
- 远端目录 `HTTP -> 本地缓存 -> 资源目录` 回退链路，支持 ETag / Last-Modified 条件请求
- `DefaultStateCenter` 统一维护下载、安装、升级运行态，并推导页面主按钮和状态文案
- `DefaultPolicyCenter` 统一处理 Wi-Fi、存储和按平台启用的附加策略；驻车/车况属于可选车载策略
- 首页、搜索、详情、我的应用、下载中心、安装中心、升级中心和开发者设置页面
- 手机、平板、桌面级大屏/横屏响应式壳层，扩展窗口提供侧边导航和任务摘要栏
- 应用图标、详情头图和详情截图加载能力，支持 `asset://`、`file://`、`http(s)://`，失败时回退文本兜底
- 本地事件打点落盘、生产配置自检提示、Release 签名环境变量入口
- 目录运营治理字段：灰度、黑白名单、隐藏/下架和回滚版本本地过滤
- APK 安装前包名、`versionCode`、签名证书 SHA-256 校验，以及安装后 PackageManager 事实核对
- 目录 `appId` / `packageName` 白名单、重复项拒绝和下载任务 canonical path containment
- Release 固定生产环境并使用安全空目录回退，LOCAL_SIM 目录与 Mock 能力仅在 Debug 生效
- `VersionedJsonStore` 同文件跨实例共享锁，冷启动会对账 PackageInstaller Session 与已安装事实
- GitHub Actions 已定义 JDK 17 单测、编译、Lint、Debug/Release APK 验证和产物归档

## 外部接入状态

仓库内已经预留生产接入点。通用 Android 发布基线和可选平台专项必须分开验收：

| 接入项 | 当前状态 |
| --- | --- |
| 远端目录 API | 客户端链路、缓存回退、鉴权头和 Gradle/环境变量注入已完成；生产目录还必须提供 APK `versionCode` 和 `signerCertificateSha256` |
| APK 联网下载源 | 下载器、任务状态、断点续传、checksum 和安装前身份校验链路已具备；真实 APK CDN、签名摘要和灰度策略下一步接入 |
| Android 设备安装行为 | 已接 Android `PackageInstaller`，并在创建会话前检查“允许安装未知应用”权限；不同 Android 版本和 ROM 的确认页、回调码及权限行为仍需设备矩阵验证 |
| 通用平台策略解耦 | 当前实现仍保留 `parkingMode` 的车载默认语义；非车载发行前需要增加平台能力开关，未启用车载适配时不得因缺少 OEM 信号阻断安装/升级 |
| 可选车载平台适配 | 已定义 `VehicleStateSignalProvider` 并支持广播型 OEM 接入；只有发行目标包含车载设备时才需要接真实协议并执行驻车/行车专项验收 |
| 运营观测 | 本地事件落盘已接入；上传服务、告警看板和隐私合规字段需要接生产平台 |

## 环境要求

- Android Studio 或命令行 Android Gradle 环境
- JDK/JBR 17
- Android Gradle Plugin 8.13.2
- Kotlin 2.0.0
- compileSdk 34，minSdk 26

## 构建与测试

Windows PowerShell:

```powershell
$env:JAVA_HOME="<your-jdk-17-path>"
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat compileDebugKotlin --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat lintDebug --no-daemon
.\gradlew.bat :app:assembleRelease --no-daemon
```

macOS / Linux / Git Bash:

```bash
./gradlew testDebugUnitTest --no-daemon
./gradlew compileDebugKotlin --no-daemon
./gradlew :app:assembleDebug --no-daemon
./gradlew lintDebug --no-daemon
./gradlew :app:assembleRelease --no-daemon
```

## 项目结构

```text
CarAppStore/
├── app/                        # 壳层：AppContainer、MainActivity
├── common/                     # 基类、通用 UI、共享资源
├── core/                       # 下载器、安装器、存储等基础能力
├── data/                       # Repository、DataSource、本地存储和模型
├── business/                   # 下载/安装/升级/应用管理/状态/策略等业务层
├── feature-home/               # 首页
├── feature-detail/             # 详情页
├── feature-myapp/              # 我的应用
├── feature-search/             # 搜索页
├── feature-downloadmanager/    # 下载中心
├── feature-installcenter/      # 安装中心
├── feature-upgrade/            # 升级中心
├── feature-debug/              # 开发者设置
├── docs/                       # 架构、流程、联调和验收文档
├── AGENTS.md                   # Codex 说明
├── CLAUDE.md                   # 项目编码规范
├── build.gradle.kts            # 根构建文件
└── settings.gradle.kts         # 模块声明
```

## 推荐阅读

| 文档 | 说明 |
| --- | --- |
| [产品定位与平台边界](docs/00-产品定位与平台边界.md) | 跨平台产品定义、当前 Android 实现和可选车载适配边界 |
| [换机接手与当前进度总览](docs/29-换机接手与当前进度总览.md) | 换电脑、重新 clone、交给新 Agent 时先确认的同步基线 |
| [当前项目状态与接手指南](docs/21-当前项目状态与接手指南.md) | 当前阶段、测试覆盖、风险和接手顺序 |
| [架构总览](docs/01-架构总览.md) | 整体分层与依赖方向 |
| [七个业务模块详解](docs/03-七个业务模块详解.md) | 业务模块边界和职责 |
| [整体业务主链路总流程](docs/16-整体业务主链路总流程.md) | 下载、安装、升级主链路流程 |
| [远端目录与可选平台信号接入约定](docs/23-远端目录与车况信号接入约定.md) | 后端和可选 OEM 适配约定 |
| [Android 设备矩阵回归测试清单](docs/25-真机回归测试清单.md) | 手机、平板、桌面级大屏和可选车载设备验证清单 |
| [后端与平台适配器验收标准](docs/26-后端与OEM验收标准.md) | 通用对接与可选 OEM 专项验收标准 |
| [跨平台 UI 与多终端框架设计](docs/27-多端UI框架设计.md) | 手机、平板、桌面级大屏和可选车载形态的 UI 适配顺序 |
| [商用化剩余事项](docs/28-商用化剩余事项.md) | 已补齐能力、外部配置入口和下一步验收顺序 |

## 发布说明

本仓库已经具备跨平台应用分发产品的 Android 主体 UI、工程分层、下载/安装/升级主链路、本地事件源、APK 身份校验、Release 环境隔离、安装会话冷启动对账和 CI 工作流。下一步通用主线是接入携带 `versionCode` 与签名摘要的真实目录和 APK 下载源、完成平台能力开关、生产签名、埋点上传及 Android 设备矩阵回归；OEM 车况和车载 ROM 验收只在车载发行目标中执行。

<p align="center">
  <h1 align="center">CarAppStore</h1>
  <p align="center">
    <b>车载应用商店 Android 工程</b><br/>
    面向车机场景的下载、安装、升级、策略和任务中心主链路样例工程
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

CarAppStore 是一个多模块 Android/Kotlin 工程，用来沉淀车载应用商店的核心链路：应用目录、下载、安装、升级、状态归约、策略门控、应用管理和任务中心。

项目重点不是营销页面或静态 Demo，而是把下载/安装/升级这些容易受网络、存储、系统安装器和车况策略影响的流程拆成清晰的工程边界，并提供可测试、可替换、可继续联调的实现。

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
- **车机安全优先**：策略门控、任务恢复、失败可见性和 OEM 接缝优先于运营功能

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
- `DefaultPolicyCenter` 统一处理 Wi-Fi、存储、驻车/车况等策略门控
- 首页、搜索、详情、我的应用、下载中心、安装中心、升级中心和开发者设置页面

## 外部接入状态

仓库内已经预留真实接入点，但发布到实际车机前仍需要替换外部资源：

| 接入项 | 当前状态 |
| --- | --- |
| 远端目录 API | 客户端链路已完成；默认配置仍使用示例地址，需要替换为真实后端地址、鉴权头和协议 |
| OEM 车况信号 | 已定义 `VehicleStateSignalProvider` 接口；未接 OEM SDK 时按安全默认值处理 |
| 真机安装行为 | 已接 Android `PackageInstaller`；不同车机 ROM 的确认页、回调码和权限行为需要实机验证 |

## 环境要求

- Android Studio 或命令行 Android Gradle 环境
- JDK/JBR 17
- Android Gradle Plugin 8.4.2
- Kotlin 1.9.24
- compileSdk 34，minSdk 26

## 构建与测试

Windows PowerShell:

```powershell
$env:JAVA_HOME="<your-jdk-17-path>"
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat compileDebugKotlin --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

macOS / Linux / Git Bash:

```bash
./gradlew testDebugUnitTest --no-daemon
./gradlew compileDebugKotlin --no-daemon
./gradlew :app:assembleDebug --no-daemon
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
| [当前项目状态与接手指南](docs/21-当前项目状态与接手指南.md) | 当前阶段、测试覆盖、风险和接手顺序 |
| [架构总览](docs/01-架构总览.md) | 整体分层与依赖方向 |
| [七个业务模块详解](docs/03-七个业务模块详解.md) | 业务模块边界和职责 |
| [整体业务主链路总流程](docs/16-整体业务主链路总流程.md) | 下载、安装、升级主链路流程 |
| [远端目录与车况信号接入约定](docs/23-远端目录与车况信号接入约定.md) | 后端和 OEM 接入约定 |
| [真机回归测试清单](docs/25-真机回归测试清单.md) | 设备验证清单 |
| [后端与 OEM 验收标准](docs/26-后端与OEM验收标准.md) | 对接验收标准 |

## 发布说明

本仓库适合作为车载应用商店主链路的工程样例和联调底座。默认后端地址、下载源和 OEM 车况 provider 均为示例或接缝实现；接入真实生产环境前，应替换外部配置并完成真机回归。

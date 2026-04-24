<p align="center">
  <h1 align="center">CarAppStore</h1>
  <p align="center">
    <b>车载应用商店 Android 工程</b><br/>
    R1-R6 + P1/P2 硬化 + P3 重构完成，144 单元测试全绿，进入设备联调阶段
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform"/>
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen.svg" alt="API"/>
  <img src="https://img.shields.io/badge/Kotlin-Coroutines-blue.svg" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Architecture-MVVM-orange.svg" alt="Architecture"/>
  <img src="https://img.shields.io/badge/DI-AppContainer-lightgrey.svg" alt="DI"/>
  <img src="https://img.shields.io/badge/Tests-144%20passed-success.svg" alt="Tests"/>
</p>

---

## 简介

CarAppStore 是面向车机场景的 Android 应用商店，覆盖 **下载、安装、升级、状态归约、策略门控、商店目录、任务中心** 完整链路，已具备可继续生产化演进的工程底座。

核心设计理念：

- **MVVM + 单向数据流** — `View → Event → ViewModel → State → View`
- **手动 DI** — `AppContainer` 装配，不引入 Hilt
- **模块边界清晰** — 7 个业务域独立，按需集成
- **车机优先** — 安全策略、兼容性、失败恢复、任务可见性优先于运营功能

## 核心能力

### 已完成

| # | 能力 | 说明 |
|---|------|------|
| 1 | 真实下载器 | HTTP 下载、Range 断点、分片、校验、IO 中断、去重保护 |
| 2 | 真实安装器 | `PackageInstaller.Session`、用户确认、Session 持久化 |
| 3 | 统一数据层 | JSON 持久化、schema 版本迁移、并发写锁、原子写 |
| 4 | Repository 真实化 | `PackageManager` 查询、Intent 打开应用、3 DataSource 聚合 |
| 5 | 批量升级 | `checkAllUpgrades`、串行执行、策略门控、失败恢复 |
| 6 | 商店目录 | HTTP → 条件请求缓存 → 资源目录回退链路、ETag/304 |
| 7 | 策略中心 | 实时网络/存储信号、设置流驱动、OEM 接缝可替换 |
| 8 | 页面状态机 | 首页/搜索/详情/我的应用/下载中心/安装中心/升级中心 |
| 9 | P1 基础硬化 | 目录 HTTP/缓存/回退、策略设置流、OEM 信号接缝 |
| 10 | P2 生产硬化 | 输入校验、下载基地址外化、XML 全量 @dimen/、开发者工具 |
| 11 | P3 代码重构 | 超长方法拆解（≤40行）、DownloadFileHelper 抽离、XML 嵌套扁平化、常量提取、新增 14 个单元测试 |

### 待外部联调

| 项目 | 说明 |
|------|------|
| 真实目录 API | 链路已搭好，待后端地址、鉴权、协议接入 |
| OEM 驻车/车况信号 | 接缝已预留，待 OEM SDK 对接 |
| 真实设备安装联调 | 需验证系统差异和 OEM 行为 |

## 架构

```
┌──────────────────────────────────────────────────────────────┐
│                     Presentation Layer                        │
│                                                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │  Feature 层
│  │   Home   │ │  Detail  │ │  Search  │ │  MyApp   │        │  (8 页面模块)
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ Download │ │  Install │ │ Upgrade  │ │  Debug   │        │
│  │ Center   │ │  Center  │ │  Center  │ │ Settings │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │  Business 层
│  │ Download │ │ Install  │ │ Upgrade  │ │AppManager│        │  (7 业务模块)
│  │ Manager  │ │ Manager  │ │ Manager  │ │          │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                     │
│  │  State   │ │  Policy  │ │Repository│                     │
│  │  Center  │ │  Center  │ │          │                     │
│  └──────────┘ └──────────┘ └──────────┘                     │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │  Core 层
│  │   Downloader │  │  Installer   │  │    Store     │       │  (基础能力)
│  │ HTTP/Range/  │  │ PackageInst- │  │ JSON/Schema/ │       │
│  │ Seg/Checksum │  │ aller Session│  │ Lock/Atomic  │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │  Data 层
│  │  Remote DS   │  │  Local DS    │  │  System DS   │       │  (数据源)
│  │ HTTP/Cache/  │  │ JSON Store/  │  │PackageMgr/   │       │
│  │ Fallback     │  │ Mapper       │  │ Intent       │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│                                                               │
├──────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐                          │  Shell 层
│  │  AppContainer│  │ MainActivity │                          │  (装配+桥接)
│  │   手动 DI    │  │ FragmentMgr  │                          │
│  └──────────────┘  └──────────────┘                          │
└──────────────────────────────────────────────────────────────┘
```

### 关键架构决策

- **页面结构固定** — `Activity + FragmentManager`，不引入 Navigation
- **业务不回流到壳层** — `app` 只做装配和系统桥接
- **Repository 收口数据真相** — ViewModel 不直接访问网络、数据库或系统安装能力
- **状态中心统一归约** — 按钮语义、状态文案、进度展示来自统一归约
- **策略中心统一拦截** — 下载、安装、升级共用一套策略判定入口

## 模块一览

| 模块 | 说明 |
|------|------|
| `app` | 壳层装配、导航与系统桥接 |
| `common` | Base 组件、通用 UI、共享资源 |
| `core` | 下载器、安装器、底层存储与执行能力 |
| `data` | Repository、远端目录、本地存储、系统数据源 |
| `business` | 七个核心业务域（下载/安装/升级/应用管理/状态中心/策略中心/Repository） |
| `feature-home` | 首页 |
| `feature-detail` | 详情页 |
| `feature-myapp` | 我的应用 |
| `feature-search` | 搜索页 |
| `feature-downloadmanager` | 下载中心 |
| `feature-installcenter` | 安装中心 |
| `feature-upgrade` | 升级中心 |
| `feature-debug` | 开发者设置 |

## 项目结构

```
CarAppStore/
├── app/                        # 壳层：AppContainer、MainActivity
├── common/                     # BaseFragment、通用 UI、共享文案
├── core/                       # 基础能力
│   ├── downloader/             # RealFileDownloader、DownloadStore
│   ├── installer/              # RealPackageInstaller、InstallSessionStore
│   └── storage/                # VersionedJsonStore
├── data/                       # 数据层
│   ├── datasource/remote/      # AppCatalogSource、CacheMetadataStore
│   ├── datasource/local/       # JsonBackedLocalStoreFacade、Mapper
│   └── datasource/system/      # AppSystemDataSource (PackageManager)
├── business/                   # 业务层
│   ├── download/               # DefaultDownloadManager
│   ├── install/                # DefaultInstallManager
│   ├── upgrade/                # DefaultUpgradeManager
│   ├── appmanager/             # DefaultAppManager
│   ├── state/                  # DefaultStateCenter
│   ├── policy/                 # DefaultPolicyCenter
│   └── repository/             # RealAppRepository
├── feature-home/               # 首页
├── feature-detail/             # 详情页
├── feature-myapp/              # 我的应用
├── feature-search/             # 搜索页
├── feature-downloadmanager/    # 下载中心
├── feature-installcenter/      # 安装中心
├── feature-upgrade/            # 升级中心
├── feature-debug/              # 开发者设置
├── docs/                       # 项目文档
├── CLAUDE.md                   # 编码规范
├── AGENTS.md                   # 完整开发规则
└── build.gradle                # 根构建文件
```

## 快速开始

### 环境要求

- Android Studio / 命令行 Gradle
- JBR 17、compileSdk 34、minSdk 26

### 构建与测试

```bash
./gradlew testDebugUnitTest    # 144 单元测试
./gradlew compileDebugKotlin   # 全量编译
./gradlew app:assemble         # 构建 APK
```

### 接手阅读顺序

1. `docs/21-当前项目状态与接手指南.md` — 项目进度单页入口
2. `docs/01-架构总览.md` — 整体架构与依赖方向
3. `docs/03-七个业务模块详解.md` — 业务模块边界与职责
4. `CLAUDE.md` — 编码硬规则

## 文档索引

| 文档 | 说明 |
|------|------|
| [当前状态与接手指南](docs/21-当前项目状态与接手指南.md) | 项目进度单页入口 |
| [架构总览](docs/01-架构总览.md) | 整体架构与依赖方向 |
| [七个业务模块详解](docs/03-七个业务模块详解.md) | 业务模块边界与职责 |
| [工程演进与阶段判断](docs/06-工程演进与当前阶段判断.md) | 从 UI 骨架到当前阶段的脉络 |
| [开发顺序与落地清单](docs/08-开发顺序与落地清单.md) | 推荐开发顺序与检查清单 |
| [策略中心架构](docs/13-策略中心架构与流程.md) | 策略中心与实时信号设计 |
| [远端目录接入约定](docs/23-远端目录与车况信号接入约定.md) | 后端/OEM 联调接入约定 |
| [联调执行清单](docs/24-后端与OEM联调执行清单.md) | 外部联调任务拆解与验收 |
| `CLAUDE.md` | 编码规范（精炼版） |
| `AGENTS.md` | 完整开发规则 |

## 下一步

1. 真实设备联调：下载 → 安装 → 升级全链路
2. 接入真实后端 API（地址、鉴权、协议）
3. 接入 OEM 驻车/车况实时信号
4. 落实后端/OEM 联调验收清单

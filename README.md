<p align="center">
  <h1 align="center">CarAppStore</h1>
  <p align="center">
    <b>车载应用商店 Android 工程</b><br/>
    R1-R6 + P1/P2 硬化完成，130 单元测试全绿，进入设备联调阶段
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform"/>
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen.svg" alt="API"/>
  <img src="https://img.shields.io/badge/Kotlin-Coroutines-blue.svg" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Architecture-MVVM-orange.svg" alt="Architecture"/>
  <img src="https://img.shields.io/badge/DI-AppContainer-lightgrey.svg" alt="DI"/>
  <img src="https://img.shields.io/badge/Tests-130%20passed-success.svg" alt="Tests"/>
</p>

---

## 简介

CarAppStore 是面向车机场景的 Android 应用商店，覆盖 **下载、安装、升级、状态归约、策略门控、商店目录、任务中心** 完整链路，已具备可继续生产化演进的工程底座。

### 已完成能力

| 能力 | 说明 |
|------|------|
| 真实下载器 | HTTP、Range、分片、校验、断点续传 |
| 真实安装器 | `PackageInstaller.Session`、用户确认、Session 持久化 |
| 统一数据层 | JSON 持久化、schema 迁移、并发写锁、原子写 |
| Repository 真实化 | `PackageManager` 查询、Intent 打开应用 |
| 批量升级 | `checkAllUpgrades`、串行执行、策略门控 |
| 商店目录 | HTTP → 条件请求缓存 → 资源目录回退链路，ETag/304 |
| 策略中心 | 实时网络/存储信号、设置流驱动、OEM 接缝可替换 |
| 页面状态机 | 首页/搜索/详情/我的应用/任务中心 |
| P1 基础硬化 | 目录 HTTP/缓存/回退、策略设置流、OEM 信号接缝 |
| P2 生产硬化 | 输入校验、下载基地址外化、XML 全量 @dimen/、开发者工具扩展 |

### 待外部联调

| 项目 | 说明 |
|------|------|
| 真实目录 API | 链路已搭好，待后端地址、鉴权、协议接入 |
| OEM 驻车/车况信号 | 接缝已预留，待 OEM SDK 对接 |
| 真实设备安装联调 | 需验证系统差异和 OEM 行为 |

## 架构

```text
app/              壳层：AppContainer、MainActivity、系统桥接
common/           BaseFragment、通用 UI、共享文案
core/             RealFileDownloader、RealPackageInstaller、Store 能力
data/             RealAppRepository、remote/local/system DataSource
business/         七个核心业务域：download/install/upgrade/appmanager/state/policy/repository
feature-home/     首页
feature-detail/   详情页
feature-myapp/    我的应用
feature-search/   搜索页
feature-downloadmanager/ 下载中心
feature-installcenter/   安装中心
feature-upgrade/         升级中心
feature-debug/           开发设置
```

### 关键架构决策

- **MVVM + 单向数据流**：`View → Event → ViewModel → State → View`
- **手动 DI**：`AppContainer` 装配，不引入 Hilt
- **Activity + FragmentManager**：不引入 Navigation
- **Repository 收口**：ViewModel 不直接访问网络/数据库/系统安装能力
- **七个业务边界**：下载、安装、升级、应用管理、状态中心、策略中心、Repository

## 快速开始

### 环境要求

- Android Studio / 命令行 Gradle
- JBR 17、compileSdk 34、minSdk 26

### 构建与测试

```bash
./gradlew testDebugUnitTest    # 130 单元测试
./gradlew compileDebugKotlin   # 全量编译
./gradlew app:assemble         # 构建 APK
```

### 接手阅读顺序

1. `docs/21-当前项目状态与接手指南.md` — 项目进度单页入口
2. `docs/01-架构总览.md` — 整体架构与依赖方向
3. `docs/03-七个业务模块详解.md` — 业务模块边界与职责
4. `AGENTS.md` — 编码硬规则

## 文档索引

| 文档 | 说明 |
|------|------|
| `docs/21` | 当前状态与接手指南（单页入口） |
| `docs/01` | 架构总览 |
| `docs/03` | 七个业务模块详解 |
| `docs/06` | 工程演进与当前阶段判断 |
| `docs/08` | 开发顺序与落地清单 |
| `docs/13` | 策略中心架构与流程 |
| `docs/23` | 远端目录与车况信号接入约定 |
| `docs/24` | 后端与 OEM 联调执行清单 |
| `AGENTS.md` | 编码规范与禁止事项 |

## 下一步

1. 真实设备联调：下载 → 安装 → 升级全链路
2. 接入真实后端 API（地址、鉴权、协议）
3. 接入 OEM 驻车/车况实时信号
4. 落实后端/OEM 联调验收清单

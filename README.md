<p align="center">
  <h1 align="center">CarAppStore</h1>
  <p align="center">
    <b>车载应用商店 Android 工程</b><br/>
    主链路已通，真实能力 R1-R6 全部完成，进入设备联调和生产硬化阶段
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform"/>
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen.svg" alt="API"/>
  <img src="https://img.shields.io/badge/Kotlin-Coroutines-blue.svg" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Architecture-MVVM-orange.svg" alt="Architecture"/>
  <img src="https://img.shields.io/badge/DI-AppContainer-lightgrey.svg" alt="DI"/>
  <img src="https://img.shields.io/badge/Tests-103%20passed-success.svg" alt="Tests"/>
</p>

---

## 简介

CarAppStore 是一个面向车机场景的 Android 应用商店工程，目标不是只做“应用列表 + 下载按钮”的页面壳子，而是把 **下载、安装、升级、状态归约、策略门控、商店目录、任务中心** 这些核心链路做成可继续生产化演进的工程底座。

当前代码现状：

- **主链路真实化**：下载、安装、升级、状态中心、Repository 都已打通
- **商店目录可演进**：已具备 `HTTP -> 缓存 -> 资源目录` 回退链路
- **策略实时化起步**：已接入网络/存储实时信号，OEM 驻车信号待后续对接
- **页面状态清晰**：首页、搜索、详情、我的应用、任务中心都已收口成显式状态机
- **测试基线稳定**：当前 `103` 个单元测试全部通过

核心设计原则：

- **MVVM + 单向数据流**：`View -> Event -> ViewModel -> State -> View`
- **手动 DI**：统一通过 `AppContainer` 装配，不引入 Hilt
- **模块边界清晰**：保持下载、安装、升级、应用管理、状态中心、策略中心、Repository 七个业务边界
- **车机优先**：优先处理安全策略、兼容性、失败恢复和任务可见性，而不是先堆手机商店式运营功能

## 特性

### 核心能力矩阵

| 能力 | 状态 | 说明 |
|------|------|------|
| 真实下载器 | ✅ | HTTP 下载、Range、分片、校验、断点续传 |
| 真实安装器 | ✅ | `PackageInstaller.Session`、用户确认、Session 持久化 |
| 统一数据层 | ✅ | JSON 持久化、schema 迁移、并发锁、原子写 |
| 下载执行控制 | ✅ | IO 中断、去重保护、暂停/取消 |
| Repository 真实化 | ✅ | `PackageManager` 查询、Intent 打开应用 |
| 批量升级 | ✅ | `checkAllUpgrades`、串行升级、策略门控 |
| 升级失败恢复 | ✅ | `StateReducer` 回退、失败后可重试 |
| 商店目录资源化 | ✅ | 分类、推荐理由、详情元信息、更新日志 |
| 远端目录回退链路 | ✅ | `HTTP -> 缓存 -> 资源目录` |
| 策略中心可观察化 | ✅ | 设置流驱动页面刷新 |
| 实时策略信号 | ✅ | 网络/存储监听已接入 |
| 页面状态机 | ✅ | 首页、搜索、详情、我的应用、任务中心 |

### 当前仍属于外部联调范围

| 项目 | 状态 | 说明 |
|------|------|------|
| 真实目录 API | 进行中 | 当前链路已搭好，待后端地址、鉴权、协议接入 |
| OEM 驻车/车况信号 | 进行中 | 当前仅有网络/存储实时信号，OEM 信号待对接 |
| 真实设备安装联调 | 进行中 | 需要验证系统差异和 OEM 行为 |

## 架构

```text
app/              壳层：AppContainer、MainActivity、系统桥接
common/           BaseFragment、通用 UI、共享文案
core/             RealFileDownloader、RealPackageInstaller、Store 能力
data/             RealAppRepository、remote/local/system DataSource
business/         download/install/upgrade/appmanager/state/policy
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

- **页面结构固定**：`Activity + FragmentManager`，不引入 Navigation
- **业务不回流到壳层**：`app` 只做装配和系统桥接
- **Repository 收口数据真相**：ViewModel 不直接访问网络、数据库或系统安装能力
- **状态中心统一归约**：按钮语义、状态文案、进度展示都来自统一归约结果
- **策略中心统一拦截**：下载、安装、升级共用一套策略判定入口

## 模块一览

| 模块 | 说明 |
|------|------|
| `app` | 壳层装配、导航与系统桥接 |
| `common` | Base 组件、通用 UI、共享资源 |
| `core` | 下载器、安装器、底层存储与执行能力 |
| `data` | Repository、远端目录、本地存储、系统数据源 |
| `business` | 七个核心业务域 |
| `feature-*` | 各页面模块与 ViewModel |
| `docs` | 项目状态、架构、演进与接手文档 |

## 快速开始

### 环境要求

- Android Studio / 命令行 Gradle 环境
- JBR 17
- compileSdk 34
- minSdk 26

### 构建与测试

```bash
# 全量单元测试
./gradlew testDebugUnitTest

# 全量 Kotlin 编译
./gradlew compileDebugKotlin

# 构建 APK
./gradlew app:assemble
```

当前测试基线：

- `103` tests
- `0` failures

### 本地运行建议

1. 先阅读 `docs/21-当前项目状态与接手指南.md`
2. 再看 `docs/01-架构总览.md` 和 `docs/03-七个业务模块详解.md`
3. 如需判断当前阶段，以 `docs/21` 为单页入口

## 文档

| 文档 | 说明 |
|------|------|
| `docs/21-当前项目状态与接手指南.md` | 当前阶段、已完成能力、风险、下一步 |
| `docs/01-架构总览.md` | 工程整体架构与依赖方向 |
| `docs/03-七个业务模块详解.md` | 七个业务模块边界与职责 |
| `docs/06-工程演进与当前阶段判断.md` | 从早期 UI 骨架演进到当前阶段的脉络 |
| `docs/08-开发顺序与落地清单.md` | 当前推荐开发顺序与检查清单 |
| `docs/13-策略中心架构与流程.md` | 策略中心与实时信号设计 |
| `docs/23-远端目录与车况信号接入约定.md` | 后端/OEM 联调接入约定 |
| `AGENTS.md` | 仓库内必须遵守的开发硬规则 |

## 当前进度判断

当前不是：

- 只有页面壳子
- 只有模拟下载安装
- 只有 fake 数据源

当前也还不是：

- 已接入真实目录 API 的交付版
- 已完成 OEM 车况接入的量产版
- 已完成真实设备全量联调的发布版

当前最准确的判断是：

> 主链路已通，真实能力 R1-R6 全部完成，商店目录已具备 HTTP/缓存/资源回退链路，策略中心已接入网络/存储实时信号，首页/搜索/详情/我的应用/任务中心状态机已补齐，进入设备联调和生产硬化阶段。

## 下一步

1. 真实设备联调下载 → 安装 → 升级全链路
2. 把当前目录回退链路接到真实后端 API
3. 接入 OEM 驻车/车况实时信号
4. 输出并落实后端/OEM 联调清单

## 开发规范摘要

- 严格使用 MVVM，业务逻辑不能写回 Activity / Fragment
- 禁止引入 Hilt、Navigation、`findViewById`、`GlobalScope`、`!!`
- 资源命名、字符串治理、模块边界不能倒退
- 新增注释使用中文
- 详细规则以 `AGENTS.md` 为准

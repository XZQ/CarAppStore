# CarAppStore — Claude Code Instructions

> 完整编码规范详见 `AGENTS.md`，本文件仅保留 Claude Code 高频需要的精炼摘要。

---

## 项目速览

- **类型**: 车载应用商店 Android 项目
- **架构**: MVVM + AppContainer 手动 DI + FragmentManager 导航
- **模块**: app / common / core / data / business(7个子模块) / feature-*(页面模块)
- **状态**: P2 生产加固完成，119 单元测试通过

## 接手流程

进入仓库后先读：`README.md` → `docs/21-当前项目状态与接手指南.md`
开始编码前必须先总结当前进度（阶段、已完成能力、风险、下一步）。

## 硬约束（不可违反）

### 架构红线
- MVVM，禁止 Activity/Fragment 写业务逻辑
- 禁止引入 Hilt、Navigation
- 7 个业务模块边界不可破坏：下载、安装、升级、应用管理、状态中心、策略中心、Repository
- 单向数据流：View → Event → ViewModel → State → View

### 编码红线
- 禁止 `!!` → 安全调用 / `requireNotNull`
- 禁止 `GlobalScope` → `viewModelScope` / `lifecycleScope`
- 禁止 `findViewById` / Kotlin synthetics → ViewBinding
- 禁止空 `catch` → 至少 `AppLogger.e(...)`
- 禁止系统 `Log` → `AppLogger`
- 禁止 `notifyDataSetChanged()` → `ListAdapter + DiffUtil`
- XML 禁止硬编码尺寸/颜色/字符串
- 禁止魔法数字/字符串 → `companion object` 或 `const val`

### 命名约定
- Kotlin: `XxxActivity` / `XxxFragment` / `XxxViewModel` 等明确后缀
- XML 布局: `activity_` / `fragment_` / `item_` 等前缀
- View ID: 组件缩写+语义 如 `tvTitle` `btnSubmit`

### 注释要求
- 注释用中文
- 成员变量加中文业务含义注释
- public 方法用 KDoc，private/internal 至少简洁注释

## Git 提交格式

`Type: Subject`（英文），Type 仅限：Feat / Fix / Refactor / Perf / Style / Docs / Revert / Build

## 提交前必检

- 业务逻辑是否泄漏到 Activity/Fragment
- 是否绕过 Repository / StateCenter / 既定模块边界
- 是否引入禁止项（Hilt / Navigation / `!!` / `GlobalScope` / 硬编码）
- 命名和资源治理是否倒退

## 参考文档

| 文件 | 用途 |
|------|------|
| `AGENTS.md` | 完整编码规范与禁止事项 |
| `docs/21-当前项目状态与接手指南.md` | 项目进度单页入口 |
| `docs/22-Agent参考规范与示例.md` | 命名示例与参考模板 |

---

*最后更新：2026-04-23*

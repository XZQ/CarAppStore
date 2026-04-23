# CarAppStore — Claude Code Instructions

> 本文件是 Claude Code 在本仓库必须遵守的完整规则。
> 项目背景与进度见 `README.md`，详细架构见 `docs/`。
> 若文档与代码现状冲突，以代码现状为准，并明确指出差异。

---

## 0. 接手流程

进入仓库后先读：`README.md` → `docs/21-当前项目状态与接手指南.md`
开始编码前必须先总结当前进度（阶段、已完成能力、风险、下一步）。
需要确认落地状态时：`git status --short` + 最近关键提交 + 对应模块源码。

## 1. 架构边界

- 强制 `MVVM`，禁止在 `Activity`/`Fragment` 中写业务逻辑
- 页面结构固定 `Activity + FragmentManager`，禁止引入 `Navigation`
- 依赖注入固定 `AppContainer` 手动装配，禁止引入 `Hilt`
- 数据层 `Repository` 模式，`ViewModel` 只与 `Repository` 交互
- 单向数据流：`View → Event → ViewModel → State → View`
- 7 个业务模块边界不可破坏：下载、安装、升级、应用管理、状态中心、策略中心、Repository
- `app` 只做壳层装配和系统桥接，禁止重新业务化

## 2. 命名与资源

- Kotlin 文件：`XxxActivity`、`XxxFragment`、`XxxViewModel`、`XxxRepository`、`XxxAdapter`、`XxxUseCase`、`XxxEntity`、`XxxUtils.kt`
- XML 布局：`activity_`、`fragment_`、`dialog_`、`bottom_sheet_`、`item_`、`view_`、`layout_`、`popup_`
- 资源语义化：
  - drawable: `ic_` / `bg_` / `img_`
  - color: 业务语义名
  - string: `模块_描述`
  - dimen: `用途_尺寸`
  - style: `组件类型.描述`
  - anim: `动作_方向`

## 3. XML 与 UI

- 根节点必须 `ConstraintLayout`，禁止 `LinearLayout`/`RelativeLayout`/`FrameLayout` 作为根节点
- 嵌套不超过 `5` 层，超过拆分为自定义 View 或 `<include>`
- View ID：组件缩写 + 语义，如 `tvTitle`、`btnSubmit`、`rvArticleList`
- 必须使用 `ViewBinding`，禁止 `findViewById` 和 Kotlin synthetics
- 禁止硬编码：尺寸 `@dimen/`、颜色 `@color/`、字符串 `@string/`

## 4. Kotlin 编码

- 禁止魔法数字/字符串（循环边界 0/1 除外）→ `companion object` 或 `const val`
- 注释用中文；成员变量加中文业务含义注释
- `public` 方法用 `KDoc`，`private`/`internal` 至少简洁注释
- 函数不超过 `40` 行；参数超过 `3` 个用数据类封装
- 禁止 `!!` → 安全调用 / 提前返回 / `requireNotNull`
- 禁止主线程 IO → `viewModelScope` / `lifecycleScope`
- 禁止 `GlobalScope`
- 禁止空 `catch` → 至少 `AppLogger.e(...)`
- 禁止系统 `Log` → 统一 `AppLogger`

## 5. 页面与组件

- 页面状态用 `sealed class` 建模，禁止布尔值拼状态
- 异步结果用 `Result` / `Resource` 封装，禁止裸 `try-catch`
- Activity 跳转 → `companion object start()`
- Fragment 传参 → `newInstance()` + `arguments`
- Fragment 间禁止直接引用 → 共享 `ViewModel` 或业务层
- 列表 → `ListAdapter + DiffUtil`，禁止 `notifyDataSetChanged()`

## 6. Git 提交

- 英文，格式 `Type: Subject`
- Type 仅限：`Feat` / `Fix` / `Refactor` / `Perf` / `Style` / `Docs` / `Revert` / `Build`
- Subject 首字母大写，祈使句或名词短语，不以句号结尾
- 示例：`Feat: Add download retry policy`、`Fix: Handle install session timeout`

## 7. 提交前检查

- 业务逻辑是否泄漏到 Activity/Fragment
- 是否绕过 Repository / StateCenter / 既定模块边界
- 是否引入禁止项（Hilt / Navigation / `!!` / `GlobalScope` / 硬编码）
- 命名和资源治理是否倒退

## 8. 禁止事项速记

| 禁止 | 正确做法 |
|------|---------|
| `findViewById` | `ViewBinding` |
| Kotlin synthetics | `ViewBinding` |
| `!!` | 安全调用 / `requireNotNull` |
| `GlobalScope` | `viewModelScope` / `lifecycleScope` |
| `notifyDataSetChanged()` | `ListAdapter + DiffUtil` |
| Activity 构造式跳转 | `companion object start()` |
| Fragment 构造函数传参 | `newInstance()` + `arguments` |
| 空 `catch` | `AppLogger.e(...)` |
| 硬编码中文文案 | `string.xml` |
| 系统 `Log` | `AppLogger` |

---

*最后更新：2026-04-23*

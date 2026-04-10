# CarAppStore AGENTS

> 本文件只保留 Codex 在本仓库中每次都必须遵守的硬规则。
> 项目背景、阶段进度、模块说明优先结合 `README.md` 与 `docs/` 阅读。
> 若 `README.md` 与本文件冲突，以本文件为准；若文档与代码现状冲突，以代码现状为准，并明确指出差异。

---

## 0. 接手与进度建立

- 每次进入仓库后，先阅读以下文件，再开始分析代码或执行任务：
  1. `README.md`
  2. `docs/25-当前项目状态与接手指南.md`
  3. `docs/08-开发顺序与落地清单.md`
  4. `docs/06-工程演进与当前阶段判断.md`
- 在开始编码、改文件、给方案前，先总结一次当前进度，至少说明：
  - 当前阶段
  - 已完成的关键能力
  - 当前风险和边界
  - 下一步最高优先级
- 若需要确认实际落地状态，可补充查看：
  - `git status --short`
  - 最近一次关键提交
  - 对应模块源码与装配代码
- 当前项目进度默认以 `docs/25-当前项目状态与接手指南.md` 作为单页入口。

## 1. 当前项目硬约束

- 强制使用 `MVVM`，禁止在 `Activity` / `Fragment` 中写业务逻辑。
- 页面结构固定为 `Activity + FragmentManager`，禁止引入 `Navigation`。
- 依赖注入固定使用 `AppContainer` 手动装配，禁止引入 `Hilt`。
- 数据层使用 `Repository` 模式，`ViewModel` 只与 `Repository` 交互，不直接访问数据库、网络或系统安装能力。
- 业务流转保持单向数据流：`View -> Event -> ViewModel -> State -> View`。
- 不得破坏当前 7 个业务模块边界：下载、安装、升级、应用管理、状态中心、策略中心、Repository。
- `app` 只做壳层装配和系统桥接，禁止重新业务化。

## 2. 命名与资源规则

- Kotlin 文件命名使用明确后缀：
  - `XxxActivity`、`XxxFragment`、`XxxViewModel`、`XxxRepository`、`XxxAdapter`、`XxxUseCase`、`XxxEntity`、`XxxResponse`、`XxxRequest`、`XxxExtensions.kt`、`XxxUtils.kt`
- XML 布局命名使用固定前缀：
  - `activity_`、`fragment_`、`dialog_`、`bottom_sheet_`、`item_`、`view_`、`layout_`、`popup_`
- 资源命名保持语义化：
  - drawable 用 `ic_` / `bg_` / `img_`
  - color 使用业务语义名
  - string 使用 `模块_描述`
  - dimen 使用 `用途_尺寸`
  - style 使用 `组件类型.描述`
  - anim 使用 `动作_方向`
- 新增字符串资源必须保持可读、可维护，避免旧式无语义命名。

## 3. XML 与 UI 规则

- 所有布局文件的根节点必须使用 `androidx.constraintlayout.widget.ConstraintLayout`。
- 禁止将 `LinearLayout`、`RelativeLayout`、`FrameLayout` 作为根节点。
- 布局嵌套层级不得超过 `5` 层，超过时拆分为自定义 View 或 `<include>`。
- View ID 命名格式为“组件类型缩写 + 语义描述”，如 `tvTitle`、`btnSubmit`、`rvArticleList`。
- 必须使用 `ViewBinding`，禁止 `findViewById` 和 Kotlin synthetics。
- XML 中禁止硬编码：
  - 尺寸，必须使用 `@dimen/`
  - 颜色，必须使用 `@color/`
  - 字符串，必须使用 `@string/`

## 4. Kotlin 编码规则

- 禁止魔法数字和魔法字符串；除循环边界 `0` / `1` 外，其余常量必须提取到 `companion object` 或顶级 `const val`。
- 所有新增注释必须使用中文。
- 所有成员变量必须添加中文注释，说明业务含义，不是类型说明。
- 所有方法必须添加注释：
  - `public` 方法使用 `KDoc`
  - `private` / `internal` 方法至少写简洁注释
  - 关键业务步骤补充行内注释
- 函数长度尽量不超过 `40` 行，超出时优先拆分职责。
- 参数超过 `3` 个时，优先使用数据类封装。
- 禁止使用 `!!`，必须使用安全调用、提前返回或 `requireNotNull`。
- 禁止在主线程执行网络、数据库、文件 IO 等耗时操作。
- 协程只能在 `viewModelScope` 或 `lifecycleScope` 中启动，禁止使用 `GlobalScope`。
- 捕获异常时禁止空 `catch`，至少通过统一日志入口记录异常。
- 禁止直接使用系统 `Log`，统一使用 `AppLogger`；禁止输出密码、Token 等敏感信息。

## 5. 页面与组件规则

- 每个页面状态使用 `sealed class` 或等价的明确状态模型建模，禁止用多个布尔值拼状态。
- 网络或异步结果统一使用 `Result` 或自定义 `Resource` 封装，避免在 `ViewModel` 中散落裸 `try-catch`。
- Activity 跳转参数必须收口到目标 Activity 的 `companion object start()` 中。
- Fragment 传参必须使用 `newInstance()` + `arguments`，禁止构造函数传参。
- Fragment 之间禁止直接持有彼此引用，使用共享 `ViewModel` 或业务层通信。
- RecyclerView 列表强制使用 `ListAdapter + DiffUtil`，禁止 `notifyDataSetChanged()`。

## 6. 提交前检查

- 是否把业务逻辑写回了 `Activity` / `Fragment`
- 是否绕过了 `Repository`、`StateCenter` 或既有业务模块边界
- 是否在 `app` 中新增了本应属于 `business` / `data` / `core` 的逻辑
- 是否引入了 `Hilt`、`Navigation`、`findViewById`、Kotlin synthetics、`GlobalScope`、`!!`
- 是否在 XML 或 Kotlin 中新增了硬编码文案、颜色、尺寸、魔法常量
- 是否让资源命名、字符串治理或模块依赖方向发生倒退

## 7. 禁止事项速记

- `findViewById` -> `ViewBinding`
- Kotlin synthetics -> `ViewBinding`
- `!!` -> 安全调用 / 提前返回 / `requireNotNull`
- `GlobalScope.launch` -> `viewModelScope.launch` / `lifecycleScope.launch`
- `notifyDataSetChanged()` -> `ListAdapter + DiffUtil`
- Activity 构造式跳转 -> `companion object start()`
- Fragment 构造函数传参 -> `newInstance() + arguments`
- 空 `catch` -> 至少 `AppLogger.e(...)`
- 业务代码直接拼中文 -> `string.xml` 或统一文案入口

---

*最后更新：2026-04-10*

# CarAppStore

车载应用商店 Android 工程 — 主链路已通，R1-R6 真实能力全部完成，103 单元测试全绿。

## 技术栈

- Kotlin + Coroutines + ViewBinding
- MVVM，手动 DI（AppContainer），FragmentManager 导航
- 不使用 Hilt / Navigation
- Java 11 target，JBR 17 构建

## 模块结构（14 模块）

```
app/              壳层：AppContainer、MainActivity
common/           BaseFragment、AppServices、UI 共享
core/             RealFileDownloader、RealPackageInstaller、DownloadStore、VersionedJsonStore
data/             RealAppRepository、3 DataSource（remote/local/system）、LocalStoreFacade
business/         7 个领域模块：download、install、upgrade、appmanager、state、policy
feature-*/        8 个页面模块：home、detail、myapp、search、downloadmanager、installcenter、upgrade、debug
```

## 已完成能力

| 能力 | 状态 |
|------|------|
| HTTP 下载（Range、分片、校验、断点续传） | ✅ |
| 系统安装（PackageInstaller.Session、用户确认） | ✅ |
| 统一数据层（JSON 持久化、schema 迁移、并发锁） | ✅ |
| 执行控制（IO 中断、去重保护、暂停/取消） | ✅ |
| Repository 真实化（PackageManager 查询、Intent 启动） | ✅ |
| 批量升级（checkAll、串行执行、策略门控） | ✅ |
| 升级失败可重试（StateReducer 回退） | ✅ |
| 资源化商店目录（分类、推荐理由、详情元信息） | ✅ |
| 远端目录回退链路（HTTP -> 缓存 -> 资源目录） | ✅ |
| 策略中心可观察化（设置流驱动页面刷新） | ✅ |
| 实时策略信号（网络/存储监听接入） | ✅ |
| 首页/搜索/详情页页面状态机 | ✅ |
| 我的应用/任务中心页面状态机 | ✅ |

## 构建与测试

```bash
./gradlew testDebugUnitTest    # 103 tests, 0 failures
./gradlew compileDebugKotlin   # 全量编译
./gradlew app:assemble         # 构建 APK
```

JBR 17 路径已固定在 `gradle.properties`。

## 文档入口

- **当前状态**：`docs/21-当前项目状态与接手指南.md`（最新进度、风险、下一步）
- **架构总览**：`docs/01-架构总览.md`
- **模块详解**：`docs/03-七个业务模块详解.md`、`docs/09-16`
- **里程碑**：`docs/17-已完成里程碑归档.md`（R1-R6 全部完成）
- **编码规范**：`AGENTS.md`（命名、XML、ViewBinding、线程等）

## 开发规范摘要

- Activity/Fragment 不承载业务逻辑，严格 MVVM
- 所有新增常量必须提取，禁止魔法数字/字符串
- 新增注释使用中文
- `data class` 参数带中文注释
- 中文文案走统一入口，不在业务代码中拼接
- 详见 `AGENTS.md`

## 下一步

1. 真实设备联调下载→安装→升级全链路
2. 远端数据接入（把当前 HTTP/缓存/资源回退链路接到真实 API）
3. 策略动态化（补驻车/OEM 车况真实信号，而非仅网络/存储）
4. 真实后端/OEM 联调清单落地并推进外部对接

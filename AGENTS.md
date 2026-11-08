# CarAppStore AGENTS

> 本文件定义 Agent 在本仓库内的接手规则，**不依赖任何特定机器上的外部文件**。
> 工作环境若额外提供全局 Android 规范（例如 `~/.agents/AGENTS.md`），可作为通用编码约定参考；
> 它不是构建或开发的前置条件，缺失时一律以本文件与 `CLAUDE.md` 为准。
> 优先级：用户要求 > 项目级 `CLAUDE.md` > 本文件与可选的全局规范 > 既有代码风格。

---

## 接手必读

| 文档 | 用途 |
| --- | --- |
| `README.md` | 项目总览、环境要求、构建命令 |
| `CLAUDE.md` | 项目特有的架构边界与硬约束 |
| `docs/00-产品定位与平台边界.md` | 修改任何文档前先确认的统一术语和平台边界 |
| `docs/21-当前项目状态与接手指南.md` | 当前阶段、验证口径、风险与剩余事项 |
| `docs/29-换机接手与当前进度总览.md` | 换机、重新 clone 时的同步基线与未完成边界 |
| `docs/36-2026-10深度审查与修复规划.md` | 最近一轮审查结论与实际门禁结果 |
| `docs/22-Agent参考规范与示例.md` | 详细示例与参考模板 |

## 最低限度硬约束

- MVVM + 单向数据流；依赖装配固定 `AppContainer`，禁止引入 Hilt/Dagger。
- 页面导航固定 `Activity + FragmentManager`，禁止引入 Navigation。
- 下载、安装、升级、应用管理、状态中心、策略中心、Repository 七个业务域边界不可破坏，跨模块调用只走公开接口。
- `app` 模块只做壳层装配和系统桥接，不重新业务化。
- 新增代码沿用既有 Kotlin 风格、中文 KDoc、ViewBinding 与资源命名约定。
- 完整架构说明见 `docs/01-架构总览.md` 与 `docs/03-七个业务模块详解.md`。

## 环境与构建

- 构建前先运行 `python tools/verification/check_build_env.py` 自检 JDK、Android SDK 与 `local.properties`；
  该脚本只做诊断，不安装工具链，也不修改仓库文件。
- 若自检报缺失，按脚本给出的修复建议配置 JDK 21、Android SDK Platform 36 和 `local.properties` 后再运行 `gradlew`。
- 验证口径以 `docs/36` 和对应提交 SHA 的 CI 产物为准；不要在文档里重复硬编码测试数量，避免再次漂移。

## 产品定位硬约束

- CarAppStore 是**跨平台应用分发与管理 App**，不是车端专属 App。
- 当前仓库是 Android/Kotlin 客户端实现；不得把“当前实现平台”误写成“产品只面向 Android 或车机”。
- 手机、平板、桌面级大屏和可选车载形态共享通用业务主线；OEM、驻车、行车和车机 ROM 仅属于可选车载平台适配。
- 新增或修改文档前先读 `docs/00-产品定位与平台边界.md`，不得再用“车载应用商店”“面向车机场景”概括整个项目。

---

*最后更新：2026-11-04*

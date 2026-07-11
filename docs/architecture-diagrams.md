# CarAppStore 架构图索引

本目录下的架构图根据当前源码、`README.md`、`docs/21-当前项目状态与接手指南.md`、`docs/01-架构总览.md` 和 `docs/03-七个业务模块详解.md` 绘制。

## 图文件

| 文件 | 说明 |
| --- | --- |
| `architecture-project-modules.svg` | 项目模块总览：app、feature、business、data、core、common 的分层与依赖方向 |
| `architecture-business-modules.svg` | 七个业务模块关系：下载、安装、升级、应用管理、状态中心、策略中心、Repository |
| `architecture-main-flow.svg` | 主链路流程：页面事件到策略判断、状态更新、Repository、core 执行与 UI 刷新 |

## 阅读顺序

1. 先看 `architecture-project-modules.svg`，确认工程分层和模块职责。
2. 再看 `architecture-business-modules.svg`，确认业务层七个模块的边界。
3. 最后看 `architecture-main-flow.svg`，理解下载、安装、升级如何在同一套架构内闭环。

## 关键约束

- `app` 只做壳层装配、导航和系统桥接。
- 页面固定为 `Activity + FragmentManager`，不引入 Navigation。
- 依赖注入固定为 `AppContainer` 手动装配，不引入 Hilt/Dagger。
- 业务跨模块调用通过公开接口完成，避免直接访问其他模块内部实现。
- `StateCenter` 是运行态统一来源，`Repository` 是数据聚合入口，`PolicyCenter` 是业务执行前置判断入口。
- 目录入口由 `AppCatalogValidator` 拒绝非法标识和冲突数据，下载任务目录由 `DownloadStore` 做 canonical containment。
- 安装链路在创建 Session 前通过 `AndroidPackageIdentityVerifier` 核对 APK 身份，成功回调后再以 PackageManager 事实收口。

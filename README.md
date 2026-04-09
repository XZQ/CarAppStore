# CarAppStore

一个基于 MVVM 的车载应用商店 Android 工程骨架，当前工程已经具备首页、详情页、我的应用、下载管理、安装管理、升级管理和开发设置等基础页面与业务编排能力。

## 文档说明
- 项目介绍、模块划分、构建方式看本文件。
- 代码生成与改造规范以 [AGENTS.md](./AGENTS.md) 为准。
- 如果 README 与 AGENTS 出现冲突，以 `AGENTS.md` 为准。

## 当前架构
- 架构模式：MVVM
- 页面组织：`Activity + FragmentManager`
- 依赖注入：手动注入，入口为 `AppContainer`
- 导航方案：不使用 Navigation
- 注入框架：不使用 Hilt
- 数据分层：`feature / business / data / core / common / app`

## 核心业务模块
- 下载模块
- 安装模块
- 升级模块
- 应用管理模块
- 状态中心
- 策略中心
- Repository

## 当前工程包含
- 首页 / 详情页 / 我的应用 基础页面
- 下载中心 / 安装中心 / 升级中心
- 开发设置页与下载环境切换能力
- `AppContainer` 手动装配
- Fake 数据源，可作为后续真实实现的替身
- 真实下载器 / 安装器骨架与模拟兜底实现

## 开发规范摘要
- 严格遵循 MVVM，Activity / Fragment 不承载业务逻辑。
- Repository 负责聚合 remote / local / system 数据来源。
- 所有新增常量必须提取，禁止散落魔法数字和魔法字符串。
- 所有新增注释必须使用中文。
- `data class` 参数必须带中文注释，成员变量也需要说明业务含义。
- 中文文案不要在业务代码中直接拼接，优先放到统一文案入口或 `string.xml`。
- 命名、XML、ViewBinding、线程与异常处理规范请直接参考 [AGENTS.md](./AGENTS.md)。

## 构建命令
```bash
./gradlew app:assemble
```

如果本地需要固定环境，也可以使用项目当前验证通过的命令：

```bash
JAVA_HOME=/home/didi/.jdks/jbr-17.0.14 GRADLE_USER_HOME=/home/didi/.gradle /home/didi/.gradle/wrapper/dists/gradle-8.14.3-all/10utluxaxniiv4wxiphsi49nj/gradle-8.14.3/bin/gradle app:assemble
```

## 下一步建议
1. 将 Fake Repository 逐步替换为真实的 remote / local / system 数据源实现。
2. 继续补齐真实下载、安装、升级链路中的平台能力接入。
3. 为状态中心、任务中心和策略中心补充更完整的自动化测试。
4. 按 `AGENTS.md` 继续清理历史布局、注释和资源命名上的存量问题。

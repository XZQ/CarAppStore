# CarAppStore

一个基于 MVVM 的车载应用商店 Android 工程骨架。

## 已落地约束
- MVVM
- 不使用 Hilt
- 不使用 Navigation
- 使用手动依赖注入（AppContainer）
- 使用 FragmentManager 管理页面切换
- 业务层保留 7 个核心模块：
  - 下载模块
  - 安装模块
  - 升级模块
  - 应用管理模块
  - 状态中心
  - 策略中心
  - Repository

## 当前骨架包含
- 首页 / 详情页 / 我的应用 基础页面
- AppContainer 手动注入
- feature / domain / data / core / common 分层目录
- Fake 数据源，可直接作为后续真实实现的替身

## 下一步建议
1. 将 Fake Repository 替换为真实 remote/local/system 数据源
2. 补充下载模块与安装模块的真实实现
3. 扩展状态中心为统一 Flow 状态源
4. 再接入升级模块和策略中心细节

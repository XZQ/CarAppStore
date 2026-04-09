# R2 真实安装器与安装中心 Session 能力收口

## 1. 文档目的
本文件合并原先的 R2 阶段增量文档，统一描述以下内容：

- `R2-1` 真实安装器骨架
- `R2-2` PackageInstaller Session 接入骨架
- `R2-3` 系统安装回调骨架
- `R2-4 ~ R2-8` 安装中心 Session 级展示、恢复、筛选、摘要、失败清理与 Tab 化
- `R2` 收口结论

---

## 2. R2 阶段目标
R2 的核心目标是把安装链路从“模拟安装”推进到“真实安装器原型可运行”，并让安装中心具备 Session 级可视化与整理能力。

目标拆解为两部分：

1. 安装执行层
   - 引入真实安装器入口
   - 建立 `create / write / commit` 三段式骨架
   - 接入 Session 持久化

2. 安装中心 UI 层
   - 展示 Session 级状态
   - 支持恢复、摘要、双层筛选、失败清理、重试与轻量 Tab 化

---

## 3. 主要实现结果

### 3.1 安装执行层
已形成以下核心类：

- `RealPackageInstaller`
- `PackageInstallerSessionAdapter`
- `SystemPackageInstallerSessionAdapter`
- `InstallCommitResult`
- `InstallSessionRecord`
- `InstallSessionStatus`
- `InstallSessionStore`

当前链路已具备：

- 真实安装入口
- `create / write / commit` 骨架
- Session 建立与持久化
- 冷启动恢复修正

### 3.2 安装中心 Session 能力
安装中心已完成以下 Session 级能力：

- Session 状态展示
- Session 摘要与统计
- Session 失败态整理
- Session 重试策略
- App 级 + Session 级双层筛选
- 轻量 Tab 化切换

---

## 4. 当前关键类

### 4.1 安装执行与会话层
- `RealPackageInstaller`
- `PackageInstallerSessionAdapter`
- `SystemPackageInstallerSessionAdapter`
- `InstallSessionStore`
- `InstallSessionRecord`
- `InstallSessionStatus`

### 4.2 安装编排层
- `DefaultInstallManager`

### 4.3 安装中心 UI 层
- `InstallCenterFragment`
- `InstallCenterViewModel`
- `InstallCenterUiState`
- `InstallCenterControlsUiState`
- `InstallSessionFilter`
- `SessionBucket`

---

## 5. 当前能力边界

### 已具备
- 真实安装器原型入口
- Session 持久化
- 冷启动恢复修正
- Session 级展示 / 摘要 / 筛选 / 清理 / 重试

### 仍未完全具备
- 真正系统 `PackageInstaller Session`
- `PendingIntent / Broadcast` 完整回调
- OEM 安装差异适配
- Session 独立详情页

---

## 6. 收口结论
R2 已完成“真实安装器原型 + 安装中心 Session 管理”的第一轮闭环。

它的价值不在于已经把系统安装彻底做完，而在于：

- 安装链路已经不再只是占位
- 安装中心已经能以 Session 为单位观察和整理任务
- 后续接系统安装回调、OEM 适配、Session 详情页时有稳定落点

---

## 7. 后续建议
建议优先级如下：

1. 真正系统安装会话接入
2. `PendingIntent / Broadcast` 回调闭环
3. OEM 差异适配
4. 安装中心 Session 独立详情页

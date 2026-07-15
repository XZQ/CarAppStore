@C:\Users\XZQ\.agents\AGENTS.md
@C:\Users\XZQ\.codex\RTK.md

# CarAppStore AGENTS

> 全局 Android 规范由 `C:\Users\XZQ\.agents\AGENTS.md` 统一维护。
> 本仓库的项目特有规则见 `CLAUDE.md`；冲突时遵循“用户要求 > 项目级 CLAUDE.md > 全局 AGENTS.md > 既有代码风格”。

---

## Codex 注意事项

- 全局编码规范、命名约定和禁止事项见 `C:\Users\XZQ\.agents\AGENTS.md`
- CarAppStore 特有架构边界见 `CLAUDE.md`
- 项目背景与进度见 `README.md`
- 详细架构与模块说明见 `docs/`
- 详细示例与参考模板见 `docs/22-Agent参考规范与示例.md`

## 产品定位硬约束

- CarAppStore 是**跨平台应用分发与管理 App**，不是车端专属 App。
- 当前仓库是 Android/Kotlin 客户端实现；不得把“当前实现平台”误写成“产品只面向 Android 或车机”。
- 手机、平板、桌面级大屏和可选车载形态共享通用业务主线；OEM、驻车、行车和车机 ROM 仅属于可选车载平台适配。
- 新增或修改文档前先读 `docs/00-产品定位与平台边界.md`，不得再用“车载应用商店”“面向车机场景”概括整个项目。

---

*最后更新：2026-07-16*

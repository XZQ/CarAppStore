# M1-M7 多 module 拆分与基础收口

## 1. 文档目的
本文件合并原先 M1 ~ M7 的多 module 增量文档，统一描述：

- M1 拆分设计
- M2 工程骨架
- M3 第一批代码迁移
- M4 app 壳层装配收平
- M5 包名与 namespace 收口
- M6 编译修正与资源校验
- M7 第一批 Kotlin 字符串清理

---

## 2. 拆分目标
M1 ~ M7 的核心目标是把原先单 app 工程逐步收敛到可持续维护的多 module 结构，同时处理拆分早期最容易引发编译和资源问题的部分。

---

## 3. 当前工程结构
当前已经建立并真实承载代码的基础 module：

- `:app`
- `:common`
- `:core`
- `:data`
- `:business`

在此基础上，后续继续演进到 `feature-*` 页面模块。

当前依赖方向保持为：

- `:core -> :common`
- `:data -> :common + :core`
- `:business -> :common + :core + :data`
- `:app -> 基础 module + feature module`

---

## 4. M1 ~ M7 关键结果

### M1：拆分设计
完成 module 目标结构、职责边界和迁移顺序设计。

### M2：工程骨架
多 module 工程骨架建立完成。

### M3：第一批真实代码迁移
第一批代码已迁入：

- `:common`
- `:core`
- `:data`
- `:business`

### M4：app 壳层收平
完成：

- `AppStoragePaths`
- `AppContainer` 装配收平
- `MainActivity` 导航事务收敛

### M5：包名与 namespace 收口
统一源码包名到：

- `com.nio.appstore.*`

### M6：编译与资源校验
完成旧包名残留清理和 XML 硬编码文本第一轮收敛。

### M7：Kotlin 文案收口
完成第一批 Kotlin 页面字符串清理，为后续任务中心文案治理打底。

---

## 5. 当前价值
这一阶段的价值主要体现在三点：

1. 工程结构已经从“单体 app”进入“基础模块可持续承载代码”
2. app 壳层职责更清晰，开始像壳层而不是业务堆叠层
3. 包名、资源和文案的第一轮清理，降低了后续 feature module 化的摩擦成本

---

## 6. 当前仍需注意
- `:app` 仍然是最终装配入口
- 资源归属与跨 module 引用仍需要继续校验
- feature module 化完成前，仍可能出现 binding / import / R 相关问题

---

## 7. 后续建议
建议直接衔接：

1. M8 ~ M12 任务中心文案体系收口
2. M13 ~ M15 feature module 化与编译收口

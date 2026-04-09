# M13-M15 Feature module 化与编译收口

## 1. 文档目的
本文件合并原先 M13 ~ M15 的 feature module 增量文档，统一描述：

- M13 第一轮 feature module 化
- M14 结构性编译修正与资源归属校验
- M15 资源 / binding / import 编译级校验与收口

---

## 2. 当前 module 结果
当前已建立并承载真实页面代码的 feature module：

- `:feature-home`
- `:feature-detail`
- `:feature-myapp`
- `:feature-search`
- `:feature-downloadmanager`
- `:feature-upgrade`
- `:feature-installcenter`
- `:feature-debug`

---

## 3. M13 ~ M15 关键结果

### M13：第一轮 feature module 化
已完成：

- `app/feature/*` 第一批真实源码迁入 feature module
- 第一批 feature 专属布局资源迁移
- `common -> app` 的直接耦合开始拆解

### M14：结构性编译修正
已完成：

- 引入 `AppServices`
- `AppContainerProvider` 暴露 `appServices`
- `AppContainer` 实现 `AppServices`
- 下载环境类型迁入 `data/downloadenv/*`
- 任务中心共享布局迁入 `common`

### M15：编译级静态收口
已完成：

- 修正 feature 的 `R` / `databinding` 引用
- 修正 `common` 共享布局的 binding 包路径
- 补齐 `common` 共享 `strings / colors / drawable`
- `common` 启用 `viewBinding`
- 消除 `common -> data/business` 的反向代码依赖
- feature 布局显式使用 `common` 资源命名空间
- 补齐 `TaskCenterUiFormatter` 缺失的方法

---

## 4. 当前价值
这一阶段的价值不是“目录被拆开了”，而是：

- feature module 已经真正承载页面代码
- 反向依赖与资源归属问题得到第一轮结构性收敛
- 后续做真实 Gradle 编译验收时，问题会集中在少量残余点，而不是系统性结构错误

---

## 5. 当前仍未完成
- 仓库当前没有 `gradlew`
- 环境当前没有系统 `gradle`
- 因此还没有完成真实 Gradle 编译验收

---

## 6. 后续建议
建议优先级如下：

1. 补齐 `gradlew` 或可用 `gradle`
2. 按 `:common -> :business -> :feature-* -> :app` 跑真实编译
3. 按真实编译报错继续做第二轮收口

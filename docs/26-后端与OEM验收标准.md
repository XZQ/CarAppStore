# 26. 后端与 OEM 验收标准

> 最后更新：2026-04-20
> 本文档面向后端和 OEM 团队，说明 CarAppStore 客户端对接的验收标准。

## 1. 后端目录 API 验收标准

### 1.1 接口对接要求

客户端已准备好的组件：

| 组件                              | 职责                           |
|----------------------------------|-------------------------------|
| `AppCatalogHttpClient`           | HTTP 请求执行抽象              |
| `AppCatalogHttpRequest`          | 请求封装（URL、方法、头、体）    |
| `AppCatalogHttpResponse`         | 响应封装（状态码、头、体）       |
| `AppCatalogCacheMetadataStore`   | 缓存元数据（ETag、Last-Modified）持久化 |
| `ResilientAppCatalogSource`      | 回退链路编排（HTTP -> 缓存 -> 资源目录） |
| `AppRemoteDataSource`            | 远端数据源统一入口               |

后端需要提供：

- 目录接口正式地址（对应 `DownloadEnvironmentConfig.catalogEndpointUrl`）
- 鉴权方案或灰度头定义（对应 `DownloadEnvironmentConfig.catalogRequestHeaders`）
- 返回字段说明与错误码说明
- 缓存策略约定（ETag / Last-Modified / Cache-Control）
- 降级策略约定（空目录、部分字段缺失、非 200 返回）

### 1.2 字段对齐验收

客户端目录 DTO 为 `AppCatalogItemResponse`，后端返回的每个应用对象应包含以下字段：

| 字段                    | 类型           | 必填 | 说明                          |
|------------------------|---------------|------|------------------------------|
| `appId`                | String        | 是   | 稳定的应用唯一标识              |
| `packageName`          | String        | 是   | Android 应用包名               |
| `name`                 | String        | 是   | 应用显示名称                   |
| `description`          | String        | 否   | 应用简要描述                   |
| `versionName`          | String        | 是   | 当前版本号（如 "1.2.3"）        |
| `category`             | String        | 否   | 分类（如 "导航"、"音乐"）        |
| `editorialTag`         | String        | 否   | 运营标签（如 "精选"、"新品"）    |
| `recommendedReason`    | String        | 否   | 推荐理由文案                    |
| `searchKeywords`       | List\<String> | 否   | 搜索关键词列表                  |
| `developerName`        | String        | 否   | 开发者名称                      |
| `ratingText`           | String        | 否   | 评分文案（如 "4.5 分"）         |
| `sizeText`             | String        | 否   | 包体大小文案（如 "32.5 MB"）    |
| `lastUpdatedText`      | String        | 否   | 最后更新时间文案                 |
| `compatibilitySummary` | String        | 否   | 兼容性说明                      |
| `permissionsSummary`   | String        | 否   | 权限摘要                        |
| `updateSummary`        | String        | 否   | 更新摘要                        |
| `latestVersion`        | String        | 否   | 最新版本号（用于升级比对）        |
| `hasUpgrade`           | Boolean       | 否   | 是否有可升级版本                 |
| `changelog`            | String        | 否   | 升级变更日志                    |

验收要求：

- 后端返回的 JSON 字段名与上表完全一致
- 必填字段缺失时，后端不应返回该条目；客户端侧会做防御性解析但依赖完整字段展示
- 可选字段缺失时客户端不会崩溃，但对应位置展示为空
- `searchKeywords` 必须是字符串数组，不能是逗号分隔的单个字符串
- 整体返回结构为 `{ "apps": [ ... ] }`，对应客户端 `AppCatalogResponse`

### 1.3 缓存策略验收

客户端已实现 HTTP 条件请求缓存，验收标准如下：

| 场景                          | 后端行为                                    | 客户端期望                            |
|-------------------------------|---------------------------------------------|---------------------------------------|
| 首次请求                      | 返回 200 + 完整目录 JSON                     | 解析目录、写入缓存、展示列表           |
| 再次请求（内容未变）           | 返回 304 + 空 Body                           | 复用本地缓存目录，不重新解析           |
| 内容已更新                    | 返回 200 + 新目录 JSON + 新 ETag/Last-Modified | 解析新目录、更新缓存、刷新列表         |
| 请求失败（超时/5xx）           | 返回非 200 或无响应                          | 回退到本地缓存目录                     |
| 本地缓存不存在且请求失败       | 返回非 200 或无响应                          | 回退到资源目录（raw/app_store_catalog.json） |

后端需要支持的响应头：

- `ETag`：目录内容的唯一标识，客户端会在后续请求中以 `If-None-Match` 回传
- `Last-Modified`：目录最后修改时间，客户端会以 `If-Modified-Since` 回传
- `Cache-Control`：建议至少提供 `max-age` 或 `no-cache`，指导客户端判断缓存新鲜度

### 1.4 降级路径验收

客户端的三级回退链路：

```
HTTP 远端目录 → 本地缓存目录 → 资源目录（raw/app_store_catalog.json）
```

验收标准：

- [ ] **接口超时回退到本地缓存**：后端接口超过 10 秒无响应时，客户端自动使用上次成功缓存的目录
- [ ] **本地缓存损坏回退到资源目录**：缓存文件被篡改或损坏时，客户端回退到 APK 内置的资源目录
- [ ] **部分字段缺失不崩溃**：后端返回的某个应用缺少可选字段时，该条目仍可展示，缺失字段显示为空
- [ ] **空目录不崩溃**：后端返回 `{ "apps": [] }` 时，客户端展示空列表页面而非崩溃
- [ ] **JSON 格式错误不崩溃**：后端返回非法 JSON 时，客户端回退到缓存或资源目录

### 1.5 鉴权验收

鉴权通过 `DownloadEnvironmentConfig.catalogRequestHeaders` 注入，不侵入业务层。

当前预设的请求头（测试环境）：

```
X-Client-Channel: carappstore-test
X-Client-Platform: android-car
```

当前预设的请求头（生产环境）：

```
X-Client-Channel: carappstore-prod
X-Client-Platform: android-car
```

验收标准：

- [ ] **自定义请求头正常传递**：客户端发出的 HTTP 请求中包含配置的所有请求头
- [ ] **灰度标识通过请求头传递**：后端如需灰度分流，应基于请求头中的标识字段路由
- [ ] **鉴权失败返回标准错误码**：后端鉴权不通过时应返回 401 或 403，客户端回退到缓存
- [ ] **请求头可按环境切换**：通过开发者设置切换环境后，请求头随之变更

## 2. OEM 车况信号验收标准

### 2.1 接入方式验收

客户端已预留的接口：

| 接口/类                             | 职责                                   |
|-------------------------------------|---------------------------------------|
| `VehicleStateSignalProvider`        | 车况信号提供者接口（`observeVehicleState()` + `currentVehicleState()`） |
| `VehicleRuntimeState`               | 车况信号数据结构（`parkingMode`、`sourceName`） |
| `PolicyRuntimeSignalProvider`       | 聚合策略信号接口（WiFi + 存储 + 车况）   |
| `PolicyRuntimeSignals`              | 聚合信号数据结构（`wifiConnected`、`parkingMode`、`lowStorageMode`） |
| `StaticVehicleStateSignalProvider`  | 默认兜底实现（固定输出驻车=true）        |

OEM 需要提供的信号：

- 驻车状态来源（是否处于驻车/P 档）
- 点火或电源状态来源（ACC ON / ACC OFF）
- 行车限制或前后台限制来源
- 接入方式：广播 / Binder / SDK，任一均可

OEM 接入步骤（不改业务层）：

1. 新增一个类实现 `VehicleStateSignalProvider` 接口
2. 在 `observeVehicleState()` 中返回实时 `StateFlow<VehicleRuntimeState>`
3. 在 `currentVehicleState()` 中返回当前快照
4. 在 `AppContainer` 中将 `StaticVehicleStateSignalProvider` 替换为 OEM 实现
5. `DefaultPolicyCenter` 和 `AndroidPolicyRuntimeSignalProvider` 无需任何修改

### 2.2 信号质量验收

| 验收项                                 | 标准                                  | 说明                                              |
|----------------------------------------|---------------------------------------|--------------------------------------------------|
| 信号延迟                               | < 500ms                               | 从车况实际变化到 `VehicleRuntimeState` 流发射的时间差 |
| 信号抖动容忍                           | 策略状态 5 秒内不频繁切换              | 短暂抖动（如驻车信号闪动）不应导致页面反复刷新       |
| 信号丢失时回退                         | 回退到安全默认值（驻车=false）         | OEM provider 崩溃或无响应时，不应影响客户端其他功能  |
| 首次信号可获取                         | 冷启动后 3 秒内获取首次信号            | 超时未获取到时使用默认值                           |
| 信号流连续性                           | `StateFlow` 不抛异常不完成             | provider 内部异常应被捕获并回退到默认值，不中断流    |

### 2.3 策略联动验收

- [ ] **驻车状态变化后页面实时刷新**：从行车切到驻车后，被策略拦截的操作按钮自动变为可操作状态，无需手动刷新页面
- [ ] **行车状态下安装/升级被正确拦截**：`DefaultPolicyCenter` 的 `canInstall()` 返回 false，升级同理
- [ ] **OEM provider 异常时回退到默认策略**：provider 抛异常或返回 null 时，`AndroidPolicyRuntimeSignalProvider` 仍能正常工作，使用兜底值
- [ ] **信号抖动不导致任务状态频繁闪烁**：短时间内信号来回切换时，策略判断结果保持稳定
- [ ] **Wi-Fi 和存储信号与 OEM 信号独立**：OEM 车况信号异常不影响 Wi-Fi 检测和存储检测

## 3. 真机安装链路验收标准

### 3.1 安装器验收

客户端使用 Android 系统 `PackageInstaller` API 进行安装。

验收标准：

- [ ] **PackageInstaller.Session 正常创建**：调用 `PackageManager.getPackageInstaller().openSession()` 不抛异常
- [ ] **APK 数据正确写入 Session**：通过 `Session.openWrite()` 写入的 APK 数据完整
- [ ] **用户确认页正常弹出**：调用 `Session.commit()` 后系统确认页 Activity 正常弹出
- [ ] **安装成功回调正确**：用户点击确认后，通过 `IntentSender` 收到 `STATUS_SUCCESS`
- [ ] **安装失败回调正确**：用户拒绝或其他原因失败时，收到对应的失败状态码和错误信息
- [ ] **安装会话 ID 持久化与恢复**：客户端通过 `InstallSessionStore` 持久化 session ID，强杀后可恢复

### 3.2 OEM 差异验收

不同车机 ROM 可能在以下方面存在差异，需要逐机型记录：

```
| 项目                     | 车型A（ROM 1.0） | 车型B（ROM 2.0） | 车型C（ROM 1.5） |
|-------------------------|------------------|------------------|------------------|
| 系统确认页样式            |                  |                  |                  |
| 确认页是否可自定义        |                  |                  |                  |
| 安装成功回调码            |                  |                  |                  |
| 安装失败回调码及含义       |                  |                  |                  |
| 安装超时时间（如有）       |                  |                  |                  |
| 静默安装是否支持          |                  |                  |                  |
| 系统签名应用是否免确认     |                  |                  |                  |
| 多用户/多空间安装行为     |                  |                  |                  |
```

验收步骤：

1. 在每款目标车型上安装 CarAppStore APK
2. 触发一次完整安装流程并截图记录确认页样式
3. 分别测试"用户确认"和"用户拒绝"两个分支
4. 记录回调状态码和错误信息
5. 强杀进程后重新打开，验证安装会话恢复
6. 将结果填入上表

## 4. 联调时序建议

### 4.1 推荐排期

| 阶段 | 时间      | 主要工作                                        | 交付物                           |
|------|-----------|------------------------------------------------|---------------------------------|
| 第1周 | 第 1-5 天 | 后端目录 API 联调                               | 目录可加载、缓存可命中、回退可工作 |
| 第2周 | 第 6-10 天| OEM 车况信号联调                                | 信号可接入、策略可联动、抖动可容忍 |
| 第3周 | 第 11-15 天| 真机全链路验证                                  | 下载-安装-升级全链路通过回归清单   |
| 第4周 | 第 16-20 天| 灰度和稳定性验证                                | 灰度头可切换、长时间运行不崩溃     |

### 4.2 各阶段验收门禁

**第1周门禁（后端联调完成标志）：**

- [ ] 200 返回时目录正常解析并写入缓存
- [ ] 304 返回时复用缓存目录不重新解析
- [ ] 鉴权头正常注入并被后端识别
- [ ] 接口超时时回退到本地缓存
- [ ] 缓存不存在时回退到资源目录

**第2周门禁（OEM 信号联调完成标志）：**

- [ ] `VehicleStateSignalProvider` 的 OEM 实现可在设备上正常获取驻车信号
- [ ] 驻车信号变化后策略在 500ms 内刷新
- [ ] 行车状态下安装和升级被正确拦截
- [ ] OEM provider 异常时客户端不崩溃，回退到默认策略

**第3周门禁（真机全链路完成标志）：**

- [ ] 回归测试清单（文档 25）中所有项 PASS 或 N/A（标记原因）
- [ ] 无 P0 缺陷，P1 缺陷不超过 3 个且有明确修复计划
- [ ] 每款目标机型至少完成一轮完整回归

**第4周门禁（灰度和稳定性完成标志）：**

- [ ] 灰度请求头可按环境切换，后端正确路由
- [ ] 连续运行 24 小时无崩溃、无内存泄漏
- [ ] 批量升级 10 个应用无异常
- [ ] 网络弱连接环境下（丢包率 20%）仍可正常使用

### 4.3 联调问题升级路径

| 问题类型           | 负责方   | 升级条件                   | 升级通道         |
|-------------------|---------|---------------------------|-----------------|
| 接口字段不匹配     | 后端     | 客户端无法解析或字段缺失     | 联调群 + 工单    |
| 缓存行为不符合预期 | 后端     | 304 或 ETag 处理异常        | 联调群 + 工单    |
| OEM 信号延迟或丢失 | OEM     | 延迟 > 500ms 或信号中断     | 联调群 + 工单    |
| 安装器 ROM 差异    | OEM     | 回调码或确认页行为与预期不符 | 联调群 + 工单    |
| 客户端崩溃或异常   | 客户端   | 回归测试中任何崩溃          | 内部 issue 跟踪  |

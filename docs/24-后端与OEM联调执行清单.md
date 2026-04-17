# 24. 后端与 OEM 联调执行清单

本文档用于把当前“仓库内已完成”与“外部团队仍需配合”的边界拆成可执行事项。

## 1. 后端目录 API

### 1.1 需要后端提供

- 目录接口正式地址
- 鉴权方案或灰度头定义
- 返回字段说明与错误码说明
- 缓存策略：`ETag` / `Last-Modified` / `Cache-Control`
- 降级策略：空目录、部分字段缺失、非 200 返回时的约定

### 1.2 当前仓库已准备好

- `AppCatalogHttpClient`
- `AppCatalogHttpRequest`
- `AppCatalogHttpResponse`
- `AppCatalogCacheMetadataStore`
- `ResilientAppCatalogSource`
- `AppRemoteDataSource`

### 1.3 联调验收项

- 200 返回时目录可正常解析并写入缓存
- 304 返回时可直接复用缓存目录
- 鉴权头或灰度头可以通过环境配置注入
- 接口失败时能从缓存或资源目录回退
- 关键字段缺失时不会导致页面直接崩溃

## 2. OEM 车况信号

### 2.1 需要 OEM 提供

- 驻车状态来源
- 点火或电源状态来源
- 行车限制或前后台限制来源
- 广播 / Binder / SDK 的接入方式
- 信号抖动、延迟和默认值约定

### 2.2 当前仓库已准备好

- `PolicyRuntimeSignalProvider`
- `AndroidPolicyRuntimeSignalProvider`
- `VehicleStateSignalProvider`
- `StaticVehicleStateSignalProvider`
- `DefaultPolicyCenter`

### 2.3 联调验收项

- 驻车状态切换后页面提示能实时刷新
- 行车状态下安装和升级会被正确拦截
- 信号短暂抖动不会导致任务状态频繁闪烁
- OEM provider 异常时仍能回退到默认策略

## 3. 真实设备链路

### 3.1 需要验证的链路

- 下载发起、暂停、恢复、失败重试
- 安装确认页拉起与回调
- 安装会话中断后的恢复
- 升级链路串行执行与失败恢复

### 3.2 设备侧记录建议

- 车型 / ROM 版本 / 系统版本
- 下载网络条件
- 驻车 / 行车状态
- 失败时的系统弹窗、回调码和日志时间点
- 是否能稳定复现

## 4. 执行顺序

1. 先打通真实目录 API
2. 再接入 OEM 驻车/车况信号
3. 同步做真机链路验证
4. 最后补灰度、回滚、看板等运营能力

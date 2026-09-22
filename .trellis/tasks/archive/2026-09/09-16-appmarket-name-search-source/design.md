# 设计：按应用名搜索并导入 apk 的上游源

## 架构与边界

本次为**纯增量接入**，全部改动收敛在 `feature/appmarket` 模块，核心文件为
`service/AppMarketExternalService.kt`；数据模型（`App` / `AppVersion` / `DownloadSource`）、
Repository、VO、控制器与前端调用方式**均不变**。

新增两个上游客户端（手撸，沿用现有风格）：

| 上游 | 类型 | 接入方式 | source 标记 | sourceType |
|------|------|----------|-------------|------------|
| APKPure | 抓取（无官方 API） | OkHttp 抓搜索页 + 详情页，提取 XAPK 直链 | `APKPure` | `APKPURE` |
| Aptoide | 官方 API | OkHttp 调 Aptoide 官方搜索/详情 API | `Aptoide` | `APTOIDE` |

**搜索/查询优先级（在 `AppMarketExternalService` 内）**：
`本地词典 → F-Droid → IzzyOnDroid → APKPure → Aptoide → 应用宝`
（APKPure/Aptoide 排在应用宝之前，因为它们提供真实 apk 直链；应用宝仅详情页兜底）。

## 数据流与契约

### 配置（新增，沿用 `-D` 覆盖约定）
- `appmarket.apkpure.enabled`（默认 true）、`appmarket.apkpure.base`、`appmarket.apkpure.search`
  —— APKPure 详情/搜索基址（实现期校验实际地址）。
- `appmarket.aptoide.enabled`（默认 true）、`appmarket.aptoide.base`
  —— Aptoide 官方 API 基址（实现期校验，如 `https://api.aptoide.com/api/...`）。

### 超时与缓存（沿用现有约定）
- 新上游只读请求：复用 `extClient`（connect8/read10/write10/call10s，四类超时全显式覆盖）；
  APKPure 搜索页较大时可单独给 15s 客户端（仿 `izzyClient`）。
- 只读查询**不重试**（硬性超时后重试无意义）。
- APKPure 详情按包名内存缓存（TTL 30min，仿 `myappCache`）；搜索按关键词缓存（TTL 5min，仿 `searchCache`）。
- Aptoide 官方 API 响应较小，可加轻量缓存或直接请求。

### 解析锚点（实现期校验/微调）
- APKPure 直链形态参考：`https://d.apkpure.com/b/XAPK/?version=latest`（来自 kdroidFilter/AndroidAppStoreKit 的 `getApkPureApplicationInfo`）；
  详情页需提取 `title / version / versionCode / signature / downloadLink`。
  搜索页需解析候选列表（包名 + 名称 + 图标 + 简介）。
- Aptoide 官方 API：按 `package` / 关键词搜索，返回结构化 JSON（含 `file.path` 直链）。

### 接入点（仅新增分支，不改既有逻辑）
- `lookup(raw)`：在 Izzy 之后、应用宝之前，增加 `fetchApkPure(pkg)?.let { return toApkPurePreview(it) }`
  与 `fetchAptoide(pkg)?.let { return toAptoidePreview(it) }`。
- `searchByName(keyword)`：在 `searchIzzy` 之后增加 `searchApkPure(kw, lower)` 与 `searchAptoide(kw, lower)`
  （各自 try/catch 记录错误），进入既有 `packageName` 去重与截断。
- `importApp(raw, source)`：增加分支
  `source == "APKPure" -> importFromApkPure(pkgName)`、
  `source == "Aptoide" -> importFromAptoide(pkgName)`。
- `listSearchProviders()`：追加 `SearchProviderMeta("apkpure","APKPure",…,apkpureEnabled)` 与 `("aptoide","Aptoide",…,aptoideEnabled)`。
- `ExternalAppPreview` 已含 `apkUrl` / `source` 字段，**无需改 DTO**；`source` 取值新增 `APKPure` / `Aptoide`。

### upsert 导入语义（D3）
`importFromApkPure` / `importFromAptoide`：
1. `fetchXxx(pkgName)` 取元数据 + apkUrl；取不到抛 `IllegalArgumentException("…未找到包名")`。
2. 应用存在则复用、缺失字段补全（同 `importFromMyApp` 的 upsert 风格）；不存在则新建 `App`。
3. 按 `versionName` 判重追加 `AppVersion`（APKPure/Aptoide 有 versionCode，优先用 versionCode 判重）。
4. 按 `downloadUrl` 判重追加 `DownloadSource`（sourceType=APKPURE/APTOIDE，region 留空，note 注明来源）。
5. 复用 `collector.ensureStoreSources(app)` 补应用宝源（与现有体系一致）。
> 注：F-Droid/Izzy 维持「已存在即拒绝」语义不变；仅新主流源走 upsert，避免重复导入又能补充下载渠道。

## 重要权衡

- **XAPK vs APK（D5）**：APKPure 对部分应用返回 XAPK（拆分包）。下载源只存直链，
  不拆解；端侧能否安装 XAPK 属后续项。导入不因此阻断。
- **抓取脆弱性**：APKPure 无官方 API，页面结构变动会导致解析失效。应对：解析失败时优雅降级（null / 友好提示），
  不抛 5xx；监控日志告警；Aptoide 官方 API 作稳定兜底，保证「至少能搜到」。
- **合规**：沿用现有「尽力抓取、不托管二进制、失败即降级」姿势；APKPure/Aptoide 直链指向其 CDN，服务端不缓存二进制。

## 查询并行与分源独立超时（迭代补充：不同源分开算）

- **问题**：原 `searchByName` 串行等待各上游（F-Droid 10s + Izzy 15s + APKPure 10s + Aptoide 10s，最坏 ~45s），
  且任一源失败被 `errors.first()` 抛成笼统「请稍后重试」，单源慢即拖垮整体查询。
- **方案**：`searchByName` / `lookup` 改用**共享线程池并行**查询各上游（守护线程，池大小 8）；每个上游任务内部用
  `runCatching` 独立捕获异常（连接超时 / 解析失败都只记为该源失败，不扩散）。聚合时采用**统一截止时间（deadline）**：
  - 每个 future 只等待「剩余时间」= 截止时间 − 当前时间；剩余 ≤ 0 即 `cancel(true)` 跳过并记录「查询超时，已跳过」；
  - 整体响应时长严格受 `SEARCH_OVERALL_TIMEOUT_MS`（默认 10_000ms，略低于前端只读查询 15s 超时）约束，
    **不随源数量线性累积**（避免「上游响应过慢」导致整体查询超时）。
- **返回语义（「不同源分开算」）**：
  - 任一来源返回候选 → 聚合去重后直接返回，**不打断、不报超时**；
  - 全部源都无结果且存在失败源 → 抛出 `IllegalStateException("搜索上游暂时不可用：源A（原因）；源B（原因）…")`，
    附**各失败源精确信息**（由控制器转成友好提示，便于定位），不再笼统「请稍后重试」；
  - 全部源可达但确实无匹配 → 静默返回空列表。
- **可覆盖**：`-Dappmarket.search.timeout.ms=<ms>` 调整整体软上限（默认 10000）。
- **线程安全**：`searchExecutor` 为类字段常驻线程池；`SourceOutcome` 为不可变数据类，各任务结果经 `Future` 收集，
  不共享可变状态，无并发写入竞争。

## 兼容与回滚

- **无数据库 schema 变更**（`App`/`AppVersion`/`DownloadSource` 已满足）。
- 全部为新增分支 + 新增配置；任一流源可通过 `-D*.enabled=false` 即时关闭。
- 回滚：删除新增分支 / 关闭 enabled 开关即可，不影响 F-Droid/Izzy/应用宝既有链路。

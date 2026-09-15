# 需求：批量补全应用宝元数据

## 背景与问题

`09-15-cn-app-name-search` 已实现单应用「按名搜索 → 应用宝补全」（`importFromMyApp` 的 upsert 语义），
但它是**逐个手动**操作。seed 中的 41 个国内应用仍是占位数据：

- `versionName = "官方最新版"`、`versionCode = 0`、`sizeMb = 0`
- `iconUrl` 是 favicon 或空、无真实开发商 / 分类 / 简介
- 下载源只有「官网 + 应用宝详情页 URL」（`syncStoreSources` 已补，但那只是**下载源链接**，不是元数据）

逐个手动点导入补全 41 个应用很繁琐。需要一个**一键批量入口**：按包名把库内所有应用跑一遍应用宝，
把占位数据刷新成真实元数据。

## 已确认的技术事实（复用前序调研）

- `AppMarketExternalService.importFromMyApp(pkgName, existing)` 已是 upsert：
  - 应用不存在 → 新建；存在 → 只填空字段、仅替换 favicon/`.ico` 占位图标、按版本名追加版本（不丢原数据）。
  - 应用宝查不到（如 NewPipe / Signal 等开源应用）→ 抛 `IllegalArgumentException("应用宝未找到该包名：…")`。
- `fetchMyAppDetail` 有 30min 内存缓存 + `myappEnabled` 开关 + 四类超时（8/15/15/20s），失败返回 `null`。
- `应用宝` 不提供 `versionCode`、不提供 APK 直链 → 批量补全后版本仍是「版本名 + 大小」，无 versionCode。

## Goal

管理后台一个按钮，**一次性**把库内所有应用按包名跑应用宝补全真实元数据（版本名 / 大小 / 图标 / 简介 /
开发商 / 分类），返回清晰的统计（处理数 / 补全数 / 跳过数 / 失败数），幂等、可重复执行。

## 方案（与用户确认）

新增一个管理端批量接口，循环复用 `importFromMyApp` 的 upsert 逻辑：

1. 遍历 `appRepository.findAll()`；
2. 对每个应用按 `packageName` 调应用宝；
   - 应用宝**有**该应用 → upsert 补全（复用 `importFromMyApp`）；
   - 应用宝**没有**（开源应用 / 已下架）→ 跳过，不报错；
   - 个别异常 → 计入失败，不影响其余应用；
3. 汇总统计返回。

## In Scope

- 后端：`AppMarketExternalService.batchCompleteFromMyApp(): BatchCompleteResultVo`
  （复用 `importFromMyApp` 的 upsert；按结果分类计数）。
- `AppMarketAdminService` 接口 + `impl` 透传（`@CacheEvict` + `@Transactional`），
  与现有 `syncStoreSources` 完全对称。
- 管理端接口：`POST /api/v1/appmarket/admin/batch-complete-myapp` → `Response<BatchCompleteResultVo>`。
- 前端：管理端「应用导入」区新增「批量补全应用宝元数据」按钮，较长超时（120s），
  展示统计结果（处理 / 补全 / 跳过 / 失败）。
- 结果 DTO `BatchCompleteResultVo`（仿 `SyncStoreResultVo`）。

## Out of Scope

- **不**对 F-Droid / IzzyOnDroid 做批量补全（仅应用宝，与设计文档 §8 一致）。
- **不**改造 `importFromMyApp` 既有语义（保持单应用导入行为不变）。
- 首版**同步**执行（41 个请求顺序跑，利用 30min 缓存，重复执行很快）；
  异步任务队列 / 进度条为后续增强（见风险 3）。

## 验收标准

- **AC1** 点击「批量补全」后，41 个 seed 国内应用的 `versionName` 由「官方最新版」变为真实版本号、
  `sizeMb` 填实数、`iconUrl` 变为应用宝 CDN 图标、`developer`/`category`/`summary` 被补全。
- **AC2** 开源应用（NewPipe / Signal / VLC / Firefox）在应用宝查不到 → 计入「跳过」，不报错、不写脏数据。
- **AC3** 已通过单应用导入补全过的应用（如微信）再次批量跑 → 幂等：不产生重复版本、图标不被回退为占位。
- **AC4** 返回统计准确：`appsProcessed / appsUpdated / appsSkipped / appsFailed` 与实际情况一致。
- **AC5** 单个应用补全失败（网络抖动 / 解析异常）只计入 `appsFailed`，不影响其余应用继续处理。
- **AC6** `myappEnabled=false` 时批量接口直接返回「应用宝上游已禁用」友好提示，不发起请求。
- **AC7** 执行后管理端应用列表 / 详情立即可见新数据（缓存已清除）。

## 风险

1. **耗时**：41 个顺序请求，每个正常 1–3s，最坏受 20s call 超时影响。首版同步执行约 40–120s，
   需前端给足超时（120s）并展示「处理中」。缓解：30min 缓存使重复执行近乎瞬时。
2. **应用宝限流**：41 次快速请求可能触发风控。缓解：顺序执行（非并发）已有天然间隔；
   如确有风控，可在请求间加极小 sleep（实现时观察，暂不加）。
3. **同步阻塞**：超长请求会占用 admin 请求线程。缓解：首版可接受（手动触发、低频）；
   若需更强壮，后续改异步（后台任务 + 状态查询）。本任务不实现异步。
4. **数据覆盖顾虑**：`importFromMyApp` 只填空字段、仅替换 favicon 占位图标、按版本名去重追加，
   不会覆盖管理员人工维护的真实数据（如手动上传的图标、F-Droid 真实版本）。属安全 upsert。

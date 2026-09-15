# 实施计划：批量补全应用宝元数据

## 前置

- 构建环境：JDK 11（`export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home`）。
- 复用：本任务**不新增**补全逻辑，只复用 `AppMarketExternalService.importFromMyApp`。

## 改动文件清单

| 文件 | 改动 |
|---|---|
| `feature/appmarket/.../dto/AdminDtos.kt` | 新增 `BatchCompleteResultVo` |
| `feature/appmarket/.../service/AppMarketExternalService.kt` | 新增 `batchCompleteFromMyApp()`（约 +40 行） |
| `feature/appmarket/.../service/AppMarketAdminService.kt` | 接口新增 `batchCompleteFromMyApp()` |
| `feature/appmarket/.../service/impl/AppMarketAdminServiceImpl.kt` | 实现（透传 + `@CacheEvict`） |
| `feature/appmarket/.../controller/AppMarketAdminApiController.kt` | 新增 `POST /batch-complete-myapp` 端点 |
| `src/main/resources/static/.../appmarket-admin.js` (+ admin.html) | 新增「批量补全」按钮 + 120s 超时调用 |

## 执行步骤（T1–T7）

- **T1** `AdminDtos.kt` 新增 `BatchCompleteResultVo`（字段见 design §2）。
- **T2** `AppMarketExternalService` 新增 `batchCompleteFromMyApp()`：遍历 `findAll()`，
  try `importFromMyApp(pkg, app)` → 按异常 message 是否含「未找到」区分 skipped/failed；
  用 `versionRepository.countByAppId` 前后对比 + 图标是否为真实图标判定 `appsUpdated`。
  开关 `myappEnabled=false` 时直接返回空结果（AC6）。
- **T3** `AppMarketAdminService` 接口 + `impl`（`@CacheEvict(allEntries=true)` 透传）新增方法。
- **T4** 控制器新增 `POST /batch-complete-myapp`，`@LoggerManage(description="批量补全应用宝元数据")`。
- **T5** 前端 `appmarket-admin.js`：新增按钮，`$.ajax` `timeout:120000`，`.always` 复位 +
  展示 `处理/补全/跳过/失败` 统计与失败明细；admin.html 放置按钮。
- **T6** JDK11 构建 `./gradlew build -x test`，确认 BUILD SUCCESSFUL。
- **T7** H2 冒烟（见下）。

## 冒烟用例（T1–T7 / H2 8099）

- **T-a** 正常全量跑：对含 seed 41 应用 + 4 开源应用的库执行批量补全；
  断言 `appsProcessed = 45`，开源 4 个在 `appsSkipped`，国内 41 个 `appsUpdated` 显著 > 0。
- **T-b** 抽查一条 seed（如微信）：补全后 `versionName` 不再是「官方最新版」、`sizeMb>0`、
  `iconUrl` 以 `pp.myapp.com` 开头。
- **T-c** 幂等：立即再跑一次；微信等已补全应用 `appsUpdated=0`（无重复版本、图标不被回退）。
- **T-d** 开关降级：`-Dappmarket.myapp.enabled=false` 启动后调用接口，返回 `appsProcessed=0` 且无网络请求。
- **T-e** 单点失败隔离：临时把一个包名指向不可达（或断网模拟 1 个），断言该应用计入 `appsFailed`、
  其余仍正常 `appsUpdated`，整体不抛 500。
- **T-f** 缓存：第二次全量跑耗时远低于首次（验证 30min 缓存生效）。
- **T-g** 接口契约：响应为 `Response<BatchCompleteResultVo>`，`code=200`；前端按钮 120s 内拿到统计并展示。

## 回滚点

- 任意步骤失败：revert 对应提交即可；不触碰 `importFromMyApp` 既有路径。
- 紧急关闭：`-Dappmarket.myapp.enabled=false`，接口直接返回（AC6）。

## 注意

- `importFromMyApp` 内部已 `@Transactional`，批量循环里每个应用各自一个事务；
  若希望整体一个事务，可在 `impl` 方法上加 `@Transactional`（默认不加，逐应用独立提交更稳妥）。
- 前端 120s 超时是为了覆盖最坏 41×20s；正常 1–3s/个，实际约 40–120s。
- 监听器：`@LoggerManage` 会序列化方法入参，本接口无入参，安全。

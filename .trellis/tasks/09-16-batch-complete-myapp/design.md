# 技术设计：批量补全应用宝元数据

## 1. 总体数据流

```
管理端「批量补全应用宝元数据」按钮
   │  POST /api/v1/appmarket/admin/batch-complete-myapp
   ▼
AppMarketAdminApiController
   │  @LoggerManage(description="批量补全应用宝元数据")
   ▼
AppMarketAdminServiceImpl.batchCompleteFromMyApp()   // @CacheEvict + @Transactional
   │
   ▼
AppMarketExternalService.batchCompleteFromMyApp(): BatchCompleteResultVo
   │  遍历 appRepository.findAll()
   │  对每个 app：
   │    try { importFromMyApp(app.packageName, app)  → 应用宝有数据，upsert }
   │    catch (IllegalArgumentException "应用宝未找到…") → 跳过（开源应用）
   │    catch (其他异常) → 失败（计入 appsFailed，不影响其余）
   ▼
返回 BatchCompleteResultVo(appsProcessed / appsUpdated / appsSkipped / appsFailed)
```

**关键**：不新增「补全核心逻辑」，完全复用现有 `private importFromMyApp(pkgName, existing)`，
只在上面套一层「遍历 + 分类计数」。单应用导入行为零变化。

## 2. 结果 DTO

新增 `BatchCompleteResultVo`（放 `dto/AdminDtos.kt`，紧挨 `SyncStoreResultVo`）：

```kotlin
data class BatchCompleteResultVo(
    val appsProcessed: Int = 0,   // 遍历到的应用总数
    val appsUpdated: Int = 0,     // 应用宝有数据且产生了真实变更（新增版本 或 占位图标→真实图标）
    val appsSkipped: Int = 0,     // 应用宝查不到（开源应用 / 已下架）
    val appsFailed: Int = 0,      // 其他异常（网络抖动 / 解析失败）
    val messages: List<String> = emptyList()  // 失败项的可读信息（包名 + 原因），最多保留前 20 条
)
```

## 3. 变更点

### 3.1 AppMarketExternalService（核心，约 +40 行）

新增 public 方法：

```kotlin
fun batchCompleteFromMyApp(): BatchCompleteResultVo {
    if (!myappEnabled) return BatchCompleteResultVo(appsProcessed = 0)  // AC6：开关关闭直接返回
    var processed = 0; var updated = 0; var skipped = 0; var failed = 0
    val msgs = mutableListOf<String>()
    for (app in appRepository.findAll()) {
        processed++
        try {
            val beforeVer = versionRepository.countByAppId(app.id)
            val beforeIcon = app.iconUrl
            importFromMyApp(app.packageName, app)   // 复用既有 upsert
            // 判定是否产生了真实变更：新增了版本，或占位图标被替换
            val afterVer = versionRepository.countByAppId(app.id)
            val after = appRepository.findById(app.id).orElse(null)
            val changed = afterVer > beforeVer ||
                (after != null && after.iconUrl != beforeIcon && isRealIcon(after.iconUrl))
            if (changed) updated++ else skipped++ // 应用宝有数据但无变化 → 计入 skipped（无副作用）
        } catch (e: IllegalArgumentException) {
            // importFromMyApp 在「应用宝未找到」时抛此异常（message 含「未找到」）
            if (e.message?.contains("未找到") == true) skipped++
            else { failed++; if (msgs.size < 20) msgs += "${app.packageName}: ${e.message}" }
        } catch (e: Exception) {
            failed++; if (msgs.size < 20) msgs += "${app.packageName}: ${e.message}"
        }
    }
    return BatchCompleteResultVo(processed, updated, skipped, failed, msgs)
}
```

> `isRealIcon(url)` 为小工具：非 null/非 blank 且不含 `favicon`、不以 `.ico` 结尾即视为真实图标
> （与 `importFromMyApp` 里「占位图标」判定保持一致）。

**为什么不把「应用宝有数据但无变化」算作 updated**：seed 已补全过的应用再次跑，结果应显示「跳过」而非「又更新了一次」，
更符合管理员的预期，也避免 `appsUpdated` 虚高。

### 3.2 AppMarketAdminService 接口 + impl

接口（`service/AppMarketAdminService.kt`）新增：
```kotlin
fun batchCompleteFromMyApp(): BatchCompleteResultVo
```
实现（`service/impl/AppMarketAdminServiceImpl.kt`），与 `syncStoreSources` 对称：
```kotlin
@CacheEvict(allEntries = true)
override fun batchCompleteFromMyApp(): BatchCompleteResultVo {
    return externalService.batchCompleteFromMyApp()
}
```
（`importFromMyApp` 内部已是 `@Transactional`，此处不必再包 `@Transactional`；`@CacheEvict` 保证门户/列表立即可见。）

### 3.3 控制器（AppMarketAdminApiController.kt）

在 `/sync-store-sources` 附近新增：
```kotlin
@PostMapping("/batch-complete-myapp")
@LoggerManage(description = "批量补全应用宝元数据")
fun batchCompleteFromMyApp(): Response<BatchCompleteResultVo> {
    return Response(adminService.batchCompleteFromMyApp())
}
```
- 无请求体 / 无敏感参数，`@LoggerManage` 安全（不会序列化大对象）。
- 统一 `Response` 信封；正常返回 `code=200, data=BatchCompleteResultVo`。

### 3.4 前端（src/main/resources/static/.../appmarket-admin.js + admin.html）

- 在「外部导入 / 从 APK 导入」按钮区旁新增「批量补全应用宝元数据」按钮。
- 点击 → `$.ajax` `POST /api/v1/appmarket/admin/batch-complete-myapp`，
  `timeout: 120000`（120s，覆盖 41 个顺序请求最坏耗时，见 PRD 风险 1），
  `.always` 复位按钮，成功后在结果区展示
  `处理 X / 补全 Y / 跳过 Z / 失败 W` 与失败明细（如有）。
- 执行中显示「批量补全中…（可能需 1~2 分钟）」禁用按钮，避免重复点击。

## 4. 复用与保持不变

| 既有能力 | 是否改动 | 说明 |
|---|---|---|
| `importFromMyApp(pkgName, existing)` | **不改** | upsert 核心逻辑完全复用 |
| `fetchMyAppDetail` / 30min 缓存 / 超时 / 开关 | **不改** | 批量跑天然受益 |
| `App.iconUrl` 为 TEXT | **不改** | 上个子任务已扩 |
| `syncStoreSources` | **不改** | 它只补下载源 URL，本任务补元数据，二者互补 |

## 5. 幂等性验证（对应 AC3）

- 已补全应用（如微信）再跑：版本名已存在 → `importFromMyApp` 不追加版本；图标已是 CDN（非占位）→ 不替换。
  `appsUpdated=0`，`appsSkipped` 计入（无变更）。无重复版本、图标不回退。
- 已下架包名 / 开源应用：`fetchMyAppDetail` 返回 null → 抛「未找到」→ `appsSkipped`。

## 6. 回滚

改动集中在：新增 `BatchCompleteResultVo` + `batchCompleteFromMyApp` 方法（service/external）+ 1 个控制器端点 + 前端按钮。
不触碰 `importFromMyApp` 既有路径，不新增表 / 字段。回滚即 revert 本次提交；
或用 `-Dappmarket.myapp.enabled=false` 关闭上游后，该接口直接返回（AC6）。

## 7. 待定 / 后续

- **异步化**：若 41 个请求耗时/阻塞成为问题，改为后台任务 + 进度查询（不在本任务）。
- **增量范围**：首版对全库跑；若应用量变大，可加 `?onlyPlaceholder=true` 只刷占位数据应用。

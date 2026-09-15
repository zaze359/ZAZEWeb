# Design — 从 APK 解析导入国内应用

> Task: `09-15-cn-store-metadata` · 依赖 `prd.md`（需求与验收以此为准）

## 架构与边界

```
[浏览器]                                   [服务端]
本地 APK 文件
  │ File/Blob
  ▼
app-info-parser (vendor JS)
  │ 解析 binary XML manifest
  ▼
元数据 {package, versionName, versionCode, label, icon}
  │ 填充表单（可人工校正）
  │
  │ POST /admin/import-from-apk  ───────▶  校验 → 判重 → 建应用/加版本
  │        （仅元数据 JSON）                → ensureStoreSources 补应用宝
  ▼                                        ▼
预览图标                              返回 AppVo + 状态 msg
```

**边界原则**

- APK 二进制**永不离开浏览器**：服务端只接收解析后的元数据 JSON，不接收文件、不落盘。
  沿用父任务「不做自有二进制存储」约束。
- 解析逻辑全部在前端，服务端不引入任何 APK 解析依赖（原候选 `net.dongliu:apk-parser` 因此不需要）。
- 新增链路与现有 F-Droid / IzzyOnDroid 导入链路**并行且互不干扰**：
  不修改 `AppMarketExternalService`，新增独立的 controller 方法与 service 方法。

## 依赖引入

- 库：`app-info-parser`（MIT）。从 npm 取浏览器构建产物，放入
  `src/main/resources/static/vendor/app-info-parser/app-info-parser.js`。
- 引入位置：管理后台模板 `src/main/resources/templates/appmarket/admin.html`
  （与 bootstrap / jquery 一致走本地 vendor，不依赖外网 CDN）。
- 全局对象：`window.AppInfoParser`；用法 `new AppInfoParser(file).parse()`。
- 若 npm 产物获取不便，退路是直接内联其 dist 文件（MIT 允许再分发，需保留 LICENSE 头文件）。

## 数据流与契约

### 1. 前端解析（新增 `static/js/appmarket-admin.js` 逻辑）

```js
// 伪代码：选择文件后解析
var file = $('#apkFile')[0].files[0];
if (!file) return;
if (file.size > MAX_APK_BYTES) { /* 提示过大并中止 */ }
setLoading(true);
new window.AppInfoParser(file).parse().then(function (result) {
    // result: { package, versionName, versionCode, application:{label, icon}, icon }
    fillForm({
        packageName: result.package,
        name:        result.application && result.application.label,
        versionName: result.versionName,
        versionCode: result.versionCode,
        iconDataUri: result.icon          // data:image/png;base64,...
    });
}).catch(function (e) {
    showError('APK 解析失败：文件可能损坏或经过加固');
}).then(function () { setLoading(false); });
```

要点：
- 解析可能耗时数秒（大 APK），必须有加载态与超时兜底。
- `result.package` 为空视为解析失败（包名是后续判重与推导下载源的前提，缺失直接阻断）。
- 图标过大（建议阈值 100 KB）时仅预览不提交，避免撑大数据库。

### 2. 提交接口（新增）

```
POST /api/v1/appmarket/admin/import-from-apk
Content-Type: application/json

请求体 ApkImportRequest:
{
  "packageName":  "com.tencent.mobileqq",   // 必填
  "name":         "QQ",                      // 可选
  "versionName":  "8.9.88",                  // 可选
  "versionCode":  1234,                      // 可选
  "iconDataUri":  "data:image/png;base64,...", // 可选
  "sizeMb":       120                         // 可选，由前端 file.size 换算
}

响应 Response<AppVo?>（沿用统一信封，code 恒 200）:
- 成功新建：data = AppVo, msg = "已导入：QQ"
- 追加版本：data = AppVo, msg = "已新增版本 v8.9.88"
- 已是最新：data = AppVo, msg = "已是最新，无需重复导入"
- 失败：    data = null, msg = 具体原因（包名缺失 / 解析数据不完整 / …）
```

### 3. 服务端处理（新增 service 方法）

```
importFromApk(req):
  1. 校验 packageName 非空，否则 IllegalArgumentException("缺少包名，无法导入")
  2. app = findByPackageName(pkg)
     - 不存在 → 创建 App（name / iconUrl=dataUri / packageName）
  3. 版本处理（仅当提供了 versionCode 或 versionName）:
     - 已有相同 versionCode（或 versionCode 缺失时按 versionName 比对）→ 返回「已是最新」
     - 否则 → 新增 AppVersion(appId, versionName, versionCode, sizeMb)
  4. ensureStoreSources(app)  // 幂等补应用宝详情页，已有则跳过
  5. @CacheEvict(allEntries = true)  // 与现有导入一致，保证门户/后台立即可见
  6. return app.asVo(1)
```

**去重口径**：以 `versionCode` 为主（数字、可靠）；`versionCode` 缺失时退化为按 `versionName` 比对。
版本记录是以 `AppVersion` 表按 `appId` 查询后内存比对（现有代码即用 `versionRepository` + 内存判断，保持一致）。

### 4. 图标字段改造

`App.kt`：

```kotlin
@Column(columnDefinition = "TEXT")
val iconUrl: String? = null,
```

**迁移（关键点）**：`spring.jpa.hibernate.ddl-auto=update` **不会修改已有列的类型**，
仅新增列。因此必须提供并执行手动 DDL 脚本，放 `src/main/resources/static/sql/`（该目录已存在）：

```sql
-- MySQL
ALTER TABLE appmarket_app MODIFY COLUMN icon_url TEXT;
-- H2（本地冒烟）
ALTER TABLE appmarket_app ALTER COLUMN icon_url TEXT;
```

兼容性：varchar → TEXT 只放宽不收紧，现有 favicon URL 数据可正常读取，无数据迁移风险。

## 兼容性

- 现有 `/external-lookup`、`/external-search`、`/external-import`、`/external-sources` 不变。
- 现有 seed 数据不受影响；本功能可用于**修正**这批占位版本（追加真实版本）。
- 门户侧 `appmarket.js` 渲染图标无需改动（`<img src>` 天然支持 data URI）；
  仅在 `onerror` 时回退占位图标的现有逻辑保持不变。
- 前端仅在管理后台页面引入解析库，门户页不加载，无额外流量成本。

## 重要权衡

| 决策 | 选择 | 取舍 |
|------|------|------|
| 解析位置 | 前端 | 省去服务端下载几十~几百 MB APK；代价是必须本地有文件，无法「填网址自动导入」 |
| 元数据来源 | 解析 APK 而非商店抓取 | 数据最准、零抓取合规风险；代价是无「按名搜索发现」能力 |
| 包名已存在 | 追加新版本 | 能修正 41 个 seed 的占位版本；需额外做版本去重 |
| 图标 | data URI 入库 | 门户图标质量提升、不依赖外链；代价是字段扩 TEXT、单应用增几 KB~几十 KB |
| 依赖引入 | 本地 vendor 而非 CDN | 内网/离线可用；代价是需把 dist 文件纳入仓库 |

## 运维与回滚

- 新接口与旧导入链路物理隔离，出问题可单独禁用入口按钮，不影响现有功能。
- `iconUrl` 扩列为放宽式变更，`TEXT` 若需回滚为 varchar 可能截断长 data URI——
  回滚前应先清空 data URI 值（保留 URL 型图标），需列入回滚步骤。
- 若 `app-info-parser` 对加固 APK 解析失败率高，退路是：表单仍支持纯手工填写，
  解析仅为「自动填充」增强，失败不影响人工录入路径。
- 建议后续为解析失败埋点（包名缺失、解析异常计数），便于评估该库的实际覆盖率。

## 风险

1. **大 APK 卡浏览器**：解析数百 MB 文件可能长时间占用主线程。
   缓解：大小上限（建议 200 MB）+ 加载态 + 超时提示。若后续成为瓶颈，
   可考虑 Web Worker 中解析（当前先不做）。
2. **加固 / 非标准 APK 解析失败**：`app-info-parser` 对部分加固包可能拿不到 manifest。
   缓解：catch 后给出明确错误，允许人工填写。
3. **data URI 体积**：高分辨率图标 base64 可能偏大。缓解：100 KB 阈值，超限不入库仅预览。
4. **依赖体积**：`app-info-parser` dist 约数百 KB，仅在管理后台加载，可接受。

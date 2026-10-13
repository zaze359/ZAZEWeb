# 技术设计：服务端 bsdiff 差分包生成与合并

> 任务：`10-10-bsdiff-incremental-update` ｜ 需求见同目录 `prd.md`；执行计划见 `implement.md`。
> 范围已确认：**纯服务端 Kotlin**，Android 客户端不在本仓库。两条能力 `bsdiff`（生成）与 `bspatch`（合并/自校验）均在 `feature:appmarket` 内实现。

## 1. 架构与边界

```
 ┌──────────────────────────── 管理端 / 下发调用 ────────────────────────────┐
 │  POST /admin/incremental-update/diff?oldV=&newV=        → 元数据(大小/比/哈希/缓存命中)
 │  GET  /admin/incremental-update/patches/{key}/download → patch 字节流
 │  POST /admin/incremental-update/verify?oldV=&newV=     → 自校验(pass+三哈希)
 │  GET  /admin/incremental-update/patches                → 已缓存 patch 列表
 │  GET  /api/v1/appmarket/incremental-patch?appId&from&to → 下发契约(二进制流 + X-*-Sha256 头)
 └───────────────────────────────────┬──────────────────────────────────────┘
                                      │
                                      ▼
        ┌──────────── IncrementalUpdateService（编排） ────────────┐
        │  1) 解析 (oldVersionId, newVersionId) → AppVersion + DownloadSource
        │  2) ApkCacheStore: 按 downloadUrl 拉取 APK 字节 → 本地临时缓存(带 TTL)
        │  3) 体积护栏: old/new > maxApkMb → 抛 PatchUnavailable(走降级)
        │  4) PatchCacheStore: key=(oldV,newV) 命中? → 直接返回; 否则:
        │  5) BsdiffService.diff(old, new) → patch        (限时 ExecutorService)
        │  6) BsdiffService.patch(old, patch) → tempRecon  ← 服务端自校验
        │  7) SHA-256(recon) == SHA-256(new)? 否 → 丢弃+报错
        │  8) 写 PatchCacheStore(patch + sidecar.json) → 返回元数据
        └───────────────────────────────┬──────────────────────────────┘
                                        │
            ┌───────────────┴───────────────┐
            ▼                               ▼
   BsdiffService                  FileSystemCacheStore(s)
   (jbsdiff 封装)                 - ApkCacheStore   : data/incremental/apk/<sha>.apk  (TTL)
   - diff(old,new→patch)          - PatchCacheStore : data/incremental/patches/<oldV>_<newV>.patch
   - patch(old,patch→new)                            + <oldV>_<newV>.json(sizes/hashes/createdAt)
   - verifyRoundTrip(old,new)
```

- **边界**：本任务**不新增任何实体/表**，差分包与临时 APK 全部落文件系统（可配置根 `data/incremental/`），
  满足 prd R10「零 DB schema 改动」。APK 字节**不长期落盘**（diff 用，TTL 清理）。
- **复用**：`AppVersionRepository` / `DownloadSourceRepository`（已有）、collector 的 `extClient` + `FileUtil`
  （拉取下载源字节）、`core/common` 的 `Response<T>` 信封与异常约定、`AuthInterceptor`（既有鉴权，不改动）。

## 2. 模块与文件清单（新增，均在 `feature:appmarket` 内）

| 文件 | 职责 |
|------|------|
| `.../appmarket/incremental/BsdiffService.kt` | jbsdiff 封装：`diff` / `patch` / `verifyRoundTrip`；强制 bzip2；失败时抛 `BsdiffException` |
| `.../appmarket/incremental/ApkCacheStore.kt` | 按 `downloadUrl` 拉取 APK 到 `data/incremental/apk/<sha256(url)>.apk`，带 TTL 与并发去重 |
| `.../appmarket/incremental/PatchCacheStore.kt` | patch 文件落盘 + sidecar JSON 元数据；`getOrCompute(key, generator)` 缓存语义；`list()` / `exists()` |
| `.../appmarket/incremental/IncrementalUpdateService.kt` | 编排（§1 流程）；体积护栏 + 超时 + 自校验门禁；对外暴露 `diff` / `fetchPatch` / `verify` / `listPatches` |
| `.../appmarket/incremental/IncrementalUpdateAdminController.kt` | `/admin/incremental-update/**` 四个管理端接口 |
| `.../appmarket/incremental/IncrementalPatchApiController.kt` | `/api/v1/appmarket/incremental-patch` 下发契约接口 |
| `.../appmarket/incremental/dto/IncrementalDtos.kt` | `PatchMetaVo`（大小/压缩比/哈希/缓存命中）、`PatchListVo`、`PatchUnavailableVo` 等 |
| `.../appmarket/incremental/exception/IncrementalExceptions.kt` | `PatchUnavailableException`（降级）、`BsdiffException`（算法失败）、`ApkFetchException` |
| `.../appmarket/incremental/config/IncrementalProperties.kt` | `@ConfigurationProperties("incremental")`：`patchDir` / `apkCacheDir` / `maxApkMb` / `diffTimeoutSec` / `apkTtlMin` |
| `build.gradle.kts`（feature:appmarket） | 新增依赖 `io.sigpipe:jbsdiff:1.0` 与 `org.apache.commons:commons-compress:<pinned>`（显式版本） |

**不改动**：`AuthInterceptor.kt`、`AppVersion.kt`、`DownloadSource.kt`、`AppMarketAdminApiController.kt`（新增独立控制器，不复用其路由）。

## 3. 数据模型与契约（冻结）

```kotlin
// BsdiffService 对外契约（纯文件操作，无 Spring 依赖）
object BsdiffService {
    fun diff(old: Path, new: Path, patch: Path)           // 生成差分包；内部强制 bzip2
    fun patch(old: Path, patch: Path, out: Path)          // 合并还原；out = 还原出的新包
    fun verifyRoundTrip(old: Path, new: Path): RoundTripResult
}
data class RoundTripResult(val pass: Boolean, val newSha256: String, val reconSha256: String)

// PatchCacheStore 落盘 sidecar 结构（<oldV>_<newV>.json）
data class PatchSidecar(
    val oldVersionId: Long, val newVersionId: Long,
    val oldSha256: String, val newSha256: String,
    val oldSize: Long, val newSize: Long, val patchSize: Long,
    val ratio: Double,                 // patchSize / newSize
    val createdAt: Long
)

// 下发/管理接口信封
data class PatchMetaVo(
    val key: String,                   // "<oldV>_<newV>"
    val oldVersionId: Long, val newVersionId: Long,
    val oldSha256: String, val newSha256: String,
    val oldSize: Long, val newSize: Long, val patchSize: Long,
    val ratio: Double, val cached: Boolean
)
```

- **patch 文件名**：`<oldV>_<newV>.patch`；sidecar：同名 `.json`。key 即 `<oldV>_<newV>`。
- **下发响应头**：`Content-Type: application/octet-stream`、`Content-Disposition: attachment; filename="<oldV>_<newV>.patch"`、
  `X-Old-Sha256`、`X-New-Sha256`、`X-Patch-Size`。
- **降级信封**（R6）：`Response(200, null, "暂不支持增量更新，请下载完整 APK")` 或
  `Response(200, PatchUnavailableVo(reason), ...)`，HTTP 状态恒 200，不抛 500。

## 4. 关键算法与契约

### 4.1 jbsdiff 封装（库兼容性硬门槛）

- 依赖：`io.sigpipe:jbsdiff:1.0`（Apache-2.0，Maven Central）。其 API：`BSDiff.diff` / `BSPatch.patch`，
  压缩算法由系统属性 `jbsdiff.compressor` 控制，**默认 bzip2**。
- **强制 bzip2**：在调用 diff 前设定 `System.setProperty("jbsdiff.compressor", "bzip2")`（或确保进程级默认），
  **禁止** gz/xz/pack200——否则未来客户端用标准 C `bspatch`（仅认 bzip2）将无法还原（prd R8）。
- **JDK 11 加载验证（实现门禁，AC10）**：jbsdiff 近期有「Bump JDK version」改动，须在 H2 冒烟（JDK 11）下
  实际加载并完成一次 diff→patch 闭环；若报 `UnsupportedClassVersionError`（字节码 > 55），
  立即切换兜底：
  1. `com.github.joelcho:bsdiff-java`（v4.3 移植，jitpack 引入，NIO 省内存，但作者声明「不保证与 C 版完全兼容」）
     需自行在封装层做 R3 自校验兜底；或
  2. 本地 shade 一个 JDK11 目标构建的 jbsdiff 作为 vendored jar。
  **无论用哪个库，R3 的服务端自校验都是最后防线**，保证下发的 patch 一定可逆。

### 4.2 服务端自校验（prd R3，核心）

每次 `diff` 生成 patch 后，**立即**同进程 `patch(old, patch, recon)` 还原，比较 `SHA-256(recon)` 与 `SHA-256(new)`：
- 相等 → 允许写入 `PatchCacheStore`，返回元数据。
- 不等 → 删除临时 patch，**不缓存、不下发**，向上抛 `BsdiffException("round-trip mismatch")` → 接口返回降级信封。
- 哈希用 `MessageDigest.getInstance("SHA-256")`，对文件流式计算（不整包读内存）。

### 4.3 APK 拉取与临时缓存（prd R5）

`ApkCacheStore.resolve(downloadUrl: String): Path`：
- key = `SHA-256(downloadUrl)`；命中本地且未过期（`<apkTtlMin>`）→ 直接返回。
- 未命中 → 用 collector 既有 `extClient`（`OkHttp`，已配置代理/超时）拉取字节写盘；
  **并发同 key 去重**（同进程 `ConcurrentHashMap<String, CompletableFuture<Path>>` 防惊群），避免重复下载。
- TTL 清理：启动期或写盘后惰性清理过期 `<apkTtlMin>` 文件；首版不引入定时任务，写时顺带扫过期。
- 拉取失败 → `ApkFetchException` → 接口降级。

### 4.4 体积护栏 + 超时（prd R9，AC6/AC7）

- **体积护栏**：`oldSize` 或 `newSize` > `maxApkMb`（默认 200，系统属性 `-Dincremental.maxApkMb` 可配）→
  直接抛 `PatchUnavailableException("apk too large")` → 降级信封；**绝不进入 diff**（防 OOM）。
  size 由 `AppVersion.sizeMb` 预判，无法判断时以本地文件实际大小判断。
- **超时**：`diff` 在独立 `ExecutorService`（单线程/`Callable`）中执行，
  `future.get(diffTimeoutSec, SECONDS)`（默认 300，`-Dincremental.diffTimeoutSec` 可配）；
  超时 → `future.cancel(true)` + 抛 `PatchUnavailableException("diff timeout")` → 降级信封；请求线程不挂起。
- **内存提示**：bsdiff 峰值 `max(17n, 9n+m)`。200MB 包约需 3.4GB+；部署机需足够堆（文档注明 `-Xmx` 建议），
  且体积护栏是第一道保险。

### 4.5 缓存语义（prd R4，AC3）

`PatchCacheStore.getOrCompute(key, generator)`：
- 文件存在且 sidecar 完整 → 返回 `cached=true`（不调用 generator，无重复 diff）。
- 否则调用 `generator()`（即 §1 的第 5~8 步）生成并写盘，返回 `cached=false`。
- `list()` 扫 `patches/` 目录返回 `PatchListVo`（key、ratio、大小、createdAt）。

## 5. 接口设计

### 5.1 管理端（`/admin/incremental-update/**`，沿用 ADMIN 鉴权）

| 方法 & 路径 | 入参 | 返回 |
|------|------|------|
| `POST /admin/incremental-update/diff` | `oldVersionId`, `newVersionId` | `Response(PatchMetaVo)` |
| `GET  /admin/incremental-update/patches/{key}/download` | path `key` | `ResponseEntity<Resource>`（patch 字节流） |
| `POST /admin/incremental-update/verify` | `oldVersionId`, `newVersionId` | `Response(VerifyVo{pass,oldSha256,newSha256,reconSha256,patchSize})` |
| `GET  /admin/incremental-update/patches` | 无 | `Response(List<PatchListVo>)` |

### 5.2 下发契约（`/api/v1/appmarket/incremental-patch`，沿用登录鉴权）

- `GET /api/v1/appmarket/incremental-patch?appId=&fromVersionId=&toVersionId=`
- 成功：`200` + `application/octet-stream` 字节流 + 头 `X-Old-Sha256` / `X-New-Sha256` / `X-Patch-Size`。
- 降级：体积超阈 / 上游不可达 / 自校验失败 / 超时 → `Response(200, null, "请下载完整 APK")`（恒 200，非 500）。
- 该接口是**未来客户端**的契约；本任务不实现客户端消费逻辑，但种子数据/文档需说明客户端应：
  校验已装旧包 SHA-256 == `X-Old-Sha256`、下载 patch、用标准 `bspatch` 还原、校验还原包 SHA-256 == `X-New-Sha256`、再安装。

### 5.3 鉴权

- 管理端挂载在 `/admin/**` → `AuthInterceptor` 的 `ADMIN_PATTERNS` 已覆盖，**零改动**。
- 下发接口挂载在 `/api/v1/appmarket/**` → 沿用既有登录拦截（与 `AppMarketApiController` 同待遇），**零改动**。
- **不新增任何鉴权常量、不改 `AuthInterceptor.kt`**（prd AC9）。

## 6. 兼容性与降级

| 场景 | 行为 |
|------|------|
| 任一 APK > `maxApkMb` | 跳过 diff，`PatchUnavailableException` → 降级信封（AC6） |
| 上游 `downloadUrl` 不可达 / 拉取失败 | `ApkFetchException` → 降级信封（AC5） |
| diff 超时 / 自校验失败 | 丢弃 patch，降级信封（AC7 / R3） |
| 库在 JDK 11 不可加载 | 实现阶段切换兜底库（§4.1），自校验仍兜底 |
| 客户端未装旧包 / 旧包哈希不匹 | 仓库外问题；下发头已带 `X-Old-Sha256` 供客户端自行判断（风险记录，prd Out of Scope） |

## 7. 权衡与取舍

1. **缓存介质选文件系统而非 DB**：满足「零 schema 改动」且 patch 是二进制大对象，文件系统更自然；代价是需自己管 TTL/清理。
2. **在 `feature:appmarket` 内加包而非新建模块**：避免与 appmarket 实体/仓库的循环依赖与 Gradle 接线成本；代价是模块边界略松。
3. **APK 取自 `DownloadSource` 而非落盘整包**：契合「按需实时 + 不长期存整包」，复用既有 collector；代价是每次冷请求需先下载 APK（受 `ApkCacheStore` TTL 缓解）。
4. **bzip2 强制**：牺牲 gz 可能更小的体积，换取与标准 C 客户端 `bspatch` 的兼容（prd R8 硬约束）。
5. **服务端自校验作为正确性防线**：即使库与 C 版存在细微不兼容，也能在分发前拦下坏 patch。
6. **体积护栏优先于「尽力生成」**：大包实时 diff 内存风险高，宁可降级为整包下载。

## 8. 回滚

- 全部改动 = `feature/appmarket` 内**新增包** + `build.gradle.kts` **两行依赖** + `application.properties` 可选配置项。
- **无数据库/实体副作用**（缓存是文件系统，非 JPA）。
- 回滚 = 删除新增包、还原 `build.gradle.kts`、`rm -rf data/incremental/`；不影响任何既有 appmarket 业务数据或接口。
- 已生成的 patch 文件无害，删除即彻底清理。

## 10. 外部 APK 来源直链化（应用宝修复 + 新增 Uptodown / APKMirror / 通用 HTML 爬虫）

> 背景：本任务 bsdiff 的 APK 取自 `DownloadSource.downloadUrl`（prd R5）。但既有「应用宝」上游把
> `https://sj.qq.com/appdetail/<pkg>`（**详情页链接**，非 APK）当作 downloadUrl 落库，导致「应用发现」
> 跳转的是引导安装应用宝 App 的网页、且 bsdiff 会拉到 HTML。参照开源项目 **Obtainium**（`lib/app_sources/`）
> 的多源机制，修正并扩充为**真实 APK 直链**来源。

### 10.1 根因与修正原则

- **根因**：应用宝详情页（`sj.qq.com/appdetail`）与「按名搜索」接口均**不返回 APK 直链**
  （`apk_url`/`download_url` 恒空），旧实现只能落详情页链接。
- **修正原则**：`DownloadSource.downloadUrl` 必须是真实 APK（`.apk`/容器）直链；**详情页链接仅作 `officialUrl`**，
  绝不作为 downloadUrl（否则 bsdiff 拉到网页=坏数据）。
- **Obtainium 已验证可用的真实直链源**：`a.app.sj.qq.com`（应用宝 APK 接口）、`APKPure`、`Aptoide`、
  `F-Droid`/`IzzyOnDroid`、`Uptodown`（带 HMAC 握手）。`APKMirror` 在 Obtainium 中 `enforceTrackOnly=true`、
  `apkUrls=[]`——**官方禁止直接下载（PR #44）**，只能给版本元数据，**不能直接出包**。

### 10.2 应用宝 APK 直链（核心修复，移植 Obtainium `tencent.dart`）

- 新增 `fetchMyAppApk(pkgName)`：请求 `https://a.app.sj.qq.com/o/simple.jsp?pkgname=<pkg>`
  （`followRedirects=false`，拿 `200` 的 `window.systemData=...` 文本），解析 `appDetail.apkUrl64 ?: apkUrl`
  （CDN 直链）+ `versionName`。**无需鉴权、可服务端抓取**（与 Obtainium 一致）。
- `importFromMyApp` 重构：先 `fetchMyAppDetail`（取元数据/图标/分类），再 `fetchMyAppApk`（取真实 APK）；
  用真实 APK 构造 `UpstreamAppDetail.downloadUrl` 走 `upsertImport` 写 **MYAPP 下载源**；详情页仅赋 `officialUrl`。
- `toMyAppPreview`：补全 `apkUrl`（来自 `fetchMyAppApk`），让前端「应用发现」直接拿到 APK 而非网页。
- **移除/回收** `AppMarketCollector.ensureStoreSources` 里把 `sj.qq.com/appdetail/<pkg>` 当 MYAPP 下载源的逻辑
  （它离线推导、无 APK，是污染 bsdiff 的元凶）；改为：仅当 `importFromMyApp` 真正取到 APK 时才写 MYAPP 下载源。
- **`appmarket_seed.json`**：现有 38+ 条 MYAPP 种子 `downloadUrl` 均为详情页链接 → 改为 `officialUrl`，
  或删除该 MYAPP 下载源行（保留 F-Droid/APKPure/Aptoide 等真实直链源）。

### 10.3 Uptodown 真实 APK（移植 Obtainium `uptodown.dart`）

- 按包名：`https://<pkg>.en.uptodown.com/android/download` 页面解析 `div.version`、`#technical-information`
  得 `package name` 与 `#detail-download-button` 的 `data-app-id`/`data-file-id` →
  HMAC-SHA256 握手（`/eapi/auth/token`，硬编码 `_hmacKey`，产出 JWT）→
  `GET /eapi/apps/<appId>/file/<fileId>/downloadUrl` 取 `data.downloadURL`（**真实 APK**）。
- 按名搜索：`POST https://en.uptodown.com/android/en/s`（`queryString` 表单）→ 结果 `url`+`name`。
- Kotlin 移植要点：`Mac`/`SecretKeySpec` 算 HMAC-SHA256；`base64Url` 解 JWT claims 取 `exp`。

### 10.4 APKMirror（元数据 / track-only，**不能直接出包**）

- 如实移植：APKMirror RSS（`/<pkg>/feed/`）给版本/发行说明/发布日期；**不产出 downloadUrl**（Obtainium `apkUrls=[]`）。
- 仅作为**版本元数据**源接入 `lookup`/`searchByName`（帮助判断「最新版本号」），其 `UpstreamAppDetail.downloadUrl`
  **恒为 null**，因此 `upsertImport` 不会写无效下载源。需要真实 APK 时回退到其它直链源。
- 反爬：`User-Agent` 必须含 `APKUpdater-v3.5.9` 令牌（Cloudflare 白名单），否则 403。

### 10.5 通用 HTML 爬虫（手动工具，移植 Obtainium `html.dart` 的 `grabLinksCommon`）

- 新增 `fetchHtmlApkLinks(url)`：拉取页面，抽取所有 `<a href>` + 裸链接 + 属性链接中匹配
  `.apk`/容器扩展名（`apkFilterService` 的扩展名清单）的绝对 URL，返回候选直链列表。
- 暴露为**管理端工具接口**（`POST /admin/external-html-links?url=`）：管理员粘贴任意商店页 URL → 返回 APK 候选链接，
  由人工确认后入库。**不自动入库、不做为自动发现主链路**（避免误抓噪声）。

### 10.6 接线与配置

- 新增开关（默认 true，可 `-D` 关闭）：`appmarket.uptodown.enabled` / `appmarket.apkmirror.enabled` /
  `appmarket.html.enabled`；沿用既有 `myapp.*` / `apkpure.*` / `aptoide.*` / `fdroid.*` / `izzy.*` / `bing.*` 体系。
- 超时预算（对齐各源 OkHttp callTimeout）：Uptodown 12s、APKMirror 12s、HTML 12s。
- `SourceGroup` 归类：应用宝/国内 → `DOMESTIC`；Uptodown/APKMirror → `OVERSEAS`（沿用「国内命中即返回、未命中才降级国外」策略）。
- `lookup` / `searchByName` / `importApp` 三处路由补充新源（与 F-Droid/Izzy/APKPure/Aptoide 同构）。

### 10.7 验收

- 应用宝某包名（如 `com.tencent.mm`）经 `lookup` / `importApp` 后，`DownloadSource(MYAPP)` 的 `downloadUrl` 是
  `a.app.sj.qq.com` 返回的 **CDN apk 直链**（非 `sj.qq.com/appdetail`）；`toMyAppPreview.apkUrl` 非空。
- Uptodown 某包名能取到真实 `downloadURL` 并落库为 `DOWNLOAD` 源。
- APKMirror 仅贡献版本号（`downloadUrl` 恒 null，不写脏源）；Uptodown/APKMirror 不可达时优雅降级（不 500）。
- `appmarket_seed.json` 不再有「详情页当 downloadUrl」的 MYAPP 行。

## 11. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| jbsdiff 字节码目标 > JDK 11 | H2 冒烟加载失败 | §4.1 兜底（joelcho / shade 构建）+ R3 自校验 |
| 大包实时 diff 吃内存 | OOM / 卡顿 | 体积护栏（§4.4）+ 部署机 `-Xmx` 建议 |
| 上游 APK 重新打包致字节变化 | 客户端拿着旧包合不出正确新包 | 下发头带 `X-*` 哈希供客户端校验；本任务记录为前提风险，不解决跨仓库一致性 |
| `downloadUrl` 失效/限速 | 冷请求降级 | `ApkCacheStore` TTL + 优雅降级信封 |
| 库与 C 版细微不兼容 | 未来客户端合不出 | R3 服务端自校验拦截坏 patch；bzip2 硬约束 |
| 差分包无 TTL/清理致磁盘膨胀 | 磁盘占满 | `ApkCacheStore` TTL；patch 可按需 `rm`；首版手写清理脚本 |
| 应用宝 `a.app.sj.qq.com` 接口变更/限流 | 应用宝直链偶尔取不到 | 该源降级为 MISS，回退其它直链源；监控 `probes` 明细 |
| Uptodown HMAC 密钥硬编码 | 与官方客户端一致，无泄密风险；接口若改签名逻辑则失败 | 失败降级为 MISS；密钥作为常量集中管理、可 `-D` 覆盖 |
| APKMirror 仅元数据、无直链 | 不能给 APK | 仅作版本探测、downloadUrl 恒 null；真 APK 由其它源承担 |
| 通用 HTML 爬虫误抓噪声链接 | 人工入库时可能选错 | 爬虫仅返回候选列表、由管理员确认，不自动入库 |

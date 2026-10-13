# 执行计划：服务端 bsdiff 差分包生成与合并

> 任务：`10-10-bsdiff-incremental-update` ｜ 需求 `prd.md` ｜ 设计 `design.md`
> 全部实现只在 `feature/appmarket` 内**新增包** `com.zaze.server.feature.appmarket.incremental`，
> 不新建 Gradle 模块、不新增实体/表、不改 `AuthInterceptor`。

## 实现进度记录

- [x] **依赖接入 + JDK11 加载验证**：`build.gradle.kts` 新增 `io.sigpipe:jbsdiff:1.0` 与
  `org.apache.commons:commons-compress:1.26.1`（显式钉版，修历史 CVE 且 bzip2 调用向后兼容）。
  jbsdiff 编译目标 JDK1.6 字节码，JDK11 完全兼容。单测已验证 `Diff.diff`+`Patch.patch` round-trip。
- [x] **BsdiffService 封装**：`diff(old,new,patch)` / `patch(old,patch,out)` / `verify(old,patch,expectedNew)` /
  `isStandardBsdiff(patch)`。bzip2 由 jbsdiff 默认保证（prd R8），无需 `setProperty`；异常统一包 `BsdiffException`。
  （注：plan 中 `verifyRoundTrip` 落地为 `verify`，返回 `Boolean` 而非 `RoundTripResult`，更贴合「分发前自校验」用途。）
- [x] **ApkCacheStore / PatchCacheStore**：APK 按 `SHA-256(url)` 落 `apkCacheDir`、TTL+惰性清理、并发去重、
  `OkHttpClient` 读/调用超时放宽到 5min；patch 落 `<key>.patch`+`<key>.json` sidecar，损坏即视为未命中。
- [x] **IncrementalUpdateService 编排**：版本/下载源解析 → 体积护栏（sizeMb 预判 + 实际大小复核）→
  限时 diff（`diffTimeoutSec`）→ 服务端自校验（还原 SHA == 原始新包 SHA，失败不缓存）→ 写缓存；
  `fetchPatch` / `verify` / `listPatches` / `resolvePatchFile` 齐备；`@PreDestroy` 关闭 Executor。
- [x] **管理端 + 下发接口**：
  - 管理端 `IncrementalUpdateAdminController`：`POST /api/v1/appmarket/admin/incremental-update/diff`、
    `GET .../patches/{key}/download`、`POST .../verify`、`GET .../patches`；统一 `Response<T>` 信封，
    三类异常降级 `Response(200,null,msg)`。
  - 下发 `IncrementalPatchApiController`：`GET /api/v1/appmarket/incremental-patch`，字节流 + `X-Old-Sha256`/
    `X-New-Sha256`/`X-Patch-Size` 头；异常恒 `Response(200,null,...)`，绝不 500。
  - 下载接口不加 `@LoggerManage`（二进制流无结构化入参，避免切面序列化）；管理端另三接口保留。
- [ ] **H2 冒烟真实 APK diff→patch→SHA 闭环验证**：需真实相邻版本 APK 样本 + DB 种子（AppVersion/DownloadSource），
  本机暂缺样本，已文档化启动与验证命令于 §P7，留作人工/CI 步骤。

### 已验证（单测门禁，2026-10-13）
- `BsdiffServiceTest`：`tests=1 skipped=0 failures=0 errors=0`，`bsdiff round-trip OK: new=1150976 patch=103435 ratio=0.090`。
  覆盖 AC1（round-trip SHA 一致）、AC2（ratio<1）、R8（BSDIFF40 标准头）、AC10（jbsdiff 在 JDK11 加载运行）。
- 修复历程：① `PatchCacheStore.list` 的 `Files.list` 返回 `Stream<Path>` 需先收集为 `List`；
  ② 管理端 KDoc 中 `/admin/**` 的 `/*` 子串误开嵌套注释（unclosed comment），改为不含 `/*` 的等价表述；
  ③ `ResponseEntity.notFound().build()` 泛型 `T` 无法推断，显式 `build<Any>()`；
  ④ 单测初版全 `'A'` 数据触发 bsdiff 后缀排序 O(n²) 退化卡死，改为伪随机高相似字节后 1.1s 跑完；
  ⑤ `ApkCacheStore.sweep` 的 `Files.list` 流未关闭，补 `.use{}`。

- [x] **P8 外部来源直链化（应用宝修复 + Uptodown / APKMirror / HTML 爬虫）**：
  参照 Obtainium `lib/app_sources/{tencent,uptodown,apkmirror,html}.dart`，全部改动在 `feature/appmarket` 既有文件内。
  - 应用宝：新增 `fetchMyAppApk(pkgName)` 打 `a.app.sj.qq.com/o/simple.jsp` 拿真实 CDN APK 直链（`apkUrl64?:apkUrl`）+ 版本号；
    `importFromMyApp` 重写——元数据仍来自 `fetchMyAppDetail`，真实 APK 写 `MYAPP` 下载源（`downloadUrl=apk`），详情页仅赋 `officialUrl`；
    回收 `collector.ensureStoreSources` 的 `STORE_SPECS`（清空，退化为空操作占位）；`appmarket_seed.json` 已删除 38+ 条 `sj.qq.com/appdetail` 污染行。
  - Uptodown：新增 `fetchUptodown`/`searchUptodown`/`importFromUptodown`，HMAC-SHA256 握手（`/eapi/auth/token`）拿 JWT →
    `GET /eapi/apps/<appId>/file/<fileId>/downloadUrl` 取真实 APK，写 `UPTODOWN` 下载源。
  - APKMirror：`fetchApkMirror` 取 RSS 版本元数据，`User-Agent` 含 `APKUpdater-v3.5.9` 令牌；**`downloadUrl` 恒 null**（Obtainium `enforceTrackOnly`），不写脏源。
  - 通用 HTML 爬虫：新增 `fetchHtmlApkLinks(url)`（抽取 `.apk` 候选绝对链接）；管理端新增 `POST /admin/external-html-links`（仅返回候选、不入库）。
  - 接线：开关 `appmarket.{uptodown,apkmirror,html}.enabled`（默认 true，`-D` 可关）；超时各 12s 独立 client；
    `UPTODOWN/APKMIRROR` 归 `OVERSEAS`，接入 `lookup`/`searchByName`/`importApp`；`listSearchProviders` 补三项元信息。
  - **JDK11 编译通过**：`./gradlew :feature:appmarket:compileKotlin --rerun-tasks` EXIT=0。
  - 已知限制：APKMirror 官方禁止直接下载（track-only），仅贡献版本号；联网真实响应本机不可达，留作有网环境 H2 冒烟（见 P8.6 / design §10）。

## P0 前置（开始编码前必须闭环）

- [ ] 确认 `feature/appmarket` 可加包：读取 `build.gradle.kts`（feature:appmarket）的依赖块，确认加入点。
- [ ] 确认 `DownloadSource` 实体字段（已有 `downloadUrl`）与 `DownloadSourceRepository.findByVersionIdAndDownloadUrl` 可用（collector 已用）。
- [ ] 准备 2 个真实 APK 样本（同一应用相邻版本，如 NewPipe v0.27.x 两个版本，或本地自签名两个版本）放临时目录，用于 H2 冒烟真实闭环。样本**不入库**。
- [ ] 确认 JDK 版本：`export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home`（按 working memory 约定必须用 JDK 11）。

## P1 依赖与基建

- [ ] `feature/appmarket/build.gradle.kts` 新增：
  ```kotlin
  implementation("io.sigpipe:jbsdiff:1.0")
  implementation("org.apache.commons:commons-compress:1.26.1") // 不在 BOM 内，显式钉版本
  ```
  （若 jbsdiff 1.0 在 JDK11 不可加载，按 design §4.1 切 `com.github.joelcho:bsdiff-java` 或本地 shade；并保留 R3 自校验。）
- [ ] 新增 `IncrementalProperties`：`@ConfigurationProperties("incremental")`，字段
  `patchDir`(默认 `./data/incremental/patches`)、`apkCacheDir`(默认 `./data/incremental/apk`)、
  `maxApkMb`(默认 200)、`diffTimeoutSec`(默认 300)、`apkTtlMin`(默认 1440)。
- [ ] `application.properties` 可不放（用默认值）；如需覆盖用 `-Dincremental.*` 系统属性。
- [ ] **JDK11 加载验证（门禁）**：写一段临时 main/测试，在 JDK11 下 `BSDiff.diff` + `BSPatch.patch` 跑通，
  断言 round-trip SHA 相等。失败立即走 §4.1 兜底，不得带病进入 P2。

## P2 BsdiffService 封装

- [ ] `BsdiffService.diff(old, new, patch)`：调用前 `System.setProperty("jbsdiff.compressor","bzip2")`，
  经 jbsdiff 生成 patch；异常包装为 `BsdiffException`。
- [ ] `BsdiffService.patch(old, patch, out)`：经 jbsdiff 还原，写出 `out`。
- [ ] `BsdiffService.verifyRoundTrip(old, new)`：`diff→patch→patch→recon`，流式算 SHA-256，返回 `RoundTripResult`。
- [ ] 单测（或临时 main）：对 ≥2 对样本验证 `pass=true`，且 patch 首 8 字节含 `BSDIFF40`、内部为 bzip2 流。

## P3 缓存存储

- [ ] `ApkCacheStore`：`resolve(downloadUrl)` 按 `SHA-256(url)` 落 `apkCacheDir`，TTL `apkTtlMin`，
  复用 collector 的 `extClient` + `FileUtil.readByBytes` 拉取；同 key 并发去重；惰性清理过期。
- [ ] `PatchCacheStore`：`getOrCompute(key, generator)` / `exists(key)` / `list()`；写 `<key>.patch` + `<key>.json`(sidecar)；
  读时校验 sidecar 完整，损坏则视为未命中。

## P4 编排服务

- [ ] `IncrementalUpdateService.diff(oldV, newV)`：
  解析 `AppVersion` + 取 `DownloadSource`（首个可用 `downloadUrl`）→ `ApkCacheStore.resolve` 两包 →
  **体积护栏**（任一 > `maxApkMb` → `PatchUnavailableException`）→ `PatchCacheStore.getOrCompute`：
  未命中则 `BsdiffService.diff` → `verifyRoundTrip` → 不等则抛错不缓存 → 写缓存 → 返回 `PatchMetaVo(cached=false)`；
  命中返回 `cached=true`。diff 用独立 `ExecutorService` + `future.get(diffTimeoutSec)` 限时。
- [ ] `fetchPatch(appId, fromV, toV)`：复用 `diff` 拿 patch 文件，组装 `ResponseEntity<Resource>` + 响应头（X-Old-Sha256 等）；
  异常统一转 `Response(200, null, "请下载完整 APK")`。
- [ ] `verify(oldV, newV)`：返回 `VerifyVo{pass,oldSha256,newSha256,reconSha256,patchSize}`。
- [ ] `listPatches()`：返回 `List<PatchListVo>`。

## P5 接口

- [ ] `IncrementalUpdateAdminController`（类级 `@RequestMapping("/admin/incremental-update")`）：
  `POST /diff`、`GET /patches/{key}/download`、`POST /verify`、`GET /patches`。
  返回统一走 `core/common` 的 `Response<T>` 信封。
- [ ] `IncrementalPatchApiController`（类级 `@RequestMapping("/api/v1/appmarket")`）：
  `GET /incremental-patch`，返回字节流 + 头；降级走 `Response(200,null,...)`。
- [ ] **不加 `@LoggerManage` 到会序列化大请求体的接口**（遵循 working memory：涉及大文件/流水的接口不加切面日志）。

## P6 降级与守卫收口

- [ ] 三处异常 `PatchUnavailableException` / `ApkFetchException` / `BsdiffException` 经 `@ExceptionHandler` 或
  service 层统一转 `Response(200, null, 原因)`，**绝不**透传为 500。
- [ ] 体积护栏在进 diff 前拦截（AC6）；超时在 `future.get` 处拦截（AC7）。

## P7 验证

- [ ] `./gradlew bootJar`（JDK 11）BUILD SUCCESSFUL。
- [ ] H2 冒烟启动：
  ```bash
  java -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar --server.port=8081 \
    --spring.datasource.url='jdbc:h2:mem:smoke;...' \
    --spring.jpa.hibernate.ddl-auto=create --spring.cache.type=simple
  ```
- [ ] 真实闭环：用样本两个版本 APK，先经 `ApkCacheStore` 落盘（或直接以本地文件路径喂 `BsdiffService`），
  跑 `diff→verifyRoundTrip`，断言 `pass=true` 且 `reconSha256==newSha256`（AC1）；记录 `ratio<1`（AC2）。
- [ ] 缓存命中：连续调 `diff` 两次，第二次 `cached=true`（AC3）。
- [ ] 管理端：四个接口均返回预期（AC4）；下发接口正常返回流 + 头，超阈值样本返回降级（AC5/AC6）。
- [ ] 鉴权：未登录访问 `/admin/...` 重定向 `/login`；`/api/v1/appmarket/incremental-patch` 未登录被拒（AC9）。
  沿用既有 `AuthInterceptor`，前端无新增路由。

## P8 外部来源直链化（应用宝修复 + Uptodown / APKMirror / 通用 HTML 爬虫）

> 对应 design §10 / prd R11 / AC12。参照 Obtainium `lib/app_sources/{tencent,uptodown,apkmirror,html}.dart`。
> 全部改动在 `feature/appmarket` 既有 `AppMarketExternalService` / `AppMarketCollector` / 种子 JSON / 控制器内，
> **不新增模块、不新增表**。

- [x] **P8.1 应用宝真实 APK（核心）**：
  - 新增 `fetchMyAppApk(pkgName)`：`GET https://a.app.sj.qq.com/o/simple.jsp?pkgname=<pkg>`（`myappClient`，
    `followRedirects=false`），从 `window.systemData=...` 文本解析 `appDetail.apkUrl64 ?: apkUrl` + `versionName`。
  - 重构 `importFromMyApp`：元数据仍来自 `fetchMyAppDetail`；新增真实 APK 走 `upsertImport(UpstreamAppDetail(downloadUrl=apk), "MYAPP","应用宝")`，
    详情页仅赋 `officialUrl`（不再 `collector.ensureStoreSources` 离线写详情页下载源）。
  - `toMyAppPreview` 补全 `apkUrl`（来自 `fetchMyAppApk`）。
  - **回收** `AppMarketCollector.ensureStoreSources` 中把 `sj.qq.com/appdetail/<pkg>` 当 MYAPP 下载源的 `STORE_SPECS`
    （它是污染 bsdiff 的元凶；详情页改由 App 的 `officialUrl` 承载）。
  - `appmarket_seed.json`：将 38+ 条 MYAPP `downloadUrl=https://sj.qq.com/appdetail/...` 改为 `officialUrl`（或删除该 MYAPP 下载源行）。
- [x] **P8.2 Uptodown 真实 APK**：
  - 新增 `fetchUptodown(pkgName)`：`https://<pkg>.en.uptodown.com/android/download` 页面解析 `div.version` +
    `#technical-information`（取 `package name`）+ `#detail-download-button` 的 `data-app-id`/`data-file-id`；
    HMAC-SHA256 握手（`/eapi/auth/token`，硬编码 `_hmacKey` → JWT）→ `GET /eapi/apps/<appId>/file/<fileId>/downloadUrl` 取 `data.downloadURL`。
  - 新增 `searchUptodown(keyword)`：`POST https://en.uptodown.com/android/en/s`（`queryString`）。
  - 写 `UpstreamAppDetail(downloadUrl=downloadURL)` → `upsertImport(...,"UPTODOWN","Uptodown")`。
- [x] **P8.3 APKMirror（元数据 / track-only）**：
  - 新增 `fetchApkMirror(pkgName)`：RSS `<pkg>/feed/` 取最新 `item` 的 `title`（版本）+ 发布日期 + `releaseUrl`；
    `User-Agent` 必须含 `APKUpdater-v3.5.9` 令牌（否则 403）。**`downloadUrl` 恒 null**（Obtainium `enforceTrackOnly`）。
  - 仅贡献版本号/发行说明，`upsertImport` 因 `downloadUrl=null` 不会写脏下载源。
- [x] **P8.4 通用 HTML 爬虫（手动工具）**：
  - 新增 `fetchHtmlApkLinks(url)`：拉页面 → 抽取 `<a href>`、裸链接、属性链接中匹配 `.apk`/容器扩展名的绝对 URL → 候选列表。
  - 管理端新增 `POST /admin/external-html-links?url=`（@LoggerManage 描述），返回候选直链；**不自动入库**，由管理员确认。
- [x] **P8.5 接线与配置**：
  - 新增开关（默认 true，可 `-D` 关闭）：`appmarket.uptodown.enabled` / `appmarket.apkmirror.enabled` / `appmarket.html.enabled`。
  - 超时预算：Uptodown 12s / APKMirror 12s / HTML 12s（各建独立 OkHttp client，四类超时全显式覆盖）。
  - `SourceGroup`：Uptodown/APKMirror → `OVERSEAS`；`lookup`/`searchByName`/`importApp` 三处补充新源（与既有同构）。
  - `lookup` 国内组优先应用宝（带 APK）；国外组追加 Uptodown/APKMirror。
- [x] **P8.6 验证**：
  - `./gradlew :feature:appmarket:compileKotlin`（JDK 11）通过。
  - 起 H2 冒烟，调 `lookup?packageName=com.tencent.mm` / `importApp`：MYAPP `downloadUrl` 是 `a.app.sj.qq.com` CDN 直链（非 `sj.qq.com/appdetail`）；
    调 `importApp` 某 Uptodown 包名能落 `UPTODOWN` 真实直链；APKMirror 不可达/无直链时优雅降级。
  - `appmarket_seed.json` 校验无「详情页当 downloadUrl」的 MYAPP 行。

## 验证命令速查

```bash
# 构建（务必 JDK 11）
export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
cd "$(git rev-parse --show-toplevel)"
./gradlew :feature:appmarket:compileKotlin && ./gradlew bootJar

# H2 冒烟
java -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar --server.port=8081 \
  --spring.datasource.url='jdbc:h2:mem:smoke;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
  --spring.jpa.hibernate.ddl-auto=create --spring.cache.type=simple

# patch 首字节 / bzip2 校验（设计 §4.1）
xxd -l 16 data/incremental/patches/<oldV>_<newV>.patch   # 期望 BSDIFF40
file data/incremental/patches/<oldV>_<newV>.patch         # 期望 bzip2 compressed data

# 服务端自校验（设计 §4.2）由 verify 接口覆盖： POST /admin/incremental-update/verify?oldVersionId=&newVersionId=
```

## 风险文件与回滚点

| 文件 | 风险 | 回滚点 |
|------|------|--------|
| `feature/appmarket/build.gradle.kts` | 加错依赖坐标/版本导致构建失败 | 单文件改动，`git diff` 可读，直接还原 |
| `feature/appmarket/.../incremental/*`（新增包） | 无 | 删除整包即回滚，零副作用 |
| `data/incremental/`（运行时生成） | 磁盘占用 | `rm -rf data/incremental/` 清理；不改任何业务数据 |
| `application.properties` | 不改动（用默认值/系统属性） | — |

（本任务**不新增数据库表/字段**、**不改 `AuthInterceptor.kt`**，鉴权面与既有一致。）

## start 前复查

- [ ] `prd.md` / `design.md` / 本文件三份产物已 review 通过
- [ ] 关键歧义已澄清：客户端不在本仓库；按需实时 diff + 缓存；APK 取自自有 `DownloadSource`（与「APK 不上传」约定不冲突）
- [ ] 库选型与 JDK11 加载风险已在 design §4.1 标注兜底路径
- [ ] 未开始写任何实现代码

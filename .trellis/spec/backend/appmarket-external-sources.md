# AppMarket External APK Sources — Direct-Link Contract

> Executable contract for fetching external app metadata/APKs in `feature/appmarket`.
> Critical because `DownloadSource.downloadUrl` is consumed directly by the bsdiff incremental-update
> pipeline (task `10-10-bsdiff-incremental-update`, R5/R11): a non-APK URL there silently corrupts diffs.

---

## 1. Scope / Trigger

- Trigger: any change to `AppMarketExternalService` (external lookup/search/import) or to how
  `DownloadSource.downloadUrl` is populated. Also any new external upstream added to appmarket.
- Why this needs code-spec depth: the field is cross-feature (imported by `incremental/` for patch
  generation). A wrong value (store **detail page** instead of a real **APK file**) breaks the diff
  with no compile error and no 4xx — it just produces a useless/garbage patch.

## 2. Signatures

- `fetchMyAppApk(pkgName): MyAppApk?` — real APK URL from Tencent/应用宝.
- `importFromMyApp(pkgName, existing): AppVo` — metadata + real APK download source.
- `fetchUptodown(pkgName): UpstreamAppDetail?` / `searchUptodown(keyword): List<ExternalAppPreview>` /
  `importFromUptodown(pkgName): AppVo`.
- `fetchApkMirror(pkgName): UpstreamAppDetail?` — metadata only.
- `fetchHtmlApkLinks(url): List<String>` — candidate extractor (admin tool, no DB write).
- `POST /api/v1/appmarket/admin/external-html-links?url=` — returns candidate APK links, never imports.

## 3. Contracts

### 3.1 `DownloadSource.downloadUrl` — the hard rule
- MUST be a URL that, when fetched server-side, returns a binary **APK file** (or XAPK/APKS container
  the diff pipeline can handle). It must NOT be a web **detail page** (HTML).
- `officialUrl` (separate field) MAY hold the store detail page for human reference; it is never used
  as a download target.
- `sourceType` values for external APKs: `MYAPP`, `UPTODOWN`, `APKMIRROR` (metadata only, see §3.3),
  plus existing `FDROID`, `IZZY`, `APKPURE`, `APTOIDE`.

### 3.2 Per-source facts (verified against Obtainium `lib/app_sources/*.dart`)
| Source | Real APK? | Endpoint / mechanism | Notes |
|--------|-----------|----------------------|-------|
| 应用宝 / Tencent | **YES** | `GET https://a.app.qq.com/o/simple.jsp?pkgname=<pkg>`; parse `window.systemData.appDetail.apkUrl64 ?: apkUrl` + `versionName` | **Single source for both APK and metadata.** The same `appDetail` (23 fields) carries `appName`/`editorIntro`/`iconUrl`/`developer`/`fileSize.bytes` → the 391KB detail page is **NOT** needed on the hot path (see §3.4). `sj.qq.com/appdetail/<pkg>` is a **detail page, NOT a download**. |
| Uptodown | **YES** | Download page → `data-app-id`/`data-file-id` → HMAC-SHA256 handshake `POST /eapi/auth/token` (hardcoded `_hmacKey`) → JWT → `GET /eapi/apps/<appId>/file/<fileId>/downloadUrl` → `data.downloadURL` | Handshake requires the exact hardcoded HMAC key from Obtainium; do not "simplify" it. |
| APKMirror | **NO** | RSS `https://www.apkmirror.com/apk/<pkg>/feed/` for version metadata | `enforceTrackOnly` upstream — direct download is forbidden. **Keep `downloadUrl = null`**; contributes version number only. |
| HTML crawler | manual | Fetch page, extract `<a href>` / bare / attribute links matching `.apk` (and container extensions), return absolute URLs | Admin-only candidate list; **never auto-import** — a human confirms. |

### 3.3 Provider config & groups
- Toggle (default `true`, override with `-D`): `appmarket.uptodown.enabled` / `appmarket.apkmirror.enabled` /
  `appmarket.html.enabled` (plus `fdroid`/`izzy`/`myapp`/`apkpure`/`aptoide` from earlier work).
- Bases (override with `-D`): `appmarket.myapp.base` (detail page, `https://sj.qq.com/appdetail/`),
  `appmarket.myapp.search` (name search), **`appmarket.myapp.apk`** (real APK endpoint,
  `https://a.app.qq.com/o/simple.jsp`).
- `SourceGroup`: `DOMESTIC` (local dict / 应用宝 / Bing discovery) is probed **first and by default**;
  `OVERSEAS` (F-Droid / Izzy / APKPure / Aptoide / Uptodown / APKMirror) is only a fallback when the
  domestic group fully misses. `UPTODOWN`/`APKMIRROR` are `OVERSEAS`.
- Each source has an **independent timeout budget**; the global deadline was removed in favor of
  per-source budgets + a safety cap. `extClient` 10s (call/read/write/connect all set), `izzyClient`
  15s (large index, 1h cache), `uptodown`/`apkmirror`/`html` 12s each with their own client.

### 3.4 Endpoint roles — hot path vs batch complete

The `a.app.qq.com` response is ~355KB and **already carries the metadata**, so also calling the
`sj.qq.com/appdetail` page (~391KB) doubles the cost for almost no new information.

| | Hot path (`lookup` / `importApp`) | Batch complete (`batchCompleteFromMyApp`) |
|---|---|---|
| Endpoints | **APK endpoint only** | APK endpoint **+ detail page** (`withDetail = true`) |
| Gets | name, version, icon, size, summary, developer, real APK | same, **plus `category` and `updateTimeSec`** |
| Cost | 1 request (~0.4s / 355KB) | 2 requests |

- The APK endpoint does **not** return `category` or update time — those are the detail page's only
  unique value, which is why it survives **only** in batch complete.
- `importFromMyApp(pkgName, existing, withDetail: Boolean = false)` — pass `true` **only** from batch
  complete. Do not reintroduce the detail-page call into `lookup` / `importApp`.
- Measured: `lookup` went from ~1.35s to **~0.42s** after dropping the detail page from the hot path.
- Searching **by package name via the search API does not work** — it returns unrelated apps
  (verified: `com.larus.nova` → 星驰加速器 / 大谷云 / 机大侠助手). Never substitute search for a
  package-name lookup.

## 4. Validation & Error Matrix

| Condition | Behavior | Why |
|-----------|----------|-----|
| `downloadUrl` set to a store detail page (`sj.qq.com/appdetail/...`) | **BUG** — reject at design/review time; if already in DB, it is a corrupt source | bsdiff would fetch HTML → corrupt/empty patch, no error surfaced |
| 应用宝 reachable | `fetchMyAppApk` returns CDN `apkUrl64`/`apkUrl` → write real `MYAPP` download source | real APK |
| 应用宝 unreachable / parse fails | `fetchMyAppApk` returns `null`; `importFromMyApp` writes metadata only (no download source) and degrades gracefully | no crash, no fake link |
| APKMirror | `downloadUrl` stays `null`; `upsertImport` skips writing a download source | upstream forbids direct download |
| Uptodown handshake fails | return `null` / `Response(200,null,...)` | graceful degradation, never throw to caller |
| Any upstream timeout | per-source `SourceProbeVo(status=TIMEOUT)`; overall still returns non-null envelope | resilient discovery |
| HTML crawler | returns candidate list only; never writes DB | avoid unverified auto-import |
| **Upstream network failure** (fetch swallows the exception → `null`) | probe lands on `MISS`, and the message **must not claim "已连通"** — it must stay honest and expose `elapsedMs` | see warning below |

> **Warning — `MISS` does NOT mean "the source is reachable".**
> Every `fetchXxx` swallows network/parse exceptions and returns `null`, so a source that was never
> reached is indistinguishable from one that genuinely has no such package — both surface as `MISS`.
> Therefore `SourceProbe.toVo()` must never print "已连通，但该源无此包名".
> Current wording rules (`SUSPECT_UNREACHED_MS = 100`):
> - `elapsedMs < 100` → "未取到数据（耗时仅 Xms，极可能是请求未连通/被拒，而非该源没有此包名）"
> - otherwise → "未匹配到该包名（耗时 Xms；注：请求失败时同样表现为未命中）"
> - `TIMEOUT` / `ERROR` keep their own messages; an exception whose `message` is `null` must fall back
>   to the class simple name, otherwise it would be misreported as `MISS`.
> The local dictionary builds its `SourceProbeVo` directly (not via `toVo()`), so it is unaffected.

## 5. Good / Base / Bad Cases

- **Good**: `downloadUrl = "http://imtt.dd.qq.com/sjy.00022/.../6542DD39....apk?fsname=com.larus.nova_15.3.0.apk"`
  — the **CDN link** returned inside `appDetail.apkUrl64` (fetched from `a.app.qq.com`), or Uptodown `downloadURL`.
  Verified live: `com.larus.nova` → 豆包 15.3.0 → real CDN `.apk`.
- **Base**: APKMirror import yields version metadata, `downloadUrl = null`, app still importable as metadata-only.
- **Bad**: `downloadUrl = "https://sj.qq.com/appdetail/com.tencent.mm"` — looks like a link, is actually a page.

## 6. Tests Required

- Unit: `fetchMyAppApk` parses `window.systemData.appDetail.apkUrl64` correctly from a fixture (assert
  non-null `downloadUrl` ends in an APK-like path; `versionName` extracted).
- Contract test: assert `importFromMyApp` never writes a `downloadUrl` containing `sj.qq.com/appdetail`.
- Integration (network, CI/manual): `lookup?packageName=com.tencent.mm` → MYAPP `downloadUrl` is the
  CDN APK link; `importApp` with a Uptodown package → `UPTODOWN` real download source; APKMirror
  unreachable → graceful degradation (no crash, no dirty source).
- See `implement.md` §P8.6 for the H2-smoke command set.

## 7. Wrong vs Correct

#### Wrong
```kotlin
// storing the 应用宝 DETAIL PAGE as the download source (old bug)
DownloadSource(sourceType = "MYAPP",
               downloadUrl = "https://sj.qq.com/appdetail/$packageName") // HTML page!
```
→ bsdiff fetches HTML, diff produces garbage, client can't patch.

#### Correct
```kotlin
// 应用宝 real APK (endpoint host a.app.qq.com), detail page goes to officialUrl
val apk = fetchMyAppApk(packageName)            // apkUrl64 ?: apkUrl from window.systemData.appDetail
if (apk != null) {
    upsertImport(UpstreamAppDetail(downloadUrl = apk.apkUrl, versionName = apk.versionName),
                 "MYAPP", "应用宝")
}
// detail page is reference-only:
app.officialUrl = "https://sj.qq.com/appdetail/$packageName"
```

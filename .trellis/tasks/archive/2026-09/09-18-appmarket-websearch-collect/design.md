# Design — 新增「搜索引擎发现」渠道（Bing）

## 架构与边界

本期为**纯增量**，全部改动收敛在 `feature/appmarket` 模块；核心文件为
`service/AppMarketExternalService.kt`，`ExternalAppPreview` / `SearchProviderMeta` DTO、控制器与前端**仅小幅扩展**。

新增一个「发现」客户端（非普通上游——它不返回元数据，只吐候选包名）：

| 项 | 值 |
|----|----|
| source 常量 | `BING_SOURCE = "Bing发现"` |
| 基址 | `bingSearchBase`，默认 `https://www.bing.com/search?q=%s+apk&setlang=zh-CN`（`%s` 为 `URLEncoder` 编码的查询）|
| 开关 | `bingEnabled`（默认 true，`-Dappmarket.bing.enabled=false` 关闭）|
| 基址覆盖 | `-Dappmarket.bing.search=...` |
| 客户端 | `bingClient`：`extClient` 形态 + 显式 `User-Agent`（Bing 对 UA 敏感），read 12s（结果页较大）|

接入形态沿用现有上游约定（基址 + `-D` 开关 + 超时客户端 + source 常量），便于与既有六路保持一致。

## 数据流与契约

### 抽取逻辑 `discoverPackagesFromBing(keyword): List<String>`

1. `GET bingSearchBase.format(URLEncoder.encode(keyword, "UTF-8"))` via `bingClient`；
   非 2xx / 超时 → 返回空（由调用方吞掉，实现优雅降级 AC4）。
2. 从所有 `<a href="...">` 与正文 URL 抽取候选包名：
   - **优先（store-URL 参数）**：`pkgname=([a-z0-9._]+)`（应用宝 `a.app.qq.com`）、
     `[?&]id=([a-z0-9._]+)`（仅当 host ∈ 已知商店域：应用宝/小米/apkpure）；
   - **兜底**：URL 路径 `/com\.[a-z0-9_]+\.[a-z0-9_]+`、`https?://` 外的裸 `com.xxx.yyy` 形态（snippet/title 出现时）。
3. 合法性校验：匹配 `^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$`。
4. **噪声域名黑名单**（host 后缀）：`baidu.com`、`miit.gov.cn`、`mps.gov.cn`、`microsoft.com`、
   `wikipedia.org`、`zhihu.com`、`zhidao.baidu.com`、`baike.baidu.com`、`gstatic.com` 等（依实测噪声域收敛）。
5. 去重 + 截断 `MAX_SEARCH_RESULTS`，返回候选包名列表（不含 name，name 交后续 lookup 解析）。

### 接入点（复用现有形态，R5：不新增导入端点）

- **`searchByName(keyword)`**：六路 `tasks` 聚合去重后**若结果为空**（可信上游都没命中），
  追加一步并行 `discoverPackagesFromBing`，把每个候选包名包装为 `ExternalAppPreview(
  packageName=候选, name=keyword, source=BING_SOURCE, apkUrl=null)`，并入既有候选列表（同样走 packageName 去重/截断）。
  ⇒ Bing 仅作**兜底发现**，不污染已知应用的搜索（AC2 触发时机）。
  发现任务作为 `tasks` 额外项，共享统一 deadline；超时 `cancel` 跳过、计入 failed 不影响整体（AC4）。
- **`external-search`**：返回列表天然含 `source=Bing发现` 项，复用前端既有渲染。
- **导入**：Bing 候选点击 → 触发既有 `external-lookup?packageName=<候选>`（单条预览）
  → `external-import?packageName=&source=<实际找到的上游>`。**无新导入端点**，管理员确认环节由既有流程保证（R1）。
- **`listSearchProviders()`**：追加 `SearchProviderMeta("bing", BING_SOURCE, bingSearchBase, bingEnabled)`（AC1）。
- **`lookup(raw)`**：**不接** Bing（Bing 是按名发现，不是按包名查元数据），保持仅六路。

### 对外契约（复用，无新增字段）

`ExternalAppPreview` 已有 `packageName / name / source / apkUrl` 字段，Bing 候选复用 `source="Bing发现"` 即可，
**无需改 DTO**。候选态不写库；真正 `importApp` 时 sourceType 由命中上游决定，故**无需新增 sourceType 枚举值**。

## 兼容与迁移

- 无 schema 变更：`App` / `AppVersion` / `DownloadSource` 不变。
- 关闭方式：`-Dappmarket.bing.enabled=false` 即时生效；或删除新增分支。无数据迁移。
- 既有六路优先级与行为**不变**（R3）。

## 重要权衡

- **兜底而非常驻**：避免给 APKPure/词典已能命中的应用塞入低置信 Bing 结果，减少管理员噪声。
- **不用百度**：实测图形验证码硬拦（`wappass.baidu.com/.../captcha/tuxing_v2.html`），程序无法绕过；DDG 不可达。
- **抽取稳健性**：以 store-URL 参数（`pkgname=`/`id=`）为主、裸包名正则兜底；即便 Bing 页面结构微调，参数型链接通常稳定。
  Bing 失效只影响「发现」，不影响六路核心。
- **合规/ToS**：Bing HTML 抓取属 ToS 灰色地带；本期作 demo/内部能力，提供 `-D` 一键关闭，上线前需评估。
- **无签名校验/白名单**：与 R1 信任边界一致——下载源始终由可信上游产生，Bing 只贡献候选包名，天然规避篡改包分发。

## 运维 / 回滚

- 开关：`-Dappmarket.bing.enabled=false` 即时关闭整条发现链路。
- 基址兜错/出网被拦：发现任务超时跳过，列表退化为仅六路结果，无 5xx、不拖慢整体（统一 deadline 10s）。
- 无 schema、无破坏性改动，回滚安全（删分支或关开关）。

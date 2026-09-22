# PRD — 新增「搜索引擎发现」渠道（Bing）采集 APK 候选

## Goal

补上现有应用市场唯一的真缺口：**按中文应用名检索不到**（静态词典 `appmarket_cn_dict.json` 仅 96 条、应用宝/小米的搜索又都是客户端渲染）。
引入 Bing 网页搜索作为「发现」渠道：给定应用名，从搜索结果 URL 参数里提取**候选包名**，再交给既有可信上游取真实元数据与 APK。
**不引入任何新的「自动分发」风险**——搜索引擎只贡献候选包名，真正的下载源仍由 APKPure/Aptoide/F-Droid/应用宝等既有上游产生，且需管理员确认才入库。

用户价值：词典外的国内应用（如 学习强国、国家反诈中心、个人所得税）也能在前端被搜到并一键导入，覆盖度不再受 96 条静态词典限制。

## Confirmed Facts（仓库证据，已核实）

**现有上游体系**（`feature/appmarket/.../service/AppMarketExternalService.kt`）

- 搜索/查询优先级：`本地词典 → F-Droid → IzzyOnDroid → APKPure → Aptoide → 应用宝`（`09-16-appmarket-name-search-source/design.md`）。
  六路在 `lookup()`/`searchByName()` 中并行执行、共享 10s 统一截止时间（`SEARCH_OVERALL_TIMEOUT_MS`），单源失败只影响自己、不拖垮整体。
- 每个上游形态一致：`基址 + enabled 开关（-D 覆盖）+ 专用 OkHttp 客户端 + 解析函数 + source 常量`。
  - 配置：`fdroidBase` :56、`fdroidSearchBase` :62、`izzyIndexUrl` :78、`myappBase` :115、`extClient` :93（connect8/read10/write10/call10s）。
  - source 常量：`MYAPP_SOURCE="应用宝"` :1217、`APKPURE_SOURCE` :1219、`APTOIDE_SOURCE` :1221。
  - 对外枚举：`listSearchProviders()` :858-866 返回 6 个 provider（含本地词典 :861）。
  - 新增一个源的**标准接线点**只有四处：`lookup()` :201-202、`searchByName()` :245-246、`importApp()` :358-359、`listSearchProviders()` :865-866。
- **中文名 → 包名** 目前依赖静态手工词典 `AppMarketCnDict`（`data/appmarket_cn_dict.json`，**仅 96 条**）。
  成因见 `:112-113`：应用宝搜索是客户端渲染、服务端抓不到，故「应用名 → 包名」改由本地词典兜底。

**信任模型现状（决定本方案形态）**

- `appmarket_source.sourceType` 是**普通字符串**，无任何可信度分级；门户把每个 `DownloadSource.downloadUrl` **直接渲染成用户可点下载按钮**
  （`09-12-app-market/design.md`：每版本来源按钮 `<a target="_blank" href=...>`）。⇒ 任何自动入库链接立即成为用户下载入口，无中间审核。
- 已存在人工路径：`POST /import-from-apk`（`AppMarketAdminApiController.kt:145`）、按「前缀 + 包名」拼详情页的第三方商店源引擎（`AppMarketCollector.kt:241` + `ensureStoreSources`）。

**实测数据（本环境经 `HTTP_PROXY=127.0.0.1:50176` 出网；`curl --noproxy` 会假性失败）**

| 探针 | 结果 |
|------|------|
| `baidu.com/s?wd=…+apk` | 302 → `wappass.baidu.com/.../captcha/tuxing_v2.html`（**图形验证码页**），0 条结果 ⇒ ❌ 服务端硬拦 |
| `bing.com/search?q=…+apk` | 302 → `cn.bing.com`，200 / 95KB / `b_algo`×10 / `<cite>`×10，含真实 `href` ⇒ ✅ 可服务端抓取 |
| `html.duckduckgo.com/html/?q=…` | HTTP 000 ⇒ ❌ 不可达 |

Bing 发现命中率压测（4 个**词典中不存在**的中文应用）：

| 查询 | 提取到的包名 | 命中 |
|------|--------------|------|
| 学习强国 | `cn.xuexi.android` | ✅ |
| 个人所得税 | `cn.gov.tax.its` | ✅ |
| 国家反诈中心 | `com.hicorenational.antifraud` | ✅ |
| 交管12123 | —（仅备案/百科域名） | ❌ |

**包名出处（关键）**：结果 URL 参数里——`a.app.qq.com/o/simple.jsp?pkgname=<pkg>`、`app.mi.com/details?id=<pkg>`。
⇒ 与"只做发现"完美契合：搜索引擎只吐候选包名，由 `pkgname=`/`id=` 参数即可抽取，无需解析网页正文或下载站跳转。

**小米商店旁证**（`app.mi.com/details?id=<pkg>` → 200/32KB，**服务端渲染含包名**；`app.mi.com/search?keywords=<中文名>` → 200 但**不含包名**）：
再次确认**商店自身的按名搜索是客户端渲染**，外部搜索是填补该缺口的唯一手段。（本期不把小米接成第 7 路，见 Out of Scope。）

## Requirements

- **R1 信任边界（已决策）**：只做发现，不自动入库。搜索引擎仅把「应用名 → 候选包名」提供给管理后台，APK 仍只从既有可信上游取；
  候选须管理员确认后才落到可信源取包；**绝不自动写入任意第三方下载站的 APK/直链**。由此无需引入签名校验/域名白名单（下载源仍由可信上游产生）。
- **R2 缺口优先级（已决策）**：先补「按中文名检索不到」，即 Bing 作为 `searchByName` 的**兜底/发现源**。
- R3 新增发现源必须沿用现有 4 个接线点形态：`基址 + -D 开关 + 超时客户端`，且 `searchByName` 中**并行**纳入统一截止时间；可一键 `-Dappmarket.bing.enabled=false` 关闭；不改变既有六路语义与优先级。
- R4 抽取出的候选包名须经过**噪声域名黑名单**过滤（`beian.miit.gov.cn`、`beian.mps.gov.cn`、`*.baidu.com`、`baike.baidu.com`、`go.microsoft.com` 等），并对结果 md5/包名去重，上限 `MAX_SEARCH_RESULTS`。
- R5 候选在前端表现为一类特殊结果（source 标记如 `Bing发现`/`搜索引擎`），点击复用**既有** lookup→import 流程（按包名），不新增导入端点、不改变管理员确认环节。

## Acceptance Criteria

- AC1：`GET /api/v1/appmarket/admin/external-sources` 含 `bing` provider（`enabled` 默认 true），`-Dappmarket.bing.enabled=false` 后显示 `enabled:false`、且不影响其余六路。
- AC2：`GET /api/v1/appmarket/admin/external-search?keyword=<词典外中文名>`（如 `国家反诈中心`）返回 ≥1 候选，其 `packageName` 为真实包名（如 `com.hicorenational.antifraud`）、`source=Bing发现`；
  触发时机为**可信上游无结果时回退**（避免对已知应用刷噪声）。
- AC3：点击该 Bing 候选复用既有 lookup→import（按包名），无新导入端点；管理员确认前不落库（满足 R1）。
- AC4：优雅降级——Bing 不可达/被拦时 `external-search` 返回友好空结果 + 提示，无 5xx；整体时延仍受 `SEARCH_OVERALL_TIMEOUT_MS`（10s）约束（发现任务并行、到点跳过）。
- AC5：噪声域（beian.*/baike.baidu.com/go.microsoft.com）永不作为候选出现；抽检 ≥5 个词典外查询，≥3/5 能给出正确包名。
- AC6：JDK 11 编译 + `bootJar` 通过；管理页渲染正常、无 JS 报错；既有六路行为与优先级不变。

## Out of Scope（本期不做）

- 把小米应用商店接成第 7 路可信上游（旁证已探活，留待独立议题）。
- 百度 / DuckDuckGo 等其他搜索引擎（百度被验证码硬拦、DDG 不可达，已证伪）。
- 任何「自动入库任意站点 APK」、签名校验/域名白名单机制（与 R1 信任边界冲突，且本方案无需）。
- 将发现结果回填进 `AppMarketCnDict`（可作为后续增强，非 MVP）。

## Open Questions

无（两项阻塞决策已定：信任边界 = 只做发现；路线 = Bing 发现管线）。

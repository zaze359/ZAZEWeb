# 按应用名搜索并导入 apk 的上游源

## Goal（目标与用户价值）

在管理后台「导入外部资源」管线中新增**服务端可搜索、且返回真实 APK 直链**的上游源，
解决两个痛点：(1) 应用宝无法服务端按名搜索（搜索页客户端渲染、抓不到），现仅靠本地词典把中文名→包名；
(2) F-Droid / IzzyOnDroid 只覆盖开源应用，主流 / 国产应用搜不到，且应用宝即便命中也只给详情页 URL、不给 APK 直链。

新上游让「输入应用名（含中文）→ 返回候选（带包名）→ 一键导入（应用 + 最新版本 + 真实下载源）」
对主流 / 国产应用可用，且下载源是可直接安装的 APK/XAPK 直链。

## Key Decisions（已确认）

- **D1 落地形态**：服务端上游源，复用现有 F-Droid/Izzy 收集器模式（用户确认）。
- **D2 上游选型**：**APKPure 为主 + Aptoide 兜底**。搜索优先级 APKPure 先于 Aptoide；
  两者均参与 `searchByName` 聚合与 `packageName` 去重，各自带独立 `source` 标记；
  Aptoide 作为 APKPure 不可达时的稳定回退（Aptoide 有官方搜索 API）。
- **D3 导入语义**：**upsert 补下载源**。已有同包名应用不重建，仅按 `downloadUrl` 去重追加新上游的真实 APK 下载源；不冲掉已有元数据。
- **D4 客户端实现**：**手撸轻量客户端**。在 `AppMarketExternalService` 内新增 OkHttp+解析方法，
  沿用 F-Droid/Izzy/应用宝同构风格；不引外部抓取库。Aptoide 走官方 API，APKPure 手写抓取。
- **D5（由 PRD 范围派生）XAPK 处理**：下载源只存上游返回的直链（APKPure 可能为 XAPK）；
  XAPK 的服务端拆包/重组与端侧安装能力**不在本次范围**，列为后续项。

## Requirements（需求）

- **R1 按名搜索**：新上游须支持服务端按应用名（含中文）搜索，返回候选（名称、包名、图标、简介、最新版本），带 `source` 标记，进入 `searchByName` 聚合去重链。
- **R2 精确查询**：新上游须支持按包名精确查询预览（`lookup`），返回元数据 + **真实 APK/XAPK 直链**。
- **R3 一键导入**：`importApp` 须能把新上游的「应用 + 最新版本 + 下载源」写入三表；下载源 `downloadUrl` 为可直接下载安装的直链（区别于应用宝仅详情页）。导入语义遵循 D3（upsert）。
- **R4 接入骨架**：复用现有 `lookup / searchByName / importApp / listSearchProviders` 与 `DownloadSource` 模型，**不改动**数据访问层与上层 VO；新上游仅在 `AppMarketExternalService` 内以新分支接入（与 F-Droid/Izzy 同构）。
- **R5 开关与降级**：提供 `-Dappmarket.apkpure.enabled` / `-Dappmarket.aptoide.enabled` 与基址覆盖开关；上游不可达时快速失败（超时 10s 量级）、优雅降级（返回 null / 抛 `IllegalStateException` 由控制器转友好提示），不重试。
- **R6 source 标记与去重**：新上游结果带独立 `source` 标记（`APKPure` / `Aptoide`），参与 `packageName` 去重；UI「所有源」列表同步展示。
- **R7 合规降级**：沿用现有「尽力抓取、失败即降级、不托管二进制」的姿势；只存上游直链，不缓存/托管 APK 二进制。

## Acceptance Criteria（验收）

- [ ] AC1 `GET /external-search?keyword=微信`（或 `抖音`）返回来自新上游的候选，`source` 标记正确、含包名（如 `com.tencent.mm`）。
- [ ] AC2 `GET /external-lookup?packageName=com.tencent.mm` 返回带真实 APK/XAPK `downloadUrl` 的预览（非详情页）。
- [ ] AC3 `POST /external-import?packageName=com.tencent.mm&source=APKPure` 把应用 + 最新版本 + 真实下载源写入三表；`DownloadSource.downloadUrl` 为可直接下载的直链。
- [ ] AC4 新上游不可达（超时/断网）时，`external-search` 优雅降级（跳过或友好提示），`external-lookup` 返回 `Response(200,null,"未找到…")`，不抛 5xx、不重试。
- [ ] AC5 `GET /external-sources` 列出新上游（含 `enabled` 状态）。
- [ ] AC6 `-Dappmarket.apkpure.enabled=false` / `aptoide.enabled=false` 可分别关闭对应上游，链路无回归。
- [ ] AC7 与现有 F-Droid/Izzy/应用宝链路共存，去重与 source 标记正确，无回归。
- [ ] **AC8 查询并行 + 分源独立超时（迭代补充）**：`searchByName` / `lookup` 各上游**并行**执行，各自独立超时与失败隔离；
      部分上游成功即返回成功结果（**不再抛笼统「请稍后重试」**）；整体响应受统一截止时间约束（默认 10s，可用
      `-Dappmarket.search.timeout.ms` 覆盖），慢/不可达源被「跳过」而不拖垮整体；仅当**全部源都无结果且存在失败源**时，
      才抛出**带各失败源精确信息**的提示（便于定位是哪个源出了问题）。

## Out of Scope（不在本次范围）

- 端侧（Android）按名搜索并直接安装（Aurora Store / Play 代理思路）。
- APK 二进制服务端缓存 / 托管 / 签名与病毒扫描。
- XAPK 拆分 APK 的服务端拆包与重组（下载源仅存直链，安装侧另议）。
- 豌豆荚 / 小米 / 酷安等国产商店非官方逆向接口（合规与稳定性风险高）。

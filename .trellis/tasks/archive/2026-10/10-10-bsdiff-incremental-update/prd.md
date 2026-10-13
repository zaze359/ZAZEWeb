# 需求：APK 增量更新 —— 服务端差分包生成与合并

> 任务：`10-10-bsdiff-incremental-update`（复杂任务）。本文件只写**需求、约束、验收**；技术设计见同目录 `design.md`，执行计划见 `implement.md`。

## 目标与用户价值

为应用市场提供**服务端侧的二进制增量更新能力**：给定同一个应用的「旧版本 APK」与「新版本 APK」，
用 bsdiff 算法算出一份很小的**差分包（patch）**，下发后由客户端（未来的学生端 App，本仓库范围外）
基于已安装的旧 APK 还原出新 APK，从而把「整包下载」降级为「差量下载」，显著节省带宽与时延。

本仓库交付的两条核心能力（均**服务端实现**）：

1. **生成（diff）**：`bsdiff(old.apk, new.apk) → patch`（差分包）。
2. **合并（patch / bspatch）**：`bspatch(old.apk, patch) → reconstructed.apk`。
   在**服务端**主要用于「生成后立即自校验」——断言还原出的新包与原始新包**逐字节一致**（SHA-256 相等），
   作为差分包可信下发的硬门槛；同时也作为独立管理工具对外暴露。

> 说明：用户已明确「**客户端不在此项目考虑中**」——真正在手机上跑 bspatch、调起安装器的那一侧不在本仓库。
> 本任务把「生成 + 合并」两个函数都作为服务端能力落地，并提供一个供未来客户端调用的**差分包下发接口（契约）**。

## 已确认的技术事实（来自对仓库的探查）

- 仓库 `zaze-server` 是 Spring Boot 2.7.18（**JDK 11** / Kotlin 1.6.21）多模块 Gradle 工程；
  既有特性模块 `feature:appmarket` 已具备应用/版本/下载源领域模型。
- `AppVersion`（`feature/appmarket/.../pojo/AppVersion.kt`）只存**版本元数据**（versionName / versionCode /
  sizeMb / changelog），**不落盘 APK 字节**；版本对应的实际安装包通过 `DownloadSource.downloadUrl`
  （外链，F-Droid / APKPure / Aptoide / 应用宝等）引用。collector 已有基于 `extClient` + `FileUtil.readByBytes`
  拉取下载源字节的能力，可直接复用。
- 既有「APK 不上传服务器」约定（见 `09-28-apk-inspector` prd）针对的是**管理后台分析第三方包**的场景，
  与本功能无关——本功能操作的 APK 来自应用**自有分发源**（`DownloadSource`），属正常分发链路。
- 鉴权现状：`/admin/**` 需 ADMIN；`/api/v1/appmarket/**` 登录即可（沿用既有 `AuthInterceptor`）。
- 依赖管理约定：被 BOM 管理且直接用到的库要**显式声明版本**（`okhttp` 等先例）；`commons-compress`
  不在 Spring Boot BOM 内，需手动钉版本。

## 需求

- **R1 生成能力**：服务端可对两个 APK 文件执行 bsdiff，产出差分包文件；差分包须采用**标准 bsdiff 格式
  （magic `BSDIFF40` + bzip2 压缩流）**，保证未来任何标准 `bspatch`（含 C 版）都能还原（契约约束，见 R8）。
- **R2 合并能力**：服务端可对「旧 APK + 差分包」执行 bspatch，还原出新 APK 文件。
- **R3 服务端自校验（核心正确性门槛）**：每次生成差分包后，**必须**在服务端立即用 R2 把它合并回去，
  并断言 `SHA-256(reconstructed) == SHA-256(new)`；不一致则视为生成失败，**不缓存、不下发**该 patch。
- **R4 按需实时 + 缓存**：差分包**运行时按需生成**（请求到来时才计算），对同一 `(oldVersion, newVersion)`
  只算一次，结果写入**文件级缓存**；后续相同请求直接命中缓存返回，不重复计算。
- **R5 APK 字节来源**：old/new APK 从 `DownloadSource.downloadUrl` 拉取（复用 collector 能力），
  拉到本地后做**临时缓存**（带过期清理），不长期落盘整包。
- **R6 下发契约（供未来客户端）**：提供 `GET /api/v1/appmarket/incremental-patch` 接口，
  入参 `(appId, fromVersionId, toVersionId)`，按需生成/命中缓存后以**二进制流**返回差分包，
  并在响应头带 `X-Old-Sha256` / `X-New-Sha256` 供客户端校验；
  当无法生成（超体积阈值 / 上游不可达 / 校验失败）时返回**优雅降级**（`Response(200, null, "请下载完整 APK")` 风格），不得 500。
- **R7 管理端可观测**：管理后台提供接口，可**手动触发生成**并拿到元数据（old/new/patch 大小、压缩比、哈希、
  是否命中缓存）、**下载**差分包、**触发自校验**、**列出已缓存的差分包**。
- **R8 格式兼容硬约束**：差分包一律使用 **bzip2** 压缩（jbsdiff 默认即 bzip2，且绝不能切到 gz/xz/pack200），
  以保持与标准 C 版 `bspatch` 的二进制兼容——这是未来客户端能离线合并的前提。
- **R9 资源护栏**：bsdiff 内存消耗约 `max(17·n, 9·n+m)`（n=旧包，m=新包）。必须设
  **单包体积上限**（超阈值直接走 R6 降级，不进 diff）与 **diff 超时**（超时返回错误信封，不挂起请求线程）。
- **R10 零鉴权常量改动 / 零 DB schema 改动**：复用既有 `/admin/**` 与 `/api/v1/appmarket/**` 鉴权；
  差分包缓存走**文件系统**（非 JPA 实体），不新增数据库表。
- **R11 外部来源「下载源」必须是真实 APK 直链**：`DownloadSource.downloadUrl` 须为可服务端直接下载的
  APK（`.apk`/容器）URL；应用宝等商店的**详情页链接仅作 `officialUrl`**，不得作为 `downloadUrl`
  （否则 bsdiff 会拉到 HTML 而非安装包，产生坏数据）。来源策略参照开源项目 Obtainium 的多源直链机制
  （应用宝 `a.app.sj.qq.com` 接口、APKPure、Aptoide、Uptodown；APKMirror 仅给版本元数据、不能直接出包）。

## 验收标准

- **AC1（正确性 · 核心）**：对任意可获取的两个版本 APK，`diff` 后 `patch` 还原出的文件与原始新包
  **SHA-256 逐字节一致**；服务端在生成流程中对每一份 patch 都执行此校验并断言通过（R3）。
- **AC2（增量有效性）**：对同一个应用的相邻版本（如 v1→v2），差分包体积**显著小于**完整新包
  （压缩比 `patchSize / newSize` < 1，典型 < 0.5 对资源/代码小改），并在管理端元数据里可见该比值。
- **AC3（缓存命中）**：对同一 `(old,new)` 连续请求两次，第二次**命中缓存**（返回 `cached=true`，
  且生成计数不增长 / 无重复 diff 计算开销）。
- **AC4（管理端接口）**：`/admin/incremental-update/diff` 返回完整元数据；`/download` 返回正确字节流；
  `/verify` 返回 `pass=true` 与三处哈希；`/patches` 能列出已缓存项。
- **AC5（下发契约与降级）**：`/api/v1/appmarket/incremental-patch` 正常返回差分包字节 +
  `X-Old-Sha256` / `X-New-Sha256` 头；当体积超阈值或上游不可达时返回优雅降级信封（非 500）。
- **AC6（体积护栏）**：任一 APK 超过阈值（如 200MB，可配）时，diff 被跳过并明确报告「建议下载完整 APK」，
  **不发生 OOM、不出现请求挂起**。
- **AC7（超时护栏）**：diff 超过配置超时（如 5min）时返回错误信封，请求线程不挂起。
- **AC8（格式兼容）**：生成的差分包首字节为 `BSDIFF40` 且内部为 bzip2 流；文档中注明
  该 patch 可被标准 C 版 `bspatch` 还原（未来客户端兼容性）。
- **AC9（鉴权与边界）**：管理端接口未登录被重定向 `/login`、非管理员被拒；下发接口未登录被拒；
  **不新增任何鉴权常量、不改 `AuthInterceptor`**。
- **AC10（构建与依赖）**：`./gradlew bootJar`（JDK 11）通过；新依赖 `jbsdiff` + `commons-compress`
  **显式钉版本**；H2 冒烟可启动并实际跑通一次真实 APK 的 diff→patch→SHA 校验闭环。
- **AC11（无侵入）**：**不新增数据库表/字段**；差分包与临时 APK 均存于可配置目录（默认 `./data/incremental/`），
  不污染既有 `appmarket` 业务数据；回滚即删除这些新增文件/目录。

- **AC12（外部来源 APK 直链）**：应用宝等来源的 `DownloadSource.downloadUrl` 为真实 APK 直链（非商店详情页），
  `toMyAppPreview`/`lookup`/导入后可直接服务端下载；应用宝详情页仅作 `officialUrl`；APKMirror 仅贡献版本号、
  `downloadUrl` 恒空不写脏源；Uptodown 等不可达时优雅降级（不 500）。`appmarket_seed.json` 不再有「详情页当 downloadUrl」的条目。

## Out of Scope

- **Android 客户端实现**：NDK/JNI 编译 `bspatch.c` + bzip2 为 `.so`、Java 层 native 桥、合并后安装、
  未知来源权限、分区存储、`PackageInstaller` Session——均属学生端 App 仓库，本任务不做。
- **在设备上真正应用差分包**：本仓库的「合并」仅用于服务端自校验与独立工具。
- **长期落盘完整 APK**：APK 仅为 diff 目的做临时缓存并过期清理，不做永久存储。
- **重新签名**：服务端无需对还原出的新包签名；该职责在客户端安装环节（仓库外）。
- **上游 APK 不可变性保证**：生产环境「客户端已装旧包字节 == 服务端 diff 所用旧包字节」依赖上游分发源
  的 immutability（如 CDN 对象版本钉死）。本任务**记录该前提为风险**，不解决跨仓库一致性。
- **多源/多 ABI 拆分（App Bundle / split APK）精细化 diff**：首版只对单个 base APK 做整包 diff；
  若未来需按 split 分别 diff，单独立任务。

## 关键决策（已定稿）

- **库选型（首选）**：`io.sigpipe:jbsdiff:1.0`（malensek/jbsdiff，Apache-2.0，已上 Maven Central，
  同时提供 `diff` / `patch`，默认 bzip2）。**实现阶段须验证其在 JDK 11 上可加载运行**
  （见 design §4 / implement P1）；若其字节码目标 > JDK 11，则退回 `joelcho/bsdiff-java`
  （v4.3 移植，NIO 省内存，经 jitpack 引入）或本地 shade 一个 JDK11 构建作为兜底。
- **模块落点**：在既有 `feature:appmarket` 内新增包 `com.zaze.server.feature.appmarket.incremental`，
  **不新建 Gradle 模块**（避免与 appmarket 的实体/仓库产生循环依赖与额外接线成本）。
- **缓存介质**：差分包与临时 APK 走**文件系统**（可配置根目录 + TTL 清理），**不引入 JPA 实体**，
  以满足「零 DB schema 改动」与最小侵入。
- **来源与「APK 不上传」约定不冲突**：本功能 APK 取自应用自有 `DownloadSource`，属分发链路，
  与分析第三方包的场景无关。

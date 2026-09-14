# PRD — 从 APK 解析导入国内应用

> Task: `09-15-cn-store-metadata` · Parent: `09-12-app-market` · Module: `feature/appmarket`
> 状态：Phase 1 规划完成，待最终评审

## 目标与用户价值

让微信、QQ、抖音等国内主流应用能被添加进应用市场门户，并带上**真实元数据**
（包名、版本名、版本号、应用图标），替代当前「人工整理 JSON + 版本占位 + favicon 外链」的做法。

解决的问题：管理后台的「导入外部资源」只接了 F-Droid 与 IzzyOnDroid，二者仅收录开源应用，
国内应用搜不到也导不进；而现有 41 个国内应用全部来自 `appmarket_seed.json` 人工整理，
版本号是占位的（`versionName="官方最新版"`、`versionCode=0`），且该种子**只在空库时写入**，
对已有数据库不生效。

## 已确认事实（代码库与调研证据）

1. **前端可解析 APK**：`app-info-parser`（MIT，支持 Browser 除 IE）输入 `File`/`Blob`，
   输出 `package`、`versionName`、`versionCode`、`application.label`、`icon`（base64 data URI）。
   内部用 isomorphic-unzip 解包 + 自带 binary XML 解析器，无原生依赖。
2. **CORS 硬约束**：浏览器 `fetch()` 跨域获取 APK 会被拦截，官网/应用宝均不返回 CORS 头。
   因此**只有本地文件（File/Blob）能直接前端解析**——这是本方案采用「本地选文件」的根本原因。
3. 现有能力可复用：`AppMarketCollector.ensureStoreSources()` 已能按 packageName 幂等推导
   应用宝详情页（`https://sj.qq.com/appdetail/{pkg}`）；`ExternalAppPreview.iconSrc` 已能承载 base64。
4. `App.iconUrl` 当前为普通 `@Column`（默认 varchar(255)），**存 data URI 需扩为 TEXT**；
   且 `spring.jpa.hibernate.ddl-auto=update` **不会修改已有列类型**，需手动 DDL（见 design.md）。
5. `App.packageName` 数据库层可空、无唯一约束，但**业务上是核心标识**
   （`findByPackageName` 判重、`ensureStoreSources` 推导下载源用）。
6. 现有 `importApp()` 在包名已存在时抛「包名已存在，无需重复导入」——本需求需改为追加版本。
7. 外部请求超时基准为 60s（`ok.http.*`），新增服务端调用须显式覆盖 connect/read/write/call 四类。
8. 第三方前端库统一放 `src/main/resources/static/vendor/`（已有 bootstrap / jquery / bootstrap-table），
   本地引入、不依赖外网 CDN。
9. 父任务硬约束：**不做自有二进制存储**，只存元数据与外链 URL。

## 需求

- **R1** 管理后台新增「从 APK 导入」入口，管理员选择本地 APK 文件。
- **R2** 前端解析 APK，自动填充表单：包名、应用名、版本名、版本号、图标（data URI）。
- **R3** 提交后：应用不存在则创建；**已存在则比对版本号，不同则追加新版本记录**，相同则提示「已是最新」。
- **R4** 复用 `ensureStoreSources()`，按包名自动补应用宝详情页作为下载源。
- **R5** 解析出的图标以 data URI 存入 `App.iconUrl`（字段扩为 TEXT）。
- **R6** 解析过程有加载态；非 APK / 损坏文件给出明确错误提示，不产生脏数据。
- **R7** 大文件保护：限制可选 APK 大小，超限或解析超时给出提示，避免浏览器卡死。
- **R8** APK 文件本身不上传服务端，仅解析出的元数据提交入库。

## 验收标准

- **AC1**：选择本地 APK（如 QQ 安装包）后，表单自动填充 `packageName` / 名称 /
  `versionName` / `versionCode`，并展示解析出的图标。
- **AC2**：导入库中不存在的应用后，详情可见**真实版本号**（非「官方最新版」），并带应用宝下载源。
- **AC3**：对已存在应用（如 seed 中的 QQ）导入更高版本 APK，**新增一条版本记录，原有数据不丢**。
- **AC4**：导入相同版本号的 APK，提示「已是最新」，不产生重复版本与下载源。
- **AC5**：选择非 APK 或损坏文件时给出明确错误，不写入任何数据。
- **AC6**：门户列表能正常渲染 APK 解析出的图标（data URI）。
- **AC7**：APK 文件不上传服务端（请求体仅含元数据，无 APK 二进制）。

## 非目标

- 不接国内商店抓取接口（应用宝 / 华为 / 酷安等），规避抓取与合规风险。
- 不做 APK 二进制存储与下载代理（后续「上传 APK」需求再评估）。
- 不提供按应用名搜索国内应用的能力（本方案无搜索上游）。
- 不改变现有 F-Droid / IzzyOnDroid 导入链路，二者并存。
- 不做定时自动检查版本更新。

## 已决策

- **D1**：走「官网 + 解析 APK」路线，不接商店抓取接口。
- **D2**：APK 解析放在**前端**（浏览器），服务端不解析、不保存 APK。
- **D3**：后续会有「本地上传 APK」需求，本方案的解析能力为其复用铺垫。
- **D4**：流程为「本地选 APK → 前端解析 → 自动填充 → 按包名推导应用宝详情页 → 提交入库」。
- **D5**：包名已存在时**追加新版本**（而非报错或覆盖）。
- **D6**：图标以 **data URI 入库**，需将 `iconUrl` 扩为 TEXT。

## 范围外但需记录

- 后续「上传 APK 并存储」若落地，将突破「不做二进制存储」约束，需单独立项评估存储与合规。

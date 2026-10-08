# 需求：APK 解析内核 + 独立「APK 分析」查看器

> 父任务：`09-28-apk-inspector`。本子任务是三个子任务里**首个可独立验收**的交付物：
> 它同时落地「共用解析内核」与「全量展示宿主」，后两个子任务都复用它。

## 目标与用户价值

浏览器选一个本地 APK，即可看到一份完整、结构化、可导出的分析报告：

- 我看到的是什么包（包名 / 应用名 / 版本 / 文件指纹 / 体积）
- 它能装在什么设备上（minSdk / targetSdk / 硬件特性 / 屏幕支持）
- 它要了哪些权限（完整清单 + 危险标记 + 中文名）
- 包体是怎么构成的（ABI 架构 / DEX 数量 / 资源密度 / 体积占比）

用途：收录前的把关，以及为门户应用详情撰写「兼容性/权限」信息提供依据。

## 已确认的技术事实

- `app-info-parser`（vendor，`src/main/resources/static/vendor/app-info-parser/app-info-parser.min.js`）
  的 APK 解析返回**清单根对象**（不只是当前用到的 5 个字段）：`package` / `versionName` / `versionCode` /
  `usesSdk` / `usesPermissions` / `usesPermissionsSDK23` / `permissions` / `permissionTrees` /
  `usesFeatures` / `supportsScreens` / `compatibleScreens` / `supportsGlTextures` / `usesConfiguration` /
  `instrumentation` / `application`（含 `label` `icon` 等），另注入 `icon`（base64 data URI）。
- 该库同时内建 ZIP 读取与 `resources.arsc` 映射；加载方式为按需插入 `<script>`，
  全局对象 `window.AppInfoParser`（当前实现见 `ApkImportModal.vue` 的 `loadParser()`）。
- 库的 APK 模块调用清单解析时传入
  `ignore: ["application.activity","application.service","application.receiver","application.provider","permission-group"]`
  —— 组件清单与权限组**拿不到**（见 Out of Scope）。
- `label` 依赖 `resources.arsc` 映射；映射失败时可能是 `@string/xxx` 这类资源引用而非真实名称。
- 前端无测试框架；门禁为 `npm run typecheck` + `npm run build`（`frontend/package.json`、`frontend/vite.config.ts`）。
- 现有派生/格式化工具在 `frontend/src/utils/appMeta.ts`；新增工具函数前必须先查 `frontend/src/utils/`。

## 需求

- **R1 解析内核**：新增 `frontend/src/utils/apk/`，对外暴露
  `analyzeApk(file: File, opts?: { onProgress?, computeHash? }): Promise<ApkAnalysis>`，
  类型与模块划分见本任务 `design.md`（父任务 prd.md 已冻结上层字段名）。
- **R2 清单维度**：从 `app-info-parser` 结果归一化出 SDK（minSdk/targetSdk/maxSdk/compileSdk）、
  权限（`uses-permission` + `uses-permission-sdk-23` + 自定义 `permission`）、
  硬件特性（`uses-feature` 及 required 标记）、屏幕与配置支持。
- **R3 权限可读化**：内置权限词典，为常见权限给出中文名、危险等级（`dangerous` / `normal` /
  `signature` / `unknown`）与所属分组；**未收录的权限不得留空**，回退显示原始权限名并在 UI 标记为未收录。
- **R4 文件构成**：自研轻量 ZIP **中央目录**读取器（只读目录，不解压业务数据），产出
  条目总数、压缩/解压总字节、DEX 清单、ABI 分组（`lib/<abi>/`）、资源密度分组、Top 占用分组，用于展示占比。
- **R5 文件指纹**：对文件计算 SHA-256（`crypto.subtle.digest`）；超过阈值的大包跳过并写入
  `basic.sha256SkippedReason`，不得因此阻塞或卡死。
- **R5b 图标透出**：内核必须在 `basic.iconDataUri` 返回 APK 图标（base64 data URI），
  供导入弹窗（`09-28-apk-import-detail`）直接提交；体积阈值判定留在调用方，内核不做截断。
- **R6 查看器**：新增独立「APK 分析」查看器页面（管理后台专属路由 `/admin/apk`，仅管理员；
  入口在 `AdminView.vue` 顶部操作区）：选文件 → 分步进度 → 全量详情
  （含权限搜索/过滤、文件构成明细、原始清单折叠视图）→ 「复制 JSON」/「下载 JSON」/「重新选择」。
- **R7 面板组件化**：详情 UI 抽成可复用组件（`variant: 'compact' | 'full'`），
  `09-28-apk-import-detail` 直接复用 compact，不得另写一套渲染。
- **R8 降级与告警**：任一子步骤失败（清单解析失败、ZIP 目录读取失败、`label` 是资源引用等）
  只降级该维度，把可读原因写入 `warnings`，UI 固定展示告警区；非 APK 文件要给出明确提示。
- **R9 vendor 资产复用而非改动**：不修改 `app-info-parser.min.js`；
  其按需加载逻辑从 `ApkImportModal.vue` 抽到内核模块中复用，消除重复实现。

## 验收标准

- **AC1** 打开查看器选一个正常 APK（含 ≥100MB 的包），能看到 basic / sdk / permissions /
  features / screens / files 六个分区，全部字段有值或明确标注「未解析」；页面不卡死。
- **AC2** 对 3 个样本 APK 核对：`minSdk` / `targetSdk` 与 `aapt dump badging`（或 `apkanalyzer manifest`）输出一致。
- **AC3** 权限条数等于清单中 `uses-permission` + `uses-permission-sdk-23` 去重后的条数；
  危险权限有醒目标记与中文名；词典未收录的权限显示原始权限名，不出现空白行。
- **AC4** 文件构成：条目总数与 `unzip -l <apk> | tail -1` 一致；ABI 分组正确归并
  `arm64-v8a` / `armeabi-v7a` / `x86` / `x86_64`；DEX 数量与包内 `classes*.dex` 一致；
  各分组解压字节之和等于条目解压总和（允许 <1% 偏差并说明口径）。
- **AC5** 文件指纹：常规包显示 SHA-256 且与 `shasum -a 256` 一致；超大包给出「已跳过大文件指纹」说明而非静默为空。
- **AC6** 导出 JSON 的内容与界面展示一致（同一份 `ApkAnalysis` 序列化）。
- **AC7** 破坏性输入不崩溃：损坏 APK / 加固包 / 改名的非 APK 文件 / 空文件，均给出可读原因或降级结果。
- **AC8** 面板组件以 `variant` 区分密度，compact 与 full 共用同一份数据与同一套分组渲染。
- **AC9** `npm run typecheck` 与 `npm run build` 通过，构建产物正常落到 `src/main/resources/static/`。
- **AC10** 无新增后端接口调用、无数据库改动、无鉴权常量改动（本任务不碰任何后端文件）。
- **AC11** `basic.iconDataUri` 返回的图标与既有弹窗解析结果一致（同一 APK 的 base64 内容相同），
  调用方无需为图标再解析一次 APK。
- **AC12** 鉴权符合预期：未登录访问 `/admin/apk` 被重定向到 `/login`，非管理员被拒（沿用既有 `/admin/**` 行为）。

## Out of Scope

- **组件清单（Activity / Service / Receiver / Provider）与权限组**：vendor 库硬编码 ignore，
  需给压缩产物打补丁或自研 AXML 解析器，本任务不做（父任务已记录）。
- 组件 intent-filter / 四大组件导出安全排查。
- 签名分析本体：本任务只保证 `ApkAnalysis.signature` 字段存在并返回 `null`，
  由 `09-28-apk-signature` 填充；UI 在 `null` 时展示「未分析」占位。
- 结果落库、门户前台入口、IPA、DEX 内容扫描、加固包专项识别。
- 在服务器端解析 APK（违反既有「APK 不上传」约定）。

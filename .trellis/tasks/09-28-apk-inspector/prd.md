# 需求：APK 详情分析能力增强（父任务）

## 目标与用户价值

管理后台现在只能从 APK 里拿到 6 个字段（包名 / 应用名 / versionName / versionCode / 图标 / 文件大小），
管理员在收录应用时**无法判断这个包到底装了什么**。本任务把 APK 分析从「抠几个字段用于导入」
升级为「一份可查看的完整 APK 档案」，覆盖四个维度：

1. **SDK 与兼容性** —— minSdk / targetSdk / maxSdk、硬件特性（uses-feature）、屏幕与配置支持
2. **权限清单** —— 完整权限列表 + 危险权限标记 + 中文名
3. **文件构成** —— ABI 架构、DEX 数量、资源密度、各分组的体积占比
4. **签名与完整性** —— v1/v2/v3 签名方案检测 + 证书 SHA-256 指纹

价值：收录质量把关（知道这是不是加固包 / 敏感权限包 / 什么架构）+ 门户应用详情的
「兼容性/权限」信息有依据可写，不再靠人工查应用商店。

## 现状（已确认的代码事实）

- `ApkImportModal.vue` 在浏览器本地用 `app-info-parser`（`src/main/resources/static/vendor/app-info-parser/`，
  459KB，按需加载）解析 APK，只取 `package` / `application.label` / `versionName` / `versionCode` / `icon`，
  `sizeMb` 由浏览器 `File.size` 换算。
- APK **不上传服务器**是既有硬约定（`vendor/app-info-parser/README.md`、`AdminDtos.kt` 的 `ApkImportRequest` 注释）。
- vendor 库的清单解析（min.js 内 `_parseManifest`）带 `ignore: ["application.activity",
  "application.service", "application.receiver","application.provider","permission-group"]`，
  **组件清单与权限组被硬编码丢弃**；`usesSdk` / `usesPermissions` / `usesPermissionsSDK23` /
  `usesFeatures` / `supportsScreens` / `usesConfiguration` / `application` 等字段仍然完整返回。
- vendor 库还内建 ZIP 读取与 `resources.arsc` 资源映射（用于把 `@string/app_name` 解析成真实应用名）。
- 前端无测试框架，门禁是 `npm run typecheck`（vue-tsc --noEmit）+ `npm run build`（产物落
  `src/main/resources/static/`，见 `frontend/vite.config.ts`）。
- 鉴权（`AuthInterceptor.kt`）：`/admin/**` 需 ADMIN；`/appmarket/**` 与 `/api/v1/appmarket/**` 登录即可。

## 需求（本任务要交付的行为）

- **R1** 选一个本地 APK，能在浏览器内得到一份结构化的完整分析结果（`ApkAnalysis`），
  上述四个维度全部填齐；单个维度解析失败时降级并给出可读原因，不整体失败。
- **R2** 结果有两个承载形态，且**共用同一份解析内核与同一个详情面板组件**：
  - 导入弹窗内嵌**紧凑**详情（导入前把关）；
  - 独立「APK 分析」查看器展示**全量**详情（含原始清单、权限过滤、导出 JSON）。
- **R3** 详情面板需明确区分「已解析」与「未解析/降级」的信息，**不得把未校验的签名当作已验证**。
- **R4** 既有导入链路行为不变：`ApkImportInput` 字段、图标 100KB 阈值、200MB 体积上限、
  `POST /api/v1/appmarket/admin/import-from-apk` 请求体一律不动。
- **R5** 零后端改动、零数据库改动、零鉴权常量改动（本任务纯前端 + 复用既有 vendor 资产）。

## 任务拆解（task map）

| 子任务 | 交付物 | 依赖 |
|--------|--------|------|
| `09-28-apk-inspect-core` | 解析内核（清单全字段 + 权限词典 + ZIP 文件构成）**与**独立「APK 分析」查看器 | 无（首个可验收交付物） |
| `09-28-apk-import-detail` | 「从 APK 导入」弹窗内嵌紧凑详情 + 导入链路零回归 | 依赖 core 的解析内核与面板组件 |
| `09-28-apk-signature` | 签名方案探测 + 证书 SHA-256 指纹，接入内核与两处 UI | 依赖 core 的 `ApkAnalysis.signature` 契约与 ZIP 读取能力 |

## 跨任务共享契约（冻结）

`09-28-apk-inspect-core` 定义并于 `frontend/src/utils/apk/types.ts` 落地 `ApkAnalysis`；
另两个子任务只消费该类型，**不得**各自复制一份结构。上层字段名冻结为：

```
ApkAnalysis = { basic, sdk, permissions, features, screens, files, signature, warnings }

basic       : fileName, fileSizeBytes, packageName, label, versionName, versionCode,
              versionCodeMajor?, iconDataUri?, sha256?, sha256SkippedReason?
              // iconDataUri 必须由内核返回：导入弹窗（child 2）依赖它提交 ICON，
              // 不允许弹窗为了头像再解析一次 APK（等于二次整包解压）
sdk         : minSdk?, targetSdk?, maxSdk?, compileSdk?
permissions : total, dangerousCount, customCount, items[{ name, cn, level, group?, custom?, maxSdkVersion? }]
features    : items[{ name, required }]
screens     : supportsAnyDensity?, compatibleScreens[], supportsGlTextures?
files       : entryCount, compressedBytes, uncompressedBytes, dexFiles[], abis[],
              densities[], topGroups[], nativeLibCount
signature   : ApkSignatureSummary | null      // null 表示尚未分析（child 1 阶段）或解析不出
warnings    : string[]                         // 降级/异常的可读说明，UI 必须展示
```

`signature` 的详细结构由 `09-28-apk-signature` 在自己的 `design.md` 中补充，但**字段挂载点与命名
必须在 core 阶段就定稿**，避免后两个子任务改类型。

## 验收标准（父任务集成验收）

- **AC1** 弹窗与查看器展示的是同一份 `ApkAnalysis`，同一 APK 在两处的
  基本信息 / SDK / 权限计数 / 构成摘要 / 签名摘要数值完全一致（仅信息密度不同）。
- **AC2** 四个维度均可观测：SDK 数值与 `aapt dump badging` 对得上；权限列表含中文名与危险标记；
  文件构成含 ABI/DEX/体积占比；签名区显示方案与证书指纹（`09-28-apk-signature` 完成后）。
- **AC3** 导入链路零回归：从 APK 导入一个新应用，落库字段、图标处理、错误提示与改动前一致。
- **AC4** 破坏性输入（损坏包 / 加固包 / 超大包 / 非 APK 文件）不白屏、不卡死，给出可读原因。
- **AC5** `npm run typecheck` 与 `npm run build` 通过；产物进 `src/main/resources/static`。
- **AC6** 新增能力不引入后端接口调用、数据库字段与鉴权常量改动（R5）。

## Out of Scope

- **组件清单（Activity / Service / Receiver / Provider）**：vendor 库硬编码 ignore，取出需要
  给压缩产物打补丁或自研 AXML 解析器；本任务不做。若后续要，单独立任务。
- 把分析结果**落库**到 `AppVersion`（如 minSdk / 权限），以及门户应用详情页新增字段展示 —— 需数据库迁移，另立任务。
- **签名有效性校验**（v2/v3 digest 与 APK 内容一致性、证书链信任）——成本极高，本任务只做方案检测与证书展示。
- IPA（iOS 包）分析；DEX 反编译 / 字符串扫描 / 加固识别；上传 APK 到服务器解析（违反既有约定）。
- 门户（前台）入口的分析能力。

## 关键决策（已定稿）

- **独立查看器的入口**：新增**管理后台专属路由 `/admin/apk`**（`meta.theme = 'admin'`），仅管理员可见/可用；
  入口按钮挂在 `AdminView.vue` 顶部操作区（与「从 APK 导入」并列）。
  - 理由：`AuthInterceptor` 的 `ADMIN_PATTERNS` 已包含 `/admin/**`，**无需改动鉴权常量**，也不扩大可访问面。
  - 代价：普通登录用户无法使用 —— 明确接受；门户入口已在 Out of Scope 中排除。
  - 附带收益：本任务**完全不碰后端代码**（R5 从「不建议改」升级为「零改动」）。

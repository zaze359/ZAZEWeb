# 需求：「从 APK 导入」弹窗内嵌 APK 详情

> 父任务：`09-28-apk-inspector` ｜ 依赖 `09-28-apk-inspect-core`（解析内核 + 详情面板组件）
> 本任务是**集成型轻量任务**：不新增解析逻辑，只把内核产出的 `ApkAnalysis` 接进现有弹窗。

## 目标与用户价值

现在管理员点「从 APK 导入」只能看到 6 个字段（名称/包名/版本/大小/图标），确认导入前**看不到这个包要了什么权限、
兼容到什么版本、是什么架构**。本任务让弹窗在导入前就地展示这些关键信息，避免把可疑包（高敏感权限 / 已停止维护的
低 targetSdk / 只有 x86 架构）收进图鉴。

同时保留一条通往完整分析的通路，不必重新选文件。

## 现状（已确认的代码事实）

- `frontend/src/components/admin/ApkImportModal.vue`（155 行）：`Teleport to body` 的居中弹窗，
  `max-w-md`，状态机 `idle | loading | parsing | done | error`；`onFile()` 里动态插入 `<script>` 加载
  vendor 解析库 → `new AppInfoParser(f).parse()` → 填 `parsed: ApkImportInput`；
  写库走 `api.admin.importFromApk(parsed)`（`frontend/src/api/client.ts`）。
- 导入字段与阈值：`APK_MAX_BYTES = 200MB`、`ICON_MAX_BYTES = 100KB`（超限则 `iconDataUri = null`
  并提示「图标过大，仅预览不入库」）；`ApkImportInput = { packageName, name, versionName, versionCode, iconDataUri, sizeMb }`。
- 该文件已被 `09-28-apk-inspect-core` 触及一次（把 `loadParser()` 抽到共用内核模块，等价重构）。
- 入口在 `frontend/src/views/AdminView.vue`（`apkOpen` 控制，`@imported` 回调刷新列表）。

## 需求

- **R1 内嵌详情**：解析成功后，在原有「图标 + 名称 + 包名 + 版本 + 大小」下方，就地渲染
  `ApkAnalysisPanel variant="compact"`（来自 core），展示 SDK 与兼容性、权限（总数 + 危险项 Top5）、
  文件构成摘要（ABI / DEX 数量 / 体积）、签名摘要、`warnings` 告警区。
- **R2 数据来源统一**：弹窗改用内核的 `analyzeApk(file, { onProgress })` 取全部信息，
  **不再单独调用** `AppInfoParser`；`parsed`（`ApkImportInput`）由同一份 `ApkAnalysis` 派生
  （`iconDataUri` 取自 `basic.iconDataUri`，`sizeMb` 取自 `basic.fileSizeBytes`）。
- **R3 查看完整分析**：面板底部提供「查看完整分析」，点击后**在同一个弹窗内**把面板切到
  `variant="full"`（弹窗加宽 + 内部滚动）。**不跳路由** —— 跨路由无法传递 `File`，
  跳转会导致用户必须重新选包。
- **R4 导入链路零回归**：`ApkImportInput` 字段、请求体、`ICON_MAX_BYTES` 判定、
  200MB 上限、各状态文案与按钮禁用逻辑全部保持现有行为；`@imported` / `@cancel` 事件语义不变。
- **R5 布局不失控**：弹窗需容纳额外内容 —— 默认宽度上调并设最大高度 + 内部滚动；
  compact 视图在 768px 高度窗口内不出现内容被裁切、不出现双滚动条。
- **R6 降级**：`analyzeApk` 因清单解析失败而抛错时，保持现有错误提示形态
  （「APK 解析失败：文件可能损坏或经过加固」）且**不显示**半截面板；`warnings` 非空时正常展示面板 + 告警区。

## 验收标准

- **AC1** 选一个正常 APK：解析完成后弹窗内出现 compact 详情（SDK / 权限计数+危险项 / 构成摘要 / 签名摘要），
  且与查看器（`ApkAnalyzerView`）对同一 APK 的数值完全一致。
- **AC2** 点「查看完整分析」：同一弹窗内切换为 full 视图，展示权限全文、文件构成明细、原始清单，
  **无需重新选文件**；可再切回 compact。
- **AC3** 导入行为零回归：对同一 APK，改动前后提交的 `POST /api/v1/appmarket/admin/import-from-apk`
  请求体逐字段一致（`packageName` / `name` / `versionName` / `versionCode` / `iconDataUri` / `sizeMb`）。
- **AC4** 图标 >100KB 的包仍显示「图标过大（超过 100KB），仅预览不入库」且 `iconDataUri` 不提交。
- **AC5** 损坏包 / 加固包：显示原有错误提示，无面板残留，导入按钮保持禁用。
- **AC6** 弹窗在 1280×720 与 1440×900 两种窗口下：内容完整可见、内部滚动正常、无布局溢出。
- **AC7** `npm run typecheck` 与 `npm run build` 通过。

## Out of Scope

- 新增/修改任何后端接口与提交字段（导入语义完全不变）。
- 在弹窗内做签名有效性校验（child 3 只做方案与指纹展示）、权限中文词典维护（属 core）。
- 弹窗内导出 JSON（导出是查看器的职责）。
- 把详情落库、门户展示。
- 组件清单（父任务已排除）。

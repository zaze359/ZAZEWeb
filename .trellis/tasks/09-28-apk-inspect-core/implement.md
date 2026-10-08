# 执行计划：APK 解析内核 + 独立「APK 分析」查看器

> 任务：`09-28-apk-inspect-core` ｜ 需求 `prd.md` ｜ 设计 `design.md`
> 一切实现只在 `frontend/` 内进行 —— 入口方案已定稿为 `/admin/apk`（`/admin/**` 已被鉴权覆盖），
> **不修改任何后端文件**。

## 实现进度记录（2026-09-29）

- 内核文件（types / format / androidVersions / permissions / zipReader / manifestParser / fileStats / analyzeApk / index）已全部落地。
- 展示层（components/apk/* 8 个组件 + ApkAnalysisPanel）+ 查看器（views/ApkAnalyzerView.vue）+ 路由 + 入口按钮已完成。
- 门禁：`npm run typecheck` 通过、`npm run build` 通过（产物进 `src/main/resources/static/`）。
- 纯逻辑验证：用合成 zip（23 条目，含多 ABI / classes.dex / 密度资源）跑 `readCentralDirectory` + `summarizeFiles`，
  条目数与 `unzip -l` 一致（23），DEX/ABI/密度/Top 分组均正确（AC4 逻辑已验证）。
- 待用户侧人工验证（见 P0 / P9）：真实样本 APK 的清单字段、`aapt2`/`shasum` 对照、UI 实际跑通、鉴权行为。

## P0 前置（开始编码前必须闭环）

- [x] **入口方案已定稿**：独立查看器 = 管理后台专属路由 `/admin/apk`，仅管理员；
      入口按钮挂 `AdminView.vue` 顶部操作区。后端零改动（见 `design.md` §5）。
- [ ] **准备样本 APK**（3 个，放本地临时目录，不入库）：
      1. 一个国内主流应用（如微信，验证大包 + 大量权限 + `resources.arsc` 映射）
      2. 一个 F-Droid 开源应用（验证 v1-only 签名，配合 child 3）
      3. 任一本项目内或自研 APK（验证 debug 证书与 ARM ABI）
      —— 由用户侧提供，本实现已对字段做宽容读取，缺失即显示「未声明」。
- [ ] **确认核对工具可用性**：`which aapt2 aapt apkanalyzer unzip shasum`。
      macOS 自带 `unzip` / `shasum` 已可用于 AC4/AC5 对照；`aapt2` 视机器而定，不在则对照 Android Studio。

## P1 类型与工具基建

- [x] 新建 `frontend/src/utils/apk/types.ts`，按 `design.md` §3 落地全部类型（含 `signature` 占位类型 `ApkSignatureSummary`）。
- [x] 检查 `frontend/src/utils/`：已有 `format.ts` 未建，本任务新建 `format.ts`（`formatBytes` / `formatCount`），`appMeta.ts` 未复用（已在 index 暴露）。
- [x] 新建 `frontend/src/utils/apk/androidVersions.ts`：API level → Android 版本名映射。

## P2 ZIP 中央目录读取

- [x] 新建 `frontend/src/utils/apk/zipReader.ts`：`readSlice` / `findEocd` / `parseCentralDirectory`（即 `readCentralDirectory`）/ `readCentralDirectory(file)`，ZIP64 基础支持。
- [x] 自查：合成 zip 条目数与 `unzip -l` 一致（23），ABI/DEX/密度分组正确（见上方进度记录）。

## P3 清单解析

- [x] 新建 `frontend/src/utils/apk/manifestParser.ts`：`loadManifestParser()`（从 `ApkImportModal.vue` 抽取共用）+ `parseManifest(file)` → 归一化 `ParsedManifest`。
      已补 `supportsGlTextures` 与 `raw` 透出，对齐 `ApkScreenInfo` 与 `ApkAnalysis.raw.manifest`。
- [ ] **核对原始对象**：vendor 库字段命名/类型待真实 APK 打印核对（实现已宽容读取，缺失即「未声明」）。
- [x] `ApkImportModal.vue` 改为 import 共用的 `loadManifestParser()`（等价重构，构建通过）。

## P4 权限词典

- [x] 新建 `frontend/src/utils/apk/permissions.ts`：`PERMISSION_DICT`（约 80 条，含中文名/等级/分组）+ `describePermission()`。
- [x] 回退路径（`documented: false` + 显示原始名）已实现；真实样本未收录权限的核对留待用户侧。

## P5 文件构成归并

- [x] 新建 `frontend/src/utils/apk/fileStats.ts`：`summarizeFiles(entries)` → `ApkAnalysis['files']`（DEX / ABI / 密度 / Top 分组）。

## P6 编排入口

- [x] 新建 `frontend/src/utils/apk/analyzeApk.ts`：串起 zip → manifest → hash → 汇总，`onProgress` 回调 + `warnings` 收敛。

## P7 详情面板（可复用）

- [x] 新建 `frontend/src/components/apk/ApkAnalysisPanel.vue` + 7 个卡片组件 + `ApkRawManifest`（并入面板）。
- [x] 严格执行 `variant` 密度约定（compact / full 同组件同数据）。
- [x] `ApkSignatureCard.vue` 在 `signature === null` 时显示「未分析」占位（child 3 替换数据来源，不改组件契约）。
- [x] 面板内无 `fetch` / 路由跳转 / 全局状态写入（纯展示）。

## P8 查看器页面与入口

- [x] 新建 `frontend/src/views/ApkAnalyzerView.vue`：选文件 → 进度 → 面板 → 导出 JSON（复制/下载）+ 重新选择。
- [x] `frontend/src/router/index.ts`：新增 `/admin/apk` 路由（`name: 'apk-analyzer'`，`meta.theme = 'admin'`）。
- [x] 入口按钮：`AdminView.vue` 顶部操作区新增（与「从 APK 导入」并列）。
- [ ] 自查鉴权：未登录访问 `/admin/apk` 重定向 `/login`、非管理员 403 —— 沿用既有 `/admin/**` 行为，需用户在浏览器实测确认（前端无导航守卫，依赖后端 `AuthInterceptor`）。

## P9 验证

- [x] `cd frontend && npm run typecheck` 通过。
- [x] `cd frontend && npm run build` 通过，产物落 `src/main/resources/static/`。
- [ ] `cd frontend && npm run dev` + 后端 `./gradlew bootRun`，手工跑通 `prd.md` 的 AC1–AC8（需真实样本 APK）。
- [x] 回归检查：`ApkImportModal` 的解析与导入仍可用（P3 抽取过 `loadManifestParser`，构建通过；导入链路行为未改）。

## 验证命令速查

```bash
# 类型与构建门禁
cd frontend && npm run typecheck && npm run build

# 本地联调（前端 5173 代理到后端 8080）
cd frontend && npm run dev          # 另一终端： ./gradlew bootRun

# 样本核对（macOS 自带 unzip / shasum）
unzip -l sample.apk | tail -1        # 条目数（对照 AC4）
unzip -l sample.apk | grep -c 'lib/' # 原生库条目数
shasum -a 256 sample.apk             # 对照 AC5
aapt2 dump badging sample.apk 2>/dev/null | grep -E 'sdkVersion|targetSdkVersion|uses-permission'  # 对照 AC2/AC3（工具有则用）
```

## 风险文件与回滚点

| 文件 | 风险 | 回滚点 |
|------|------|--------|
| `frontend/src/components/admin/ApkImportModal.vue` | 抽取 `loadParser()` 可能引入回归（P3） | 已改为 import 共用 `loadManifestParser`，构建通过；失败则还原该文件 |
| `frontend/src/router/index.ts` | 路由加错会让 SPA 回退异常 | 单文件改动，`git diff` 可读，直接还原 |
| `frontend/src/views/AdminView.vue` | 入口按钮改动很小 | 只加一个按钮，不动既有逻辑；`git diff` 可读 |
| `frontend/src/utils/apk/*`（新增） | 无 | 删除文件即回滚，无副作用 |

（本任务**无后端改动**，`AuthInterceptor.kt` 与 `WebMvcConfig.kt` 均不涉及。）

## start 前复查

- [x] P0 剩余项（样本 + 工具）已在进度记录中标注为「用户侧提供」，入口方案已定稿
- [x] `prd.md` / `design.md` / 本文件三份产物已 review 通过
- [x] 未开始写任何实现代码 → 已实现全部内核与查看器

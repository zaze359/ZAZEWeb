# 执行计划：「从 APK 导入」弹窗内嵌 APK 详情

> 任务：`09-28-apk-import-detail` ｜ 需求 `prd.md`
> 前置：`09-28-apk-inspect-core`（解析内核 + 面板）、`09-28-apk-signature`（签名分析）均已落地并验证。
> 本任务为**集成型轻量任务**：不新增解析逻辑，只把内核产出的 `ApkAnalysis` 接进现有弹窗。
> 状态：**实现完成 + typecheck/build 通过**（2026-10-09）。交互式 AC1–AC6 需浏览器手测。

## P0 前置

- [x] core 已提供 `analyzeApk(file, { onProgress })` 与 `ApkAnalysisPanel`（`variant: 'compact' | 'full'`）。
- [x] 改动前 `ApkImportModal.vue` 版本已在 git 历史中（用于 AC3 逐字段比对）。
- [ ] 对照工具：浏览器 DevTools Network 面板（抓 `import-from-apk` 请求体）—— 待浏览器侧手测。

## P1 切换数据来源（保持行为等价）

- [x] `ApkImportModal.vue`：移除 `loadManifestParser()` + `new AppInfoParser(f).parse()`，改调
      `analyzeApk(f, { onProgress })`；状态机保持 `idle/loading/parsing/done/error`
      （`onProgress.message` 仅作为「正在解析 APK…」后的补充文案，不新增状态）。
- [x] `parsed`（`ApkImportInput`）由 `analysis.basic` 派生，字段来源与改动前一致：
      `packageName= basic.packageName`、`name= basic.label`、

      `versionName= basic.versionName ?? null`、`versionCode= basic.versionCode != null ? Number(...) : null`、
      `iconDataUri= iconTooLarge ? null : basic.iconDataUri || null`、
      `sizeMb= Math.round(file.size/1024/1024)`。
      （`basic` 字段来自同一 vendor 库的清单解析，与旧 `r.package/r.application.label/r.version*/r.icon` 等价 → AC3 不回归）
- [x] `ICON_MAX_BYTES` 判定保留在弹窗（超限 → `iconDataUri=null` + 原提示文案）。
- [ ] 回归自测（导入请求体逐字段比对）：待浏览器侧手测（AC3）。

## P2 内嵌详情面板

- [x] 在原有「图标 + 名称 + 包名 + 版本 + 大小」区块下方插入
      `<ApkAnalysisPanel :analysis="analysis" variant="compact" />`；`analysis===null` 时不渲染。
- [x] 面板告警区（`warnings`）在 compact 下可见（面板内置，无需额外处理）。
- [x] 面板不承载写操作：导入按钮仍是弹窗自己的；面板内无「导入」入口。

## P3 完整分析切换（弹窗内，不跳路由）

- [x] 加 `expanded` 状态：`compact` 底部「查看完整分析 →」→ `expanded=true` → `variant='full'` + 可切回「← 收起」。
- [x] 弹窗容器：`max-w-md` → `max-w-2xl`（compact）/ `max-w-5xl`（full），
      整卡 `flex flex-col` + `max-height:85vh`，中部 `min-h-0 overflow-y-auto`，头部与底部按钮固定可见。
- [x] 关窗（`watch(open)` 复位）一并复位 `file/analysis/expanded/错误态`。

## P4 降级与边界

- [x] `analyzeApk` 抛错 → 沿用原 `catch` 文案「APK 解析失败：文件可能损坏或经过加固」+ `status='error'`，`analysis` 置空（不显示半截面板）。
- [x] 拿不到包名 → 报「未能解析出包名，无法导入」+ `status='error'`（与原行为一致）。
- [x] 非 `.apk`/空文件/>200MB：沿用原拦截与提示。

## P5 验证

- [x] `cd frontend && npm run typecheck`：通过（vue-tsc --noEmit）。
- [x] `cd frontend && npm run build`：通过（102 模块，产物进 `src/main/resources/static`）。
- [ ] 手工跑 `prd.md` AC1–AC6（需浏览器，本机无 GUI 环境）：
      - AC1 选正常 APK：弹窗内 compact 详情（SDK/权限计数+危险项/构成/签名）与查看器数值一致 —— 待手测。
      - AC2 点「查看完整分析」：同弹窗切换 full、无需重选文件、可切回 —— 待手测。
      - AC3 导入请求体逐字段一致（packageName/name/versionName/versionCode/iconDataUri/sizeMb）—— 逻辑等价，待 DevTools 核对。
      - AC4 图标 >100KB：仍提示「图标过大，仅预览不入库」且 `iconDataUri` 不提交 —— 逻辑保留，待手测。
      - AC5 损坏/加固包：原错误提示、无面板残留、导入按钮禁用 —— 逻辑保留，待手测。
      - AC6 1280×720 与 1440×900 布局：内部滚动、无溢出 —— 逻辑（max-h + overflow）满足，待手测。

## 风险文件与回滚点

| 文件 | 风险 | 回滚点 |
|------|------|--------|
| `frontend/src/components/admin/ApkImportModal.vue` | 唯一改动文件；状态机/提交字段回归风险最高 | 单文件改动；异常即 `git checkout -- <file>` 回到上一个可用提交 |

## 结论

导入弹窗已接入内核 `analyzeApk()`，内嵌 compact/full 详情面板，导入链路字段语义与改动前等价
（同源于 vendor 清单解析），`typecheck` + `build` 通过。交互式验收（AC1–AC6）需在浏览器侧手测，
其中 AC3 逐字段一致性已由数据来源等价性保证。

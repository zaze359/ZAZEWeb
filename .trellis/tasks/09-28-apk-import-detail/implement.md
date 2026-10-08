# 执行计划：「从 APK 导入」弹窗内嵌 APK 详情

> 任务：`09-28-apk-import-detail` ｜ 需求 `prd.md`
> 前置：`09-28-apk-inspect-core` 的 `analyzeApk()` 与 `ApkAnalysisPanel.vue` 已落地并验收通过。
> 本任务不改解析内核；若发现内核缺字段，回到 core 任务补，**不在弹窗里打补丁**。

## P0 前置

- [ ] 确认 core 已提供 `analyzeApk(file, { onProgress })` 与 `ApkAnalysisPanel`（`variant: 'compact' | 'full'`）。
- [ ] 用 `git show` / `git log` 记录改动前的 `ApkImportModal.vue` 版本，供 AC3 逐字段比对请求体。
- [ ] 准备对照工具：浏览器 DevTools Network 面板（抓 `import-from-apk` 请求体）。

## P1 切换数据来源（保持行为等价）

- [ ] `ApkImportModal.vue`：把 `loadParser()` + `new AppInfoParser(f).parse()` 换成
      `analyzeApk(f, { onProgress })`；`status` 状态机保持 `idle | loading | parsing | done | error` 四态语义
      （`onProgress` 的 stage 文案映射到现有「正在解析 APK…」提示，不新增状态）。
- [ ] `parsed` 改为由 `ApkAnalysis` 派生的 `computed`（或解析完成时一次性构造）：
      `packageName / name / versionName / versionCode / iconDataUri / sizeMb` 六个字段来源与改动前一致
      （`name` 取 `basic.label`，`iconDataUri` 取 `basic.iconDataUri`，`sizeMb` 取 `Math.round(fileSizeBytes/1024/1024)`）。
- [ ] `ICON_MAX_BYTES` 判定逻辑保留在弹窗（超限 → `iconDataUri = null` + 原有提示文案），不放进内核。
- [ ] **回归自测（做完立即做，不要攒到最后）**：导入一个应用，用 DevTools 比对请求体与改动前一致；图标超限包走一遍。

## P2 内嵌详情面板

- [ ] 在现有「图标 + 名称 + 包名 + 版本 + 大小」区块下方插入
      `<ApkAnalysisPanel :analysis="analysis" variant="compact" />`；`analysis === null` 时不渲染。
- [ ] 面板告警区（`warnings`）在 compact 下也必须可见（不能只放 full）。
- [ ] 面板不承载任何写操作：导入按钮仍是弹窗自己的按钮，面板内不放「导入」入口（职责分离）。

## P3 完整分析切换（弹窗内，不跳路由）

- [ ] 加 `expanded` 状态：`compact` 底部「查看完整分析」→ `expanded = true` → full 视图 + 可切回。
- [ ] 弹窗容器：默认宽度上调（如 `max-w-md` → `max-w-3xl` 视图下 `max-w-5xl`），
      加 `max-h-[85vh] overflow-y-auto`；切换视图时保持「图标/名称/包名」头部与底部按钮区固定可见。
- [ ] 关窗（取消/Esc/点击遮罩）时重置 `file` / `analysis` / `expanded` / 错误态 —— 沿用现有 `watch(open)` 复位逻辑，新增字段一并复位。

## P4 降级与边界

- [ ] `analyzeApk` 抛错 → 沿用现有 `catch` 文案与 `status = 'error'`，且 `analysis` 置 `null`（不显示半截面板）。
- [ ] 非 `.apk` 文件、空文件、>200MB 文件：沿用现有拦截与提示。

## P5 验证

- [ ] `cd frontend && npm run typecheck && npm run build`。
- [ ] 手工跑 `prd.md` AC1–AC6，逐条记录（含窗口尺寸截图/说明）。
- [ ] 对照检查：弹窗数值与查看器数值一致（AC1）—— 同一个 APK 两处各看一遍。

## 风险文件与回滚点

| 文件 | 风险 | 回滚点 |
|------|------|--------|
| `frontend/src/components/admin/ApkImportModal.vue` | 唯一的改动文件；状态机/提交字段回归风险最高 | 每个 P 阶段结束后跑一次导入自测；异常即 `git checkout -- <file>` 回到上一个可用提交 |

回滚 = 还原该单个文件（无数据迁移、无接口变更、无其他文件受影响）。

## start 前复查

- [ ] core 子任务已验收（解析内核 + 面板可用）
- [ ] `prd.md` / 本文件已 review 通过
- [ ] 未开始写实现代码

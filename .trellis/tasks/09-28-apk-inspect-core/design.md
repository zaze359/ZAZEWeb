# 技术设计：APK 解析内核 + 独立「APK 分析」查看器

> 任务：`09-28-apk-inspect-core` ｜ 父任务：`09-28-apk-inspector`
> 需求见同目录 `prd.md`；执行计划见 `implement.md`。

## 1. 架构与边界

```
              ┌─────────────────────── 浏览器本地（APK 永不上传）───────────────────────┐
File(.apk) ──▶│  analyzeApk()  ── 编排 + 进度 + 降级收敛                              │
              │      ├─ zipReader    : 中央目录（EOCD → CD）→ entries[]              │
              │      ├─ manifestParser: app-info-parser → 归一化 → ApkManifest       │
              │      ├─ permissions  : 词典（中文名 / 危险等级 / 分组）                │
              │      └─ hasher       : SHA-256（超阈值跳过）                          │
              │                              ↓                                       │
              │                        ApkAnalysis  ──▶ ApkAnalysisPanel(variant)    │
              └───────────────────────────────────────────────────────────────────┘
                                     ▲                            ▲
                    ApkAnalyzerView（full）        ApkImportModal（compact，child 2）
```

- **边界**：本任务**不新增后端接口、不写数据库、不改 vendor 库**；数据全部在浏览器内存里，
  生命周期止于页面/弹窗关闭（R4/R5/R9）。
- **分层**：`utils/apk/*` 是纯逻辑（无 Vue 依赖、无 DOM 依赖，只依赖 `File`/`Blob`/`crypto`），
  `components/apk/*` 是纯展示（`props: analysis`），`views/ApkAnalyzerView.vue` 负责选文件与编排。
  这样 compact/full 两个宿主与后续签名扩展都不需要动解析内核的调用方式。

## 2. 模块清单（新增文件）

| 文件 | 职责 |
|------|------|
| `frontend/src/utils/apk/types.ts` | `ApkAnalysis` 及子类型、`ApkPermission`、`ApkSignatureSummary`（签名字段先占位定义，child 3 填充实现） |
| `frontend/src/utils/apk/zipReader.ts` | 轻量 ZIP 中央目录读取：`readCentralDirectory(file)` → `ZipEntry[]`；`readSlice(file, start, end)`；`findSigningBlock(file, entryCount?)` 预留（child 3 用） |
| `frontend/src/utils/apk/manifestParser.ts` | `loadManifestParser()`（从 `ApkImportModal.vue` 抽出按需加载逻辑）+ `parseManifest(file)` → 归一化 `ApkManifest` |
| `frontend/src/utils/apk/permissions.ts` | 权限词典（`PERMISSION_DICT`）+ `describePermission(name)` |
| `frontend/src/utils/apk/fileStats.ts` | 由 `ZipEntry[]` 归并出 `files`（DEX / ABI / 密度 / Top 分组） |
| `frontend/src/utils/apk/analyzeApk.ts` | 编排入口：串起上面四者，产出 `ApkAnalysis`，收集 `warnings` 与进度 |
| `frontend/src/utils/format.ts` | `formatBytes()` / `formatCount()`（若 `utils/` 下已有等价函数则复用，不新建） |
| `frontend/src/components/apk/ApkAnalysisPanel.vue` | 详情面板（`variant: 'compact' \| 'full'`），编排下面的卡片 |
| `frontend/src/components/apk/ApkBasicCard.vue` | 基本信息（图标/名称/包名/版本/体积/指纹） |
| `frontend/src/components/apk/ApkSdkCard.vue` | SDK 与兼容性（minSdk/targetSdk/maxSdk + Android 版本名） |
| `frontend/src/components/apk/ApkPermissionCard.vue` | 权限清单（危险标记 + 中文名 + full 下搜索过滤） |
| `frontend/src/components/apk/ApkFeatureCard.vue` | 硬件特性 + 屏幕/配置支持 |
| `frontend/src/components/apk/ApkFileBreakdownCard.vue` | 文件构成（占比条 + ABI/DEX/密度明细） |
| `frontend/src/components/apk/ApkSignatureCard.vue` | 签名区；`analysis.signature === null` 时显示「未分析」（child 3 填充数据渲染） |
| `frontend/src/components/apk/ApkRawManifest.vue` | 原始清单折叠视图（full 专属） |
| `frontend/src/views/ApkAnalyzerView.vue` | 独立查看器：选文件 / 进度 / 面板 / 导出 JSON |

**复用（不新建）**：`app-info-parser` vendor 资产、`frontend/src/utils/appMeta.ts` 中已有的派生工具、
admin 主题令牌（`admin-bg/surface/border/text/muted/accent`）。
`ApkImportModal.vue` 内联的 `loadParser()` / `APK_MAX_BYTES` / `ICON_MAX_BYTES` 中的**解析器加载部分**
改为从 `manifestParser.ts` 导入，消除重复（常量保留在弹窗，因为那是导入策略不是解析策略）。

## 3. 数据模型（冻结契约）

```ts
// frontend/src/utils/apk/types.ts
export type PermissionLevel = 'dangerous' | 'normal' | 'signature' | 'unknown'

export interface ApkPermission {
  name: string                 // 原始权限名，如 android.permission.CAMERA
  cn: string                   // 中文名；词典未收录时回退为 name
  level: PermissionLevel
  group?: string               // 危险权限分组（如「位置」「相机」）
  documented: boolean          // 词典是否收录（UI 据此标注「未收录」）
  custom?: boolean             // 来自 <permission>（应用自定义声明）而非 uses-permission
  sinceSdk23?: boolean         // 来自 uses-permission-sdk-23
  maxSdkVersion?: number
}

export interface ApkAnalysis {
  basic: {
    fileName: string
    fileSizeBytes: number
    packageName: string
    label: string
    versionName?: string
    versionCode?: number
    versionCodeMajor?: number
    /** 图标 base64 data URI（vendor 库注入的 r.icon）；导入弹窗依赖它，内核必须返回，避免二次解析整包 */
    iconDataUri?: string | null
    sha256?: string
    sha256SkippedReason?: string
  }
  sdk: { minSdk?: number; targetSdk?: number; maxSdk?: number; compileSdk?: number }
  permissions: { total: number; dangerousCount: number; customCount: number; items: ApkPermission[] }
  features: { items: { name: string; required: boolean }[] }
  screens: { supportsAnyDensity?: boolean; compatibleScreens: string[]; supportsGlTextures?: boolean }
  files: {
    entryCount: number
    compressedBytes: number
    uncompressedBytes: number
    dexFiles: { name: string; bytes: number }[]
    abis: { abi: string; fileCount: number; bytes: number }[]
    densities: { density: string; fileCount: number; bytes: number }[]
    topGroups: { path: string; fileCount: number; bytes: number }[]
    nativeLibCount: number
  }
  signature: ApkSignatureSummary | null   // child 3 填充；本任务恒为 null
  warnings: string[]
  raw: { manifest: unknown | null }       // 原始清单对象，仅 full 视图渲染
}
```

父任务 prd.md 已冻结上层字段名；本文件是唯一权威定义，其他子任务只 import。

## 4. 关键算法与契约

### 4.1 ZIP 中央目录读取（`zipReader.ts`）

只读目录、不解压数据 —— 这是「200MB 包也能秒出文件构成」的关键。

1. 读文件尾部切片（`file.slice(max(0, size - 65557))`）找 EOCD 签名 `0x06054b50`；
   从后往前扫第一个命中，取 `cdOffset` / `cdSize` / `entryCount` / `commentLen`。
2. 若 `entryCount === 0xFFFF` 或 `cdOffset === 0xFFFFFFFF`：按 ZIP64 处理 ——
   读 EOCD 前 20 字节找 EOCD64 Locator（`0x07064b50`），再按其中的偏移读 EOCD64 记录（`0x06064b50`）取真值。
3. 读切片 `[cdOffset, cdOffset + cdSize)`，逐条解析中央目录记录（签名 `0x02014b50`）：
   `method` / `crc32` / `compressedSize` / `uncompressedSize` / `nameLen` / `extraLen` / `commentLen` /
   `localHeaderOffset` / `name`。遇到 ZIP64 扩展字段（id `0x0001`）时用其中的 8 字节真值替换哨兵。
4. 返回 `ZipEntry[]`（`name` 用 UTF-8 解码，flag bit 11 未置位时按 CP437/ASCII 处理 —— APK 条目名实际全为 ASCII）。

**失败模式**：找不到 EOCD（不是 ZIP）→ `files` 维度整体降级，`warnings` 记「未找到 ZIP 中央目录，文件可能损坏或非 APK」；
ZIP64 解析异常 → 降级为「条目数未知」，其余维度不受影响。

**不用现成库的原因**：`jszip`/`fflate` 需要解压或引入较大依赖，而 `app-info-parser` 的 `unzip.getBuffer(name[])`
只按名取条目、无法列目录。自研只有约 150 行，零依赖、零体积。

### 4.2 清单归一化（`manifestParser.ts`）

- 通过既有方式按需加载 vendor 库（`<script>` → `window.AppInfoParser`），`new window.AppInfoParser(file).parse()`。
  该 Promise 内部会解压 `AndroidManifest.xml` + `resources.arsc`（整包读入，既有行为，不改）。
- 逐字段**宽容读取**（vendor 返回的字段可能缺失或为字符串）：
  - `usesSdk.minSdkVersion|targetSdkVersion|maxSdkVersion` → `Number(...)`，`NaN` 则置 `undefined` 并记 warning。
  - `usesPermissions` / `usesPermissionsSDK23`：数组元素取 `name`（可能带 `android: ` 前缀则剥掉）；
    同名去重，命中 SDK23 时置 `sinceSdk23 = true`；`maxSdkVersion` 一并保留（既有 `ignore` 不含权限，字段可得）。
  - `usesFeatures`：元素取 `name` + `required`（缺省 `true`，与 Android 语义一致：未写 required 表示必需）。
  - `supportsScreens` / `compatibleScreens` / `supportsGlTextures` 原样归一。
  - `application.label`：若匹配 `/^@(string|android:string)\//` 说明 `resources.arsc` 映射失败 →
    `label` 回退为包名，并记 warning「应用名资源未解析，已回退为包名」。
- **不使用** `r.icon` 做体积/构成统计（图标是 base64 字符串，与 `files` 口径无关），
  但**必须**把它放进 `basic.iconDataUri` —— 导入弹窗（child 2）依赖它提交 `ICON`，
  否则弹窗要为头像再解析一次 APK（二次整包解压）。

### 4.3 权限词典（`permissions.ts`）

- 结构：`Record<string, { cn: string; level: PermissionLevel; group?: string }>`，
  覆盖 Android 核心权限（`android.permission.*`）约 80 条，危险权限按官方分组标注
  （位置 / 相机 / 麦克风 / 通讯录 / 日历 / 电话 / 短信 / 存储 / 传感器 / 身体传感器 / 活动识别 / 附近设备等）。
- `describePermission(name)`：命中词典 → 用词典；未命中 → `{ cn: name, level: 'unknown', documented: false }`。
  非 `android.permission.` 前缀的自定义权限 → `level: 'unknown'`、`custom: true`。
- **不引入网络查询**（离线可用是既有设计取向，见 `AppMarketCnDict.kt` 的同类决策）。

### 4.4 文件构成归并（`fileStats.ts`）

以 `ZipEntry.uncompressedSize` 为**主口径**（`compressedBytes` 另行汇总，UI 说明口径）：

| 聚合项 | 规则 |
|--------|------|
| ABI | 名称匹配 `^lib/([^/]+)/.+` → 按 `arm64-v8a` / `armeabi-v7a` / `armeabi` / `x86` / `x86_64` / `mips*` 归并，未知 ABI 落 `other` |
| nativeLibCount | 名称匹配 `^lib/.+\.so$` 的条目数 |
| DEX | 名称匹配 `^classes(\d*)\.dex$`（根目录），按序号排序 |
| 密度 | 目录段匹配 `-(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|nodpi|anydpi)(-|/)` → 按密度归并；`res/` 下无密度后缀的归 `default` |
| Top 分组 | 取名称前 1~2 段（`res/<type>`、`assets/<首段>`、`lib/<abi>`、`META-INF`、根文件）聚合，按解压字节倒序取前 12 |

**口径声明**：展示的「占比」以解压字节计（真实安装占用更接近解压值），压缩后字节仅用于展示「传输体积」。

### 4.5 文件指纹（`analyzeApk.ts`）

- `crypto.subtle.digest('SHA-256', await file.arrayBuffer())` → 十六进制小写。
- 阈值 `HASH_MAX_BYTES = 100 * 1024 * 1024`：超过则跳过，写 `sha256SkippedReason = '文件超过 100MB，已跳过指纹计算'`，
  UI 展示该说明而非空白。
- `crypto.subtle` 不可用（非安全上下文）→ 同样降级并写原因。`opts.computeHash = false` 可用于规避。

### 4.6 编排与进度（`analyzeApk.ts`）

```ts
type AnalyzeStage = 'zip' | 'manifest' | 'hash' | 'done'
analyzeApk(file, { onProgress, computeHash = true })
```

- 顺序：`zip`（快）→ `manifest`（慢，库内部整包解压）→ `hash`（中）→ 汇总。
  不使用 `Promise.all`：vendor 库会自行读整个文件，与哈希并发会让峰值内存翻倍。
- 每一步用 `try/catch` 包住，失败只写 `warnings` 并把对应分区留空（`files` 空则 `entryCount = 0`），
  **`manifest` 失败是唯一硬失败**（没有包名就无法构成 `basic`）→ 抛出交由调用方展示错误。
- 非 `.apk` 扩展名或 `file.size === 0` 在调用方（UI）先行拦截并提示。

## 5. UI 设计

- **面板密度**：`variant='compact'` 只渲染 basic / sdk / 权限计数+Top5 危险权限 / 构成摘要（ABI + DEX 数 + 体积）/
  签名摘要 / warnings；`variant='full'` 渲染全部卡片 + 权限搜索 + 原始清单折叠 + 导出按钮。
  同一组件内以 `v-if` 控制区块，**不可**拆成两个组件各自实现（R7、AC8）。
- **主题**：后台浅色，沿用现成令牌 —— 卡片 `bg-admin-surface border-admin-border`、正文 `text-admin-text`、
  次要 `text-admin-muted`、主行动 `bg-admin-accent`。
  语义色**沿用 `ApkImportModal.vue` 既有做法**（Tailwind 原生调色板）：
  危险权限/告警 `text-red-600` + `bg-red-50`，需注意项 `text-yellow-600`，正常 `text-emerald-600`。
  不新增 Tailwind 令牌（`tailwind.config.js` 只保留 portal/admin 两套基线）。
- **危险权限表达**：不靠颜色单独承载语义 —— 危险项带「危险」文字标签，未收录项带「未收录」标签（可访问性）。
- **构成占比**：横向堆叠条（`div` 宽度百分比），颜色取 admin 语义色 + 中性灰阶；不引入图表库。
- **原始清单**：`<pre>` 展示 `JSON.stringify(raw.manifest, null, 2)`，默认折叠，超长（>200KB）截断并提示。
- **查看器布局**：顶部选文件 + 进度条（stage 文案）/ 重新选择；中部 `ApkAnalysisPanel variant="full"`；
  底部导出（复制/下载 `.json`，文件名 `<包名>-<版本名>-analysis.json`）。
- **路由与入口（已定稿）**：新增管理后台专属路由 `/admin/apk`（`name: 'apk-analyzer'`，`meta.theme = 'admin'`），
  仅管理员；`AdminView.vue` 顶部操作区新增入口按钮（与「从 APK 导入」并列）。
  `/admin/**` 已被 `AuthInterceptor.kt` 的 `ADMIN_PATTERNS` 覆盖 → **本任务无需修改任何后端文件**，
  也不会扩大可访问面（未登录访问会被重定向到 `/login`，非管理员被 403 回首页）。

## 6. 兼容性与降级

| 场景 | 行为 |
|------|------|
| 非安全上下文（无 `crypto.subtle`） | 跳过指纹，写 `warnings` |
| 加固包 / 清单异常 | `parseManifest` 抛错 → 查看器显示可读错误，不进入面板 |
| 超大包（>200MB 由 UI 拦截；100~200MB 放行） | 指纹跳过，其余维度正常 |
| 老浏览器不支持 `structuredClone` / `Array.from` 等 | 不使用这些 API；仅用 `TextDecoder` / `DataView` / `Promise` |
| `label` 为资源引用 | 回退包名 + warning |
| ZIP64 | 基础支持；解析失败降级为条目数未知 |

## 7. 权衡与取舍

1. **不自研 AXML 解析器**：换取组件清单（Out of Scope）不值得在本任务付出成本；vendor 库已覆盖其余全部清单字段。
2. **不打补丁改 `app-info-parser.min.js`**：压缩产物改一行虽可行，但升级库时会静默丢失补丁（无测试保护），风险高于收益。
3. **自研 ZIP 目录读取而非引库**：只需中央目录，150 行零依赖优于新增 npm 依赖（也避免版本/体积维护）。
4. **主口径用解压字节**：更接近真实占用，但会与「商店标注大小」不一致 —— 在 UI 明确标注两种口径。
5. **不用 `Promise.all` 并发**：内存峰值优先（见 4.6）。
6. **不落库**：本任务零后端/零 DB 改动，风险最低；落库另立任务。

## 8. 回滚

- 全部改动是**新增文件 + 两处小改**（`router/index.ts` 一条路由、`AdminView.vue` 一个入口按钮），
  无数据迁移，**无后端文件改动**。
- 回滚 = 删除新增文件、还原 `router/index.ts` 与 `AdminView.vue` 即可；
  `ApkImportModal.vue` 的 `loadParser()` 抽取属于等价重构，回滚时一并还原。
- 无数据库/接口副作用，回滚后无残留状态。

## 9. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| vendor 库返回字段与预期不符（命名/类型） | 分区为空 | 归一化层全部宽容读取 + warning；实现阶段先用真实 APK 打印原始对象核对 |
| 清单解析耗时（大包数秒） | 观感卡顿 | 分步进度文案；解析期间禁用重复选择 |
| `resources.arsc` 映射失败率高 | 应用名不准确 | 回退包名 + warning（既有弹窗已有此问题，不回归） |
| 权限词典维护成本 | 新权限显示 `unknown` | 回退显示原始名并标「未收录」，不是错误状态 |
| 大包内存峰值 | 标签页卡死 | 不并发、指纹超阈值跳过、UI 200MB 上限沿用 |

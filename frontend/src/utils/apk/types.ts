// APK 解析内核的类型契约（由 09-28-apk-inspect-core 拥有，其他子任务只消费，不得复制一份）。
// 父任务 prd.md 已冻结上层字段名；本文件是权威定义。

export type PermissionLevel = 'dangerous' | 'normal' | 'signature' | 'unknown'

export interface ApkPermission {
  name: string // 原始权限名，如 android.permission.CAMERA
  cn: string // 中文名；词典未收录时回退为 name
  level: PermissionLevel
  group?: string // 危险权限分组（如「位置」「相机」）
  documented: boolean // 词典是否收录（UI 据此标注「未收录」）
  custom?: boolean // 来自 <permission>（应用自定义声明）而非 uses-permission
  sinceSdk23?: boolean // 来自 uses-permission-sdk-23
  maxSdkVersion?: number
}

export interface ApkFeatureItem {
  name: string
  required: boolean
}

export interface ApkScreenInfo {
  supportsAnyDensity?: boolean
  compatibleScreens: { screenSize?: string; screenDensity?: string }[]
  supportsGlTextures?: boolean
}

export interface ApkFileBreakdown {
  entryCount: number
  compressedBytes: number
  uncompressedBytes: number
  dexFiles: { name: string; bytes: number }[]
  abis: { abi: string; fileCount: number; bytes: number }[]
  densities: { density: string; fileCount: number; bytes: number }[]
  topGroups: { path: string; fileCount: number; bytes: number }[]
  nativeLibCount: number
}

// ---------- 签名（由 09-28-apk-signature 填充实现；本任务定义为 null 占位）----------

export interface ApkSignerCert {
  subject: string // "CN=..., O=..., C=..."（openssl 风格）
  issuer: string
  subjectCN?: string
  serial?: string
  sha256?: string // 冒号分隔大写十六进制；crypto.subtle 不可用时 undefined
  sha1?: string
  fingerprintError?: string
  notBefore?: string // ISO 8601
  notAfter?: string
  signatureAlgorithm?: string
  selfSigned?: boolean
  debugCert?: boolean
}

export interface ApkSignatureSummary {
  schemes: { v1: boolean; v2: boolean; v3: boolean; v3_1: boolean }
  v1EntryNames: string[]
  signerCount: number
  certs: ApkSignerCert[] // 按 sha256 去重（无指纹时按 subject+serial）
  integrityVerified: false // 字面量 false：类型层面禁止声称「已验证」
  degraded: string[] // 降级原因（同步进 ApkAnalysis.warnings）
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
    /** 图标 base64 data URI（vendor 库注入的 r.icon）；导入弹窗依赖它提交 ICON，内核必须返回 */
    iconDataUri?: string | null
    sha256?: string
    sha256SkippedReason?: string
  }
  sdk: {
    minSdk?: number
    targetSdk?: number
    maxSdk?: number
    compileSdk?: number
    minAndroid?: string
    targetAndroid?: string
  }
  permissions: {
    total: number
    dangerousCount: number
    customCount: number
    items: ApkPermission[]
  }
  features: { items: ApkFeatureItem[] }
  screens: ApkScreenInfo
  files: ApkFileBreakdown
  signature: ApkSignatureSummary | null // child 3 填充；本任务恒为 null
  warnings: string[]
  raw: { manifest: unknown | null } // 原始清单对象，仅 full 视图渲染
}

export type AnalyzeStage = 'zip' | 'manifest' | 'signature' | 'hash' | 'done'

export interface AnalyzeProgress {
  stage: AnalyzeStage
  /** 0~100，粗略进度 */
  percent: number
  message?: string
}

export interface AnalyzeOptions {
  onProgress?: (p: AnalyzeProgress) => void
  computeHash?: boolean
}

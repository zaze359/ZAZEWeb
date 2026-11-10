// APK 清单解析：按需加载 vendor 的 app-info-parser（全局 window.AppInfoParser），
// 归一化出强类型结果。被「解析内核」与「从 APK 导入」弹窗共用（单一加载入口，避免重复实现）。

import { isResourceRef } from '../format'

// 解析库放在 frontend/public/vendor/：由前端自己的 dev server / 静态托管伺服，
// 不再依赖后端 src/main/resources/static/vendor/（否则 npm run dev 下必然 404）。
// 用 BASE_URL 拼接，兼容子路径部署（base=/zaze/ 时同样能取到）。
const PARSER_SRC = `${import.meta.env.BASE_URL}vendor/app-info-parser/app-info-parser.min.js`

let parserPromise: Promise<void> | null = null

/** 按需插入 <script> 加载 app-info-parser，挂到 window.AppInfoParser。并发调用只加载一次。 */
export function loadManifestParser(): Promise<void> {
  if ((window as any).AppInfoParser) return Promise.resolve()
  if (parserPromise) return parserPromise
  parserPromise = new Promise<void>((resolve, reject) => {
    const s = document.createElement('script')
    s.src = PARSER_SRC
    s.onload = () => resolve()
    s.onerror = () =>
      reject(new Error(`解析库加载失败（${s.src}），请确认该路径可访问后重试`))
    document.head.appendChild(s)
  })
  return parserPromise
}

export interface ParsedManifest {
  packageName?: string
  versionName?: string
  versionCode?: number
  versionCodeMajor?: number
  icon?: string | null
  label?: string
  labelIsResourceRef: boolean
  usesSdk?: { minSdkVersion?: number; targetSdkVersion?: number; maxSdkVersion?: number; compileSdkVersion?: number }
  usesPermissions: { name: string; maxSdkVersion?: number }[]
  usesPermissionsSDK23: { name: string; maxSdkVersion?: number }[]
  customPermissions: { name: string }[]
  usesFeatures: { name: string; required: boolean }[]
  supportsScreens?: Record<string, unknown>
  compatibleScreens: { screenSize?: string; screenDensity?: string }[]
  supportsGlTextures?: boolean
  /** 原始清单对象（vendor 返回），供 full 视图展示原始清单与后续签名分析取用 */
  raw: unknown
}

function toNumber(v: unknown): number | undefined {
  if (v == null) return undefined
  const n = Number(v)
  return Number.isFinite(n) ? n : undefined
}

function asString(v: unknown): string | undefined {
  if (v == null) return undefined
  return String(v)
}

/** 解析 APK 的 AndroidManifest。失败（加固包/损坏）由调用方捕获。 */
export async function parseManifest(file: File | Blob): Promise<ParsedManifest> {
  await loadManifestParser()
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const raw: any = await new (window as any).AppInfoParser(file).parse()

  const pkg = asString(raw && raw.package) || undefined
  const app = raw && raw.application
  const rawLabel = app ? asString(app.label) : undefined

  const usesSdkRaw = raw ? raw.usesSdk : undefined
  const usesSdk = usesSdkRaw
    ? {
        minSdkVersion: toNumber(usesSdkRaw.minSdkVersion),
        targetSdkVersion: toNumber(usesSdkRaw.targetSdkVersion),
        maxSdkVersion: toNumber(usesSdkRaw.maxSdkVersion),
        compileSdkVersion: toNumber(usesSdkRaw.compileSdkVersion)
      }
    : undefined

  const permissionList = (raw && raw.usesPermissions) || []
  const permissionSdk23List = (raw && raw.usesPermissionsSDK23) || []
  const customPermissionList = (raw && raw.permissions) || []
  const featureList = (raw && raw.usesFeatures) || []

  return {
    packageName: pkg,
    versionName: asString(raw && raw.versionName),
    versionCode: toNumber(raw && raw.versionCode),
    versionCodeMajor: toNumber(raw && raw.versionCodeMajor),
    icon: raw ? (raw.icon as string | null) || null : null,
    label: rawLabel,
    labelIsResourceRef: isResourceRef(rawLabel),
    usesSdk,
    usesPermissions: permissionList
      .map((p: any) => ({ name: asString(p && p.name) || '', maxSdkVersion: toNumber(p && p.maxSdkVersion) }))
      .filter((p: { name: string }) => p.name),
    usesPermissionsSDK23: permissionSdk23List
      .map((p: any) => ({ name: asString(p && p.name) || '', maxSdkVersion: toNumber(p && p.maxSdkVersion) }))
      .filter((p: { name: string }) => p.name),
    customPermissions: customPermissionList
      .map((p: any) => ({ name: asString(p && p.name) || '' }))
      .filter((p: { name: string }) => p.name),
    usesFeatures: featureList
      .map((f: any) => ({ name: asString(f && f.name) || '', required: f ? f.required !== false : true }))
      .filter((f: { name: string }) => f.name),
    supportsScreens: raw ? raw.supportsScreens : undefined,
    compatibleScreens: raw ? (raw.compatibleScreens || []).map((c: any) => ({
      screenSize: asString(c && c.screenSize),
      screenDensity: asString(c && c.screenDensity)
    })) : [],
    supportsGlTextures: raw ? Boolean(raw.supportsGlTextures) : undefined,
    raw: raw || null
  }
}

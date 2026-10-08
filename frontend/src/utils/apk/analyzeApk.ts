// 编排入口：串起 ZIP 目录 → 清单 → 指纹 → 汇总，产出 ApkAnalysis。
// 顺序执行（不并发）：vendor 库会自行读整个文件，与哈希并发会让峰值内存翻倍。
// 任一子步骤失败只降级该维度并写入 warnings；清单解析失败是唯一硬失败（拿不到包名）。

import { readCentralDirectory, type CentralDirectory } from './zipReader'
import { parseManifest, type ParsedManifest } from './manifestParser'
import { describePermission } from './permissions'
import { summarizeFiles, emptyFiles } from './fileStats'
import { androidVersionName } from './androidVersions'
import { analyzeSignature } from './signature'
import type { ApkAnalysis, ApkPermission, AnalyzeOptions } from './types'

const HASH_MAX_BYTES = 100 * 1024 * 1024

/** 计算整体分析的对外入口。失败时（清单解析异常）抛出，交由调用方展示错误。 */
export async function analyzeApk(file: File | Blob, opts: AnalyzeOptions = {}): Promise<ApkAnalysis> {
  const onProgress = opts.onProgress
  const warnings: string[] = []

  // 1) ZIP 中央目录（快，只读目录）
  onProgress?.({ stage: 'zip', percent: 5, message: '读取 APK 文件结构…' })
  let cd: CentralDirectory | null = null
  try {
    cd = await readCentralDirectory(file)
  } catch (e) {
    warnings.push('未找到 ZIP 中央目录，文件可能损坏或非 APK：' + (e as Error).message)
  }

  // 2) 清单解析（慢，库内部整包解压）
  onProgress?.({ stage: 'manifest', percent: 30, message: '解析 AndroidManifest…' })
  const manifest: ParsedManifest = await parseManifest(file) // 失败抛出

  if (manifest.labelIsResourceRef) {
    warnings.push('应用名资源未解析（显示为资源引用），已回退为包名')
  }

  // 3) 文件构成（目录失败则降级为空）
  const files = cd ? summarizeFiles(cd.entries) : emptyFiles()

  // 4) 签名分析（清单之后，避免与 vendor 库整包读取并发；失败只降级该维度）
  onProgress?.({ stage: 'signature', percent: 55, message: '分析签名…' })
  let signature: ApkAnalysis['signature'] = null
  if (cd) {
    try {
      signature = await analyzeSignature(file, cd.entries, cd.cdOffset)
    } catch (e) {
      warnings.push('签名分析失败：' + (e as Error).message)
    }
  }
  if (signature && signature.degraded.length) {
    warnings.push(...signature.degraded)
  }

  // 5) 权限归并（去重 + 词典）
  const permissions = assemblePermissions(manifest)

  // 5) 文件指纹（中，可跳过）
  let sha256: string | undefined
  let sha256SkippedReason: string | undefined
  if (opts.computeHash !== false) {
    onProgress?.({ stage: 'hash', percent: 80, message: '计算文件指纹…' })
    if (file.size > HASH_MAX_BYTES) {
      sha256SkippedReason = '文件超过 100MB，已跳过指纹计算'
    } else if (typeof crypto !== 'undefined' && crypto.subtle) {
      try {
        const buf = await file.arrayBuffer()
        const digest = await crypto.subtle.digest('SHA-256', buf)
        sha256 = toHex(digest)
      } catch (e) {
        sha256SkippedReason = '指纹计算失败：' + (e as Error).message
      }
    } else {
      sha256SkippedReason = '当前环境不支持安全计算（crypto.subtle 不可用），已跳过指纹'
    }
  }

  onProgress?.({ stage: 'done', percent: 100, message: '分析完成' })

  const label = manifest.labelIsResourceRef
    ? manifest.packageName || ''
    : manifest.label || manifest.packageName || ''

  return {
    basic: {
      fileName: (file as File).name || 'unknown.apk',
      fileSizeBytes: file.size,
      packageName: manifest.packageName || '',
      label,
      versionName: manifest.versionName,
      versionCode: manifest.versionCode,
      versionCodeMajor: manifest.versionCodeMajor,
      iconDataUri: manifest.icon || null,
      sha256,
      sha256SkippedReason
    },
    sdk: {
      minSdk: manifest.usesSdk?.minSdkVersion,
      targetSdk: manifest.usesSdk?.targetSdkVersion,
      maxSdk: manifest.usesSdk?.maxSdkVersion,
      compileSdk: manifest.usesSdk?.compileSdkVersion,
      minAndroid: androidVersionName(manifest.usesSdk?.minSdkVersion),
      targetAndroid: androidVersionName(manifest.usesSdk?.targetSdkVersion)
    },
    permissions,
    features: { items: manifest.usesFeatures },
    screens: {
      supportsAnyDensity: readAnyDensity(manifest.supportsScreens),
      compatibleScreens: manifest.compatibleScreens,
      supportsGlTextures: manifest.supportsGlTextures
    },
    files,
    signature,
    warnings,
    raw: { manifest: manifest.raw ?? null }
  }
}

function assemblePermissions(manifest: ParsedManifest): ApkAnalysis['permissions'] {
  const seen = new Map<string, ApkPermission>()
  const put = (
    name: string,
    extra: { custom?: boolean; sinceSdk23?: boolean; maxSdkVersion?: number }
  ) => {
    const existing = seen.get(name)
    if (existing) {
      if (extra.sinceSdk23) existing.sinceSdk23 = true
      if (extra.maxSdkVersion != null) existing.maxSdkVersion = extra.maxSdkVersion
      return
    }
    seen.set(name, describePermission(name, extra))
  }

  for (const p of manifest.usesPermissions) {
    put(p.name, { maxSdkVersion: p.maxSdkVersion })
  }
  for (const p of manifest.usesPermissionsSDK23) {
    put(p.name, { sinceSdk23: true, maxSdkVersion: p.maxSdkVersion })
  }
  for (const p of manifest.customPermissions) {
    put(p.name, { custom: true })
  }

  const items = [...seen.values()]
  const dangerousCount = items.filter((p) => p.level === 'dangerous').length
  const customCount = items.filter((p) => p.custom).length
  return { total: items.length, dangerousCount, customCount, items }
}

function readAnyDensity(ss: unknown): boolean | undefined {
  if (!ss || typeof ss !== 'object') return undefined
  const o = ss as Record<string, unknown>
  const attrs = (o.attributes as Record<string, unknown> | undefined) || {}
  const v = o.anyDensity ?? o['android:anyDensity'] ?? attrs.anyDensity
  if (v == null) return undefined
  return v === true || v === 'true' || v === '1' || v === 1
}

function toHex(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf)
  let s = ''
  for (let i = 0; i < bytes.length; i++) {
    s += bytes[i].toString(16).padStart(2, '0')
  }
  return s
}

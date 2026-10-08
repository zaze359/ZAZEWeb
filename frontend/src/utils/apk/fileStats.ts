// 文件构成归并：由 ZIP 中央目录条目聚合出 ApkAnalysis['files']。
// 主口径用解压字节（更接近真实安装占用），压缩字节另行汇总（传输体积）。

import type { ZipEntry } from './zipReader'
import type { ApkFileBreakdown } from './types'

const KNOWN_ABIS = new Set([
  'arm64-v8a',
  'armeabi-v7a',
  'armeabi',
  'x86',
  'x86_64',
  'mips',
  'mips64'
])

// 资源密度后缀（目录名形如 drawable-hdpi / mipmap-xxxhdpi）
const DENSITY_RE = /\/([a-z]+)-(ldpi|mdpi|tvdpi|hdpi|xhdpi|xxhdpi|xxxhdpi|nodpi|anydpi)(?:-|\/|$)/

/** 空的文件构成（ZIP 目录读取失败时的降级结果） */
export function emptyFiles(): ApkFileBreakdown {
  return {
    entryCount: 0,
    compressedBytes: 0,
    uncompressedBytes: 0,
    dexFiles: [],
    abis: [],
    densities: [],
    topGroups: [],
    nativeLibCount: 0
  }
}

export function summarizeFiles(entries: ZipEntry[]): ApkFileBreakdown {
  let compressedBytes = 0
  let uncompressedBytes = 0
  const dexFiles: { name: string; bytes: number }[] = []
  const abiMap = new Map<string, { fileCount: number; bytes: number }>()
  const densityMap = new Map<string, { fileCount: number; bytes: number }>()
  const groupMap = new Map<string, { fileCount: number; bytes: number }>()
  let nativeLibCount = 0

  for (const e of entries) {
    const comp = e.compressedSize || 0
    const uncomp = e.uncompressedSize || 0
    compressedBytes += comp
    uncompressedBytes += uncomp

    // DEX（根目录 classes.dex / classes2.dex ...）
    if (/^classes(\d*)\.dex$/.test(e.name)) {
      dexFiles.push({ name: e.name, bytes: uncomp })
    }

    // ABI / 原生库：lib/<abi>/...
    const lib = /^lib\/([^/]+)\/(.+)$/.exec(e.name)
    if (lib) {
      const abiRaw = lib[1]
      const abi = KNOWN_ABIS.has(abiRaw) ? abiRaw : 'other'
      const cur = abiMap.get(abi) || { fileCount: 0, bytes: 0 }
      cur.fileCount++
      cur.bytes += uncomp
      abiMap.set(abi, cur)
      if (/\.so$/.test(lib[2])) nativeLibCount++
    }

    // 资源密度
    const dm = DENSITY_RE.exec(e.name)
    const density = dm ? dm[2] : 'default'
    const dcur = densityMap.get(density) || { fileCount: 0, bytes: 0 }
    dcur.fileCount++
    dcur.bytes += uncomp
    densityMap.set(density, dcur)

    // Top 分组（前 1~2 段）
    const group = groupKey(e.name)
    const gcur = groupMap.get(group) || { fileCount: 0, bytes: 0 }
    gcur.fileCount++
    gcur.bytes += uncomp
    groupMap.set(group, gcur)
  }

  dexFiles.sort((a, b) => dexIndex(a.name) - dexIndex(b.name))
  const abis = [...abiMap.entries()]
    .map(([abi, v]) => ({ abi, ...v }))
    .sort((a, b) => b.bytes - a.bytes)
  const densities = [...densityMap.entries()]
    .map(([density, v]) => ({ density, ...v }))
    .sort((a, b) => b.bytes - a.bytes)
  const topGroups = [...groupMap.entries()]
    .map(([path, v]) => ({ path, ...v }))
    .sort((a, b) => b.bytes - a.bytes)
    .slice(0, 12)

  return {
    entryCount: entries.length,
    compressedBytes,
    uncompressedBytes,
    dexFiles,
    abis,
    densities,
    topGroups,
    nativeLibCount
  }
}

function dexIndex(name: string): number {
  const m = /classes(\d*)\.dex$/.exec(name)
  if (!m) return 0
  const n = parseInt(m[1] || '0', 10)
  return Number.isFinite(n) ? n : 0
}

function groupKey(name: string): string {
  const parts = name.split('/')
  if (parts.length === 1) return name // 根目录文件（classes.dex / AndroidManifest.xml / resources.arsc ...）
  if (parts[0] === 'lib') return parts.length >= 2 ? `lib/${parts[1]}` : 'lib'
  if (parts[0] === 'META-INF') return 'META-INF'
  // res/<type-density> 与 assets/<首段> 取前两段，其余取首段
  if (parts.length >= 2) return `${parts[0]}/${parts[1]}`
  return parts[0]
}

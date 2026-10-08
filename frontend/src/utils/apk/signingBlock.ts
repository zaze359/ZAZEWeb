// APK Signing Block 解析（v2 / v3 / v3.1）。
// 块位于 ZIP 中央目录之前，末尾 16 字节为 magic "APK Sig Block 42"。
// 块内为 (uint64 length, uint32 id, value) 序列；按 ID 分派 signer 结构。

import { readSlice } from './zipReader'

const SIG_BLOCK_MAGIC = [
  0x41, 0x50, 0x4b, 0x20, 0x53, 0x69, 0x67, 0x20, 0x42, 0x6c, 0x6f, 0x63, 0x6b, 0x20, 0x34, 0x32
]
const ID_V2 = 0x7109871a
const ID_V3 = 0xf05368c0
const ID_V3_1 = 0x1b93ad61
const MAX_BLOCK = 4 * 1024 * 1024

function u64(buf: Uint8Array, off: number): number {
  const lo = new DataView(buf.buffer, buf.byteOffset + off, 4).getUint32(0, true)
  const hi = new DataView(buf.buffer, buf.byteOffset + off + 4, 4).getUint32(0, true)
  return hi * 2 ** 32 + lo
}
function u32(buf: Uint8Array, off: number): number {
  return new DataView(buf.buffer, buf.byteOffset + off, 4).getUint32(0, true)
}

export interface SigningBlock {
  schemes: { v2: boolean; v3: boolean; v3_1: boolean }
  v2Value?: Uint8Array
  v3Value?: Uint8Array
  v3_1Value?: Uint8Array
  degraded: string[]
}

/** 读取 APK Signing Block，识别 v2/v3/v3.1 是否存在（仅存在性，不做有效性）。 */
export async function readSigningBlock(file: File | Blob, cdOffset: number): Promise<SigningBlock> {
  const result: SigningBlock = { schemes: { v2: false, v3: false, v3_1: false }, degraded: [] }
  if (cdOffset < 24) return result

  const tail = await readSlice(file, cdOffset - 24, cdOffset)
  for (let i = 0; i < 16; i++) {
    if (tail[8 + i] !== SIG_BLOCK_MAGIC[i]) return result // 无 v2/v3 块（非错误）
  }

  const size2 = u64(tail, 0)
  if (size2 + 8 > MAX_BLOCK || size2 + 8 > cdOffset) {
    result.degraded.push('APK Signing Block 声明长度越界，未解析 v2/v3 签名')
    return result
  }

  const blockStart = cdOffset - (size2 + 8)
  let block: Uint8Array
  try {
    block = await readSlice(file, blockStart, cdOffset)
  } catch {
    result.degraded.push('读取 APK Signing Block 失败')
    return result
  }

  // 块结构：[size(8)] 条目… [size(8)] magic(16)；条目区在 [8, length-24]
  let p = 8
  const endPairs = block.length - 24
  while (p + 8 <= endPairs) {
    const len = u64(block, p)
    p += 8
    if (len < 4) {
      result.degraded.push('Signing Block 条目长度异常，停止解析')
      break
    }
    const id = u32(block, p)
    p += 4
    if (p + len > endPairs) {
      result.degraded.push('Signing Block 条目越界，停止解析')
      break
    }
    const value = block.subarray(p, p + len)
    p += len
    if (id === ID_V2) {
      result.schemes.v2 = true
      result.v2Value = value
    } else if (id === ID_V3) {
      result.schemes.v3 = true
      result.v3Value = value
    } else if (id === ID_V3_1) {
      result.schemes.v3_1 = true
      result.v3_1Value = value
    }
  }
  return result
}

function readLenPrefixed(buf: Uint8Array, off: number): { data: Uint8Array; next: number } {
  const len = u32(buf, off)
  const dataStart = off + 4
  if (dataStart + len > buf.length) throw new Error('length-prefixed 越界')
  return { data: buf.subarray(dataStart, dataStart + len), next: dataStart + len }
}

/** 从单个 scheme 块的值中按 signer 结构取出 X.509 DER 证书列表。v2/v3 的 signer 结构不同，必须分派。 */
export function extractSignerCertificates(value: Uint8Array, scheme: 'v2' | 'v3'): Uint8Array[] {
  const certs: Uint8Array[] = []
  let p = 0
  while (p + 4 <= value.length) {
    let signer: Uint8Array
    try {
      const r = readLenPrefixed(value, p)
      signer = r.data
      p = r.next
    } catch {
      break
    }
    try {
      certs.push(...parseSigner(signer, scheme))
    } catch {
      // 单 signer 解析失败不影响其它
    }
  }
  return certs
}

function parseSigner(signer: Uint8Array, scheme: 'v2' | 'v3'): Uint8Array[] {
  let p = 0
  const signedData = readLenPrefixed(signer, p)
  p = signedData.next
  if (scheme === 'v3') {
    if (p + 8 > signer.length) throw new Error('v3 signer 缺 min/maxSdk')
    p += 8 // uint32 minSdkVersion + uint32 maxSdkVersion
  }
  const sigs = readLenPrefixed(signer, p) // signatures，跳过
  p = sigs.next
  readLenPrefixed(signer, p) // public key，跳过
  return parseSignedData(signedData.data)
}

function parseSignedData(signedData: Uint8Array): Uint8Array[] {
  let p = 0
  const digests = readLenPrefixed(signedData, p)
  p = digests.next
  const certificates = readLenPrefixed(signedData, p)
  p = certificates.next
  // attrs 跳过（如有）
  const certs: Uint8Array[] = []
  let cp = 0
  while (cp + 4 <= certificates.data.length) {
    const r = readLenPrefixed(certificates.data, cp)
    cp = r.next
    certs.push(r.data) // 每个 chunk 即一张完整 X.509 DER
  }
  return certs
}

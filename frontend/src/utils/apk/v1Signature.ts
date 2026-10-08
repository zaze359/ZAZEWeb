// v1（JAR 签名）证书提取：定位 META-INF/*.RSA|DSA|EC 条目 → 读字节（必要时解压）→ 导航 PKCS#7 → 证书 DER。

import { readEntryBytes, type ZipEntry } from './zipReader'
import { parseDer, type DerNode, DerError } from './der'

const V1_RE = /^META-INF\/.*\.(RSA|DSA|EC)$/i

export interface V1Result {
  entryNames: string[]
  certs: Uint8Array[]
  degraded: string[]
}

export async function analyzeV1(file: File | Blob, entries: ZipEntry[]): Promise<V1Result> {
  const result: V1Result = { entryNames: [], certs: [], degraded: [] }
  const sigEntries = entries.filter((e) => V1_RE.test(e.name))
  result.entryNames = sigEntries.map((e) => e.name)

  for (const e of sigEntries) {
    try {
      const bytes = await readEntryBytes(file, e, { maxBytes: 512 * 1024 })
      result.certs.push(...extractPkcs7Certs(bytes))
    } catch (err) {
      const msg = (err as Error).message || '读取失败'
      if (!result.degraded.includes(msg)) result.degraded.push(msg)
    }
  }
  return result
}

export function extractPkcs7Certs(bytes: Uint8Array): Uint8Array[] {
  // parseDer 返回的 root 即为 ContentInfo ::= SEQUENCE（已是顶层节点，无需再取 children[0]）。
  const contentInfo = parseDer(bytes)
  if (contentInfo.tag !== 0x30) throw new DerError('PKCS#7 根非 SEQUENCE')

  // ContentInfo ::= SEQUENCE { contentType OID, content [0] EXPLICIT }
  const explicit = contentInfo.children?.[1]
  if (!explicit || explicit.tag !== 0xa0) throw new DerError('PKCS#7 缺少 EXPLICIT 内容')
  const signedDataNode = explicit.children?.[0]
  if (!signedDataNode || signedDataNode.tag !== 0x30) throw new DerError('PKCS#7 缺少 SignedData')

  // SignedData ::= SEQUENCE { version, digestAlgorithms, encapContentInfo, [0] certificates, [1] crls, signerInfos }
  const children = signedDataNode.children || []
  let certSet: DerNode | undefined
  for (const c of children) {
    if (c.tag === 0xa0) {
      certSet = c
      break
    }
  }
  if (!certSet) throw new DerError('PKCS#7 缺少 certificates 字段')

  const certs: Uint8Array[] = []
  for (const cc of certSet.children || []) {
    if (cc.tag === 0x30) certs.push(cc.full) // 完整证书 DER
  }
  return certs
}

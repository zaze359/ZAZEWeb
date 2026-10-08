// 签名分析编排：探测 v1/v2/v3/v3.1 方案 + 提取全部证书 + 计算指纹（SHA-256/SHA-1）+ 去重 + 降级收敛。
// 只检测方案与证书，**绝不**校验签名有效性（integrityVerified 恒为字面量 false）。

import { readSigningBlock, extractSignerCertificates } from './signingBlock'
import { analyzeV1 } from './v1Signature'
import { parseCertificate } from './x509'
import type { ZipEntry } from './zipReader'
import type { ApkSignatureSummary, ApkSignerCert } from './types'

export async function analyzeSignature(
  file: File | Blob,
  entries: ZipEntry[],
  cdOffset: number
): Promise<ApkSignatureSummary | null> {
  const degraded: string[] = []
  try {
    const block = await readSigningBlock(file, cdOffset)
    degraded.push(...block.degraded)

    const v1 = await analyzeV1(file, entries)
    degraded.push(...v1.degraded)

    const schemes = {
      v1: v1.entryNames.length > 0,
      v2: block.schemes.v2,
      v3: block.schemes.v3,
      v3_1: block.schemes.v3_1
    }

    const certDers: Uint8Array[] = []
    if (block.v2Value) certDers.push(...extractSignerCertificates(block.v2Value, 'v2'))
    if (block.v3Value) certDers.push(...extractSignerCertificates(block.v3Value, 'v3'))
    if (block.v3_1Value) certDers.push(...extractSignerCertificates(block.v3_1Value, 'v3'))
    certDers.push(...v1.certs)

    const certs = await buildCerts(certDers)
    const hasAny = schemes.v1 || schemes.v2 || schemes.v3 || schemes.v3_1

    return {
      schemes,
      v1EntryNames: v1.entryNames,
      signerCount: certs.length,
      certs,
      integrityVerified: false,
      degraded: hasAny ? degraded.filter(Boolean) : degraded.filter(Boolean)
    }
  } catch (e) {
    return {
      schemes: { v1: false, v2: false, v3: false, v3_1: false },
      v1EntryNames: [],
      signerCount: 0,
      certs: [],
      integrityVerified: false,
      degraded: [String((e as Error).message || e)]
    }
  }
}

async function buildCerts(ders: Uint8Array[]): Promise<ApkSignerCert[]> {
  const out: ApkSignerCert[] = []
  const seen = new Set<string>()
  for (const der of ders) {
    let raw
    try {
      raw = parseCertificate(der)
    } catch {
      continue
    }
    let sha256: string | undefined
    let sha1: string | undefined
    let fingerprintError: string | undefined
    try {
      if (typeof crypto !== 'undefined' && crypto.subtle) {
        const d256 = await crypto.subtle.digest('SHA-256', der)
        sha256 = toHexColon(d256)
        const d1 = await crypto.subtle.digest('SHA-1', der)
        sha1 = toHexColon(d1)
      } else {
        fingerprintError = '当前环境不支持 crypto.subtle，无法计算指纹'
      }
    } catch (e) {
      fingerprintError = '指纹计算失败：' + (e as Error).message
    }

    const key = sha256 || `${raw.subject}|${raw.serial || ''}`
    if (seen.has(key)) continue
    seen.add(key)

    const subject = raw.subject
    out.push({
      subject,
      issuer: raw.issuer,
      subjectCN: raw.subjectCN,
      serial: raw.serial,
      sha256,
      sha1,
      fingerprintError,
      notBefore: raw.notBefore,
      notAfter: raw.notAfter,
      signatureAlgorithm: raw.signatureAlgorithm,
      selfSigned: subject === raw.issuer,
      debugCert: /CN\s*=\s*Android\s*Debug/i.test(subject)
    })
  }
  return out
}

function toHexColon(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf)
  const parts: string[] = []
  for (let i = 0; i < bytes.length; i++) {
    parts.push(bytes[i].toString(16).padStart(2, '0').toUpperCase())
  }
  return parts.join(':')
}

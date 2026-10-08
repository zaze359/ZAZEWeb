// X.509 证书字段解析（基于 der.ts）。输入为完整证书 DER，输出 subject/issuer/serial/有效期/签名算法。
// Name 拼接按 RDN 逆序（对齐 openssl subject= 输出）。

import { DerNode, parseDer, readOid, readInteger, readString, DerError } from './der'

const OID_NAME: Record<string, string> = {
  '2.5.4.3': 'CN',
  '2.5.4.6': 'C',
  '2.5.4.7': 'L',
  '2.5.4.8': 'ST',
  '2.5.4.10': 'O',
  '2.5.4.11': 'OU',
  '1.2.840.113549.1.9.1': 'E'
}

const SIG_ALG_OID: Record<string, string> = {
  '1.2.840.113549.1.1.11': 'SHA256withRSA',
  '1.2.840.113549.1.1.12': 'SHA384withRSA',
  '1.2.840.113549.1.1.13': 'SHA512withRSA',
  '1.2.840.113549.1.1.5': 'SHA1withRSA',
  '1.3.14.3.2.29': 'SHA1withRSA',
  '1.2.840.10045.4.3.2': 'SHA256withECDSA',
  '1.2.840.10045.4.3.3': 'SHA384withECDSA',
  '1.2.840.10045.4.3.4': 'SHA512withECDSA',
  '2.16.840.1.101.3.4.3.2': 'SHA256withDSA',
  '2.16.840.1.101.3.4.3.3': 'SHA384withDSA',
  '2.16.840.1.101.3.4.3.4': 'SHA512withDSA',
  '1.2.840.113549.1.10': 'RSASSA-PSS'
}

export interface RawCert {
  subject: string
  issuer: string
  subjectCN?: string
  serial?: string
  notBefore?: string
  notAfter?: string
  signatureAlgorithm?: string
}

function formatName(nameNode: DerNode): { name: string; cn?: string } {
  const rdns: string[] = []
  let cn: string | undefined
  const seq = nameNode.children || []
  for (const rdnSet of seq) {
    if (rdnSet.tag !== 0x31) continue // SET OF RDN
    const items = rdnSet.children || []
    const parts: string[] = []
    for (const attrSeq of items) {
      if (attrSeq.tag !== 0x30) continue // SEQUENCE { OID, value }
      const oidNode = attrSeq.children?.[0]
      const valNode = attrSeq.children?.[1]
      if (!oidNode || !valNode) continue
      const oid = readOid(oidNode)
      const short = OID_NAME[oid] || oid
      const val = readString(valNode)
      if (short === 'CN' && cn === undefined) cn = val
      parts.push(`${short}=${val}`)
    }
    if (parts.length) rdns.push(parts.join('+'))
  }
  return { name: rdns.slice().reverse().join(', '), cn }
}

function formatTime(node: DerNode): string | undefined {
  const tag = node.tag
  const s = readString(node)
  if (!s) return undefined
  try {
    if (tag === 0x17) {
      // UTCTime YYMMDDHHMMSSZ
      const year = parseInt(s.slice(0, 2), 10)
      const fullYear = year >= 50 ? 1900 + year : 2000 + year
      const rest = s.slice(2) // MMDDHHMMSSZ
      return `${fullYear}-${rest.slice(0, 2)}-${rest.slice(2, 4)}T${rest.slice(4, 6)}:${rest.slice(6, 8)}:${rest.slice(8, 10)}Z`
    }
    if (tag === 0x18) {
      // GeneralizedTime YYYYMMDDHHMMSSZ
      return `${s.slice(0, 4)}-${s.slice(4, 6)}-${s.slice(6, 8)}T${s.slice(8, 10)}:${s.slice(10, 12)}:${s.slice(12, 14)}Z`
    }
  } catch {
    return s
  }
  return s
}

export function parseCertificate(der: Uint8Array): RawCert {
  // parseDer 返回的 root 即为 Certificate ::= SEQUENCE（已是顶层节点，无需再取 children[0]）。
  const certSeq = parseDer(der)
  if (certSeq.tag !== 0x30) throw new DerError('Certificate 根不是 SEQUENCE')
  const tbs = certSeq.children?.[0]
  if (!tbs || tbs.tag !== 0x30) throw new DerError('缺少 tbsCertificate')

  const fields = tbs.children || []
  let startIdx = 0
  if (fields[0] && fields[0].tag === 0xa0) startIdx = 1 // version [0] EXPLICIT 可选

  const serialNode = fields[startIdx]
  const sigAlgNode = fields[startIdx + 1]
  const issuerNode = fields[startIdx + 2]
  const validityNode = fields[startIdx + 3]
  const subjectNode = fields[startIdx + 4]

  if (!serialNode || !issuerNode || !validityNode || !subjectNode) {
    throw new DerError('Certificate 字段不足')
  }

  // 序列号首字节若为 0x00（DER 正整数符号位补零），去掉以便与 apksigner/openssl 显示一致。
  let serial = readInteger(serialNode).toUpperCase()
  if (serial.length > 2 && serial.startsWith('00')) serial = serial.slice(2)
  const sigOid = sigAlgNode.children?.[0] ? readOid(sigAlgNode.children[0]) : ''
  const signatureAlgorithm = SIG_ALG_OID[sigOid] || sigOid || undefined

  const issuer = formatName(issuerNode)
  const subject = formatName(subjectNode)

  const nb = validityNode.children?.[0]
  const na = validityNode.children?.[1]

  return {
    subject: subject.name,
    issuer: issuer.name,
    subjectCN: subject.cn,
    serial: serial || undefined,
    notBefore: nb ? formatTime(nb) : undefined,
    notAfter: na ? formatTime(na) : undefined,
    signatureAlgorithm
  }
}

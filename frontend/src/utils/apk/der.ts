// 最小 DER / ASN.1 解析（仅 definite length，长格式长度最多 4 字节）。
// 用于从 APK 签名块与 PKCS#7 中取 X.509 证书并读字段。
// 严格边界与递归深度保护，防止畸形输入导致死循环 / 越界（design.md §4.1）。

export class DerError extends Error {
  constructor(msg: string) {
    super(msg)
    this.name = 'DerError'
  }
}

export interface DerNode {
  tag: number
  headerLength: number // tag + length 字节数
  length: number // content 长度
  value: Uint8Array // content 字节（primitive）
  children?: DerNode[] // constructed 时递归
  full: Uint8Array // 完整编码（tag + length + value），用于取出完整证书 DER
}

const MAX_DEPTH = 24

export function parseDer(bytes: Uint8Array, maxDepth = MAX_DEPTH): DerNode {
  return parseNode(bytes, 0, bytes.length, maxDepth)
}

function parseNode(bytes: Uint8Array, start: number, end: number, depth: number): DerNode {
  if (depth < 0) throw new DerError('DER 递归过深')
  if (start + 1 > end) throw new DerError('DER 数据不足（缺少 tag）')
  const tag = bytes[start]
  let p = start + 1
  if (p >= end) throw new DerError('DER 数据不足（缺少 length）')

  let len = bytes[p]
  p++
  if (len & 0x80) {
    const numBytes = len & 0x7f
    if (numBytes === 0) throw new DerError('不支持不定长 DER')
    if (numBytes > 4) throw new DerError('DER 长度字段过长')
    if (p + numBytes > end) throw new DerError('DER 长度字段越界')
    len = 0
    for (let i = 0; i < numBytes; i++) {
      len = (len << 8) | bytes[p]
      p++
    }
  }

  const headerLength = p - start
  if (len < 0 || start + headerLength + len > end) throw new DerError('DER 内容越界')
  const contentStart = start + headerLength
  const contentEnd = contentStart + len
  const value = bytes.subarray(contentStart, contentEnd)
  const full = bytes.subarray(start, contentEnd)

  const node: DerNode = { tag, headerLength, length: len, value, full }
  if (tag & 0x20) {
    node.children = []
    let cp = contentStart
    while (cp < contentEnd) {
      const child = parseNode(bytes, cp, contentEnd, depth - 1)
      node.children.push(child)
      cp += child.headerLength + child.length
      if (cp <= cp - (child.headerLength + child.length)) {
        throw new DerError('DER 指针未前进（畸形）')
      }
    }
  }
  return node
}

export function readOid(node: DerNode): string {
  const b = node.value
  if (b.length === 0) return ''
  const first = b[0]
  const x = Math.floor(first / 40)
  const y = first % 40
  const parts = [String(x), String(y)]
  let val = 0
  for (let i = 1; i < b.length; i++) {
    const byte = b[i]
    val = (val << 7) | (byte & 0x7f)
    if ((byte & 0x80) === 0) {
      parts.push(String(val))
      val = 0
    }
  }
  return parts.join('.')
}

export function readInteger(node: DerNode): string {
  const b = node.value
  if (b.length === 0) return ''
  let hex = ''
  for (let i = 0; i < b.length; i++) hex += b[i].toString(16).padStart(2, '0')
  return hex
}

export function readString(node: DerNode): string {
  const tag = node.tag
  const b = node.value
  try {
    if (tag === 0x1e) {
      // BMPString (UTF-16BE)
      let s = ''
      for (let i = 0; i + 1 < b.length; i += 2) s += String.fromCharCode((b[i] << 8) | b[i + 1])
      return s
    }
    if (tag === 0x14) {
      // T61String / TeletexString —— 近似按 Latin-1 解码
      let s = ''
      for (let i = 0; i < b.length; i++) s += String.fromCharCode(b[i])
      return s
    }
    if (
      tag === 0x0c || // UTF8String
      tag === 0x13 || // PrintableString
      tag === 0x16 || // IA5String
      tag === 0x12 || // NumericString
      tag === 0x1a || // VisibleString
      tag === 0x1b // GeneralString
    ) {
      return new TextDecoder('utf-8').decode(b)
    }
  } catch {
    /* fallthrough */
  }
  let s = ''
  for (let i = 0; i < b.length; i++) s += String.fromCharCode(b[i])
  return s
}

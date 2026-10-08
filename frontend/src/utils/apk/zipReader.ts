// 轻量 ZIP 中央目录读取器（只读目录，不解压业务数据）。
// 目的：在 200MB 包上秒级得到条目清单，用于「文件构成」统计与签名条目定位。
// 仅依赖 File/ArrayBuffer/DataView，零外部依赖。支持 ZIP64（超大包 / >65535 条目）。

export interface ZipEntry {
  name: string
  method: number // 0 = STORED, 8 = DEFLATE
  crc32: number
  compressedSize: number
  uncompressedSize: number
  localHeaderOffset: number
}

export interface CentralDirectory {
  entries: ZipEntry[]
  cdOffset: number
}

const EOCD_SIG = 0x06054b50
const EOCD64_LOC_SIG = 0x07064b50
const EOCD64_SIG = 0x06064b50
const CDH_SIG = 0x02014b50
const LFH_SIG = 0x04034b50

// 小端读取：从 Uint8Array 的指定偏移取多字节整数（切片按自身偏移换算）。
function u16(buf: Uint8Array, off: number): number {
  return new DataView(buf.buffer, buf.byteOffset + off, 2).getUint16(0, true)
}
function u32(buf: Uint8Array, off: number): number {
  return new DataView(buf.buffer, buf.byteOffset + off, 4).getUint32(0, true)
}
function u64(buf: Uint8Array, off: number): number {
  const lo = new DataView(buf.buffer, buf.byteOffset + off, 4).getUint32(0, true)
  const hi = new DataView(buf.buffer, buf.byteOffset + off + 4, 4).getUint32(0, true)
  return hi * 2 ** 32 + lo
}

/** 读文件的一段（半开区间），返回 Uint8Array */
export async function readSlice(file: File | Blob, start: number, end: number): Promise<Uint8Array> {
  const buf = await file.slice(start, end).arrayBuffer()
  return new Uint8Array(buf)
}

async function findEocd(file: File | Blob): Promise<{ eocd: Uint8Array; eocdOffset: number }> {
  const fileSize = file.size
  // EOCD + 最大注释长度
  const maxScan = 22 + 65535
  const start = Math.max(0, fileSize - maxScan)
  const tail = await readSlice(file, start, fileSize)
  // 从尾部向前找 EOCD 签名
  for (let i = tail.length - 22; i >= 0; i--) {
    if (u32(tail, i) === EOCD_SIG) {
      return { eocd: tail.subarray(i), eocdOffset: start + i }
    }
  }
  throw new Error('未找到 ZIP 中央目录结尾（EOCD），文件可能损坏或非 APK/ZIP')
}

/** 解析 ZIP64 扩展字段（id 0x0001），按需取 64 位值 */
function applyZip64Extra(
  extra: Uint8Array,
  fields: { compSize: number; uncompSize: number; localHeaderOffset: number; diskStart: number }
): void {
  let off = 0
  while (off + 4 <= extra.length) {
    const id = u16(extra, off)
    const size = u16(extra, off + 2)
    const dataStart = off + 4
    if (id === 0x0001) {
      let p = dataStart
      if (fields.uncompSize === 0xffffffff && p + 8 <= dataStart + size) {
        fields.uncompSize = u64(extra, p)
        p += 8
      }
      if (fields.compSize === 0xffffffff && p + 8 <= dataStart + size) {
        fields.compSize = u64(extra, p)
        p += 8
      }
      if (fields.localHeaderOffset === 0xffffffff && p + 8 <= dataStart + size) {
        fields.localHeaderOffset = u64(extra, p)
        p += 8
      }
      if (fields.diskStart === 0xffffffff && p + 4 <= dataStart + size) {
        fields.diskStart = u32(extra, p)
        p += 4
      }
    }
    off += 4 + size
  }
}

/** 读取中央目录，返回条目列表与中央目录偏移（ZIP64 已展开） */
export async function readCentralDirectory(file: File | Blob): Promise<CentralDirectory> {
  const { eocd, eocdOffset } = await findEocd(file)

  let totalEntries = u16(eocd, 10)
  let cdSize = u32(eocd, 12)
  let cdOffset = u32(eocd, 16)

  // ZIP64 探测：条目数或偏移为哨兵
  if (totalEntries === 0xffff || cdOffset === 0xffffffff) {
    // EOCD64 locator 在 EOCD 前 20 字节（固定）
    const locStart = eocdOffset - 20
    if (locStart >= 0) {
      const loc = await readSlice(file, locStart, eocdOffset)
      if (u32(loc, 0) === EOCD64_LOC_SIG) {
        const eocd64Offset = u64(loc, 8)
        const eocd64 = await readSlice(
          file,
          eocd64Offset,
          Math.min(file.size, eocd64Offset + 56)
        )
        if (u32(eocd64, 0) === EOCD64_SIG) {
          totalEntries = u64(eocd64, 32) & 0xffffffff
          cdSize = u64(eocd64, 40) & 0xffffffff
          cdOffset = u64(eocd64, 48) & 0xffffffff
        }
      }
    }
  }

  const cdEnd = Math.min(file.size, cdOffset + cdSize)
  const cd = await readSlice(file, cdOffset, cdEnd)

  const entries: ZipEntry[] = []
  let p = 0
  while (p + 46 <= cd.length) {
    if (u32(cd, p) !== CDH_SIG) break
    const method = u16(cd, p + 10)
    const crc32 = u32(cd, p + 16)
    let compressedSize = u32(cd, p + 20)
    let uncompressedSize = u32(cd, p + 24)
    const nameLen = u16(cd, p + 28)
    const extraLen = u16(cd, p + 30)
    const commentLen = u16(cd, p + 32)
    let localHeaderOffset = u32(cd, p + 42)
    const flags = u16(cd, p + 8)

    const nameBytes = cd.subarray(p + 46, p + 46 + nameLen)
    const name = decodeName(nameBytes, flags)

    if (
      compressedSize === 0xffffffff ||
      uncompressedSize === 0xffffffff ||
      localHeaderOffset === 0xffffffff
    ) {
      const extra = cd.subarray(p + 46 + nameLen, p + 46 + nameLen + extraLen)
      const fields = {
        compSize: compressedSize,
        uncompSize: uncompressedSize,
        localHeaderOffset,
        diskStart: 0
      }
      applyZip64Extra(extra, fields)
      compressedSize = fields.compSize
      uncompressedSize = fields.uncompSize
      localHeaderOffset = fields.localHeaderOffset
    }

    entries.push({ name, method, crc32, compressedSize, uncompressedSize, localHeaderOffset })
    p += 46 + nameLen + extraLen + commentLen
  }

  return { entries, cdOffset }
}

function decodeName(bytes: Uint8Array, flags: number): string {
  const isUtf8 = (flags & 0x0800) !== 0
  if (isUtf8) {
    try {
      return new TextDecoder('utf-8').decode(bytes)
    } catch {
      /* fallthrough */
    }
  }
  // Latin-1（APK 条目名实际上全为 ASCII）
  let s = ''
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i])
  return s
}

/** 读取某个 ZIP 条目的原始字节（用于 v1 签名文件解析）。 */
export async function readEntryBytes(
  file: File | Blob,
  entry: ZipEntry,
  opts: { maxBytes?: number } = {}
): Promise<Uint8Array> {
  const maxBytes = opts.maxBytes ?? 512 * 1024
  const lfh = await readSlice(file, entry.localHeaderOffset, entry.localHeaderOffset + 30)
  if (u32(lfh, 0) !== LFH_SIG) {
    throw new Error('本地文件头签名不匹配')
  }
  const nameLen = u16(lfh, 26)
  const extraLen = u16(lfh, 28)
  const dataStart = entry.localHeaderOffset + 30 + nameLen + extraLen
  const dataEnd = Math.min(file.size, dataStart + entry.compressedSize)
  const compressed = await readSlice(file, dataStart, dataEnd)

  if (entry.method === 0) {
    return compressed.subarray(0, Math.min(compressed.length, entry.uncompressedSize || compressed.length))
  }
  if (entry.method === 8) {
    return inflateRaw(compressed, maxBytes)
  }
  throw new Error(`不支持的压缩方式 method=${entry.method}`)
}

export async function inflateRaw(bytes: Uint8Array, maxBytes?: number): Promise<Uint8Array> {
  if (typeof DecompressionStream === 'undefined') {
    throw new Error('当前浏览器不支持 DecompressionStream，无法解压该条目')
  }
  const ds = new DecompressionStream('deflate-raw')
  const writer = ds.writable.getWriter()
  void writer.write(bytes)
  void writer.close()
  const reader = ds.readable.getReader()
  const chunks: Uint8Array[] = []
  let total = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    if (value) {
      total += value.byteLength
      if (maxBytes != null && total > maxBytes) {
        throw new Error('条目解压后超过阈值')
      }
      chunks.push(value)
    }
  }
  const out = new Uint8Array(total)
  let off = 0
  for (const c of chunks) {
    out.set(c, off)
    off += c.byteLength
  }
  return out
}

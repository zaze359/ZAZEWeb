// 通用格式化工具（字节 / 数量）。放 utils/ 根目录，供 portal 与 admin 共用。

/** 字节数 → 人类可读（B / KB / MB / GB）。decimals 控制小数位。 */
export function formatBytes(bytes: number, decimals = 1): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  const fixed = value.toFixed(decimals)
  // 去掉多余的 .0
  const trimmed = fixed.endsWith('.0') ? fixed.slice(0, -2) : fixed
  return `${trimmed} ${units[i]}`
}

/** 大整数加千分位 */
export function formatCount(n: number): string {
  if (!Number.isFinite(n)) return '-'
  return n.toLocaleString('en-US')
}

/** 0x7f0b0001 之类的 16 进制资源引用 → 是否形如 @type/name（资源未解析） */
export function isResourceRef(value: unknown): value is string {
  return typeof value === 'string' && /^@/.test(value)
}

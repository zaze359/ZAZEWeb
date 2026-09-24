import type { AppVo } from '@/types'

// 后端目前未提供 grade / heat 字段，这里从已有字段派生，用于门户游戏化展示。
// 待后端补齐（见任务 #8：列表 category / sort / heat 字段）后改为直接读取。
export type Grade = 'S' | 'A' | 'B' | 'C' | 'D'

export function deriveGrade(app: AppVo): Grade {
  const v = app.versionCount ?? 0
  if (v >= 8) return 'S'
  if (v >= 5) return 'A'
  if (v >= 3) return 'B'
  if (v >= 1) return 'C'
  return 'D'
}

export function deriveHeat(app: AppVo): number {
  // 0~100，随版本数增长，封顶 100
  const v = app.versionCount ?? 0
  return Math.max(8, Math.min(100, v * 11 + 8))
}

export const GRADE_COLOR: Record<Grade, string> = {
  S: '#ff4ecd',
  A: '#2ce8ff',
  B: '#9b5cff',
  C: '#fde24a',
  D: '#a99fd6'
}

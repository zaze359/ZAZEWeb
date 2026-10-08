// API level → Android 版本名（仅用于展示，未知 level 显示 API n）。

const API_TO_ANDROID: Record<number, string> = {
  1: '1.0',
  2: '1.1',
  3: '1.5 Cupcake',
  4: '1.6 Donut',
  5: '2.0 Eclair',
  6: '2.0.1 Eclair',
  7: '2.1 Eclair',
  8: '2.2 Froyo',
  9: '2.3 Gingerbread',
  10: '2.3.3 Gingerbread',
  11: '3.0 Honeycomb',
  12: '3.1 Honeycomb',
  13: '3.2 Honeycomb',
  14: '4.0 Ice Cream Sandwich',
  15: '4.0.3 Ice Cream Sandwich',
  16: '4.1 Jelly Bean',
  17: '4.2 Jelly Bean',
  18: '4.3 Jelly Bean',
  19: '4.4 KitKat',
  20: '4.4W KitKat',
  21: '5.0 Lollipop',
  22: '5.1 Lollipop',
  23: '6.0 Marshmallow',
  24: '7.0 Nougat',
  25: '7.1 Nougat',
  26: '8.0 Oreo',
  27: '8.1 Oreo',
  28: '9 Pie',
  29: '10',
  30: '11',
  31: '12',
  32: '12L',
  33: '13',
  34: '14',
  35: '15'
}

export function androidVersionName(apiLevel: number | undefined): string | undefined {
  if (apiLevel == null || !Number.isFinite(apiLevel)) return undefined
  return API_TO_ANDROID[apiLevel] ?? `API ${apiLevel}`
}

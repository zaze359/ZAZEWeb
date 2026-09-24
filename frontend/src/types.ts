// 后端 VO 的 TS 镜像，字段与 feature:appmarket / feature:auth 保持一致。
// 改动后端 VO 时这里同步即可，组件层不感知。

export interface UserVo {
  username: string
  displayName?: string
  role?: 'ADMIN' | 'USER'
}

export interface DownloadSource {
  id?: string
  sourceType: string
  sourceName?: string
  downloadUrl: string
  region?: string
  note?: string
}

export interface AppVersion {
  id?: string
  versionName?: string
  releaseDate?: number
  sizeMb?: number
  sourceCount?: number
  changelog?: string
  sources?: DownloadSource[]
}

export interface AppVo {
  id: string
  name: string
  packageName?: string
  iconUrl?: string
  category?: string
  developer?: string
  versionCount?: number
  summary?: string
  officialUrl?: string
  versions?: AppVersion[]
}

// 后台写入用的入参类型（与 VO 对齐，字段可缺省）
export interface AppInput {
  name: string
  packageName?: string
  category?: string
  developer?: string
  iconUrl?: string
  officialUrl?: string
  summary?: string
}

export interface VersionInput {
  versionName: string
  versionCode?: number | null
  releaseDate?: string | null // 来自 date 输入，YYYY-MM-DD
  sizeMb?: number | null
  changelog?: string
}

export interface SourceInput {
  sourceName?: string
  sourceType: string
  downloadUrl: string
  region?: string
  note?: string
}

// 从 APK 导入：浏览器本地解析后提交解析出的元数据（APK 本身不上传）
export interface ApkImportInput {
  packageName: string
  name: string
  versionName?: string | null
  versionCode?: number | null
  iconDataUri?: string | null
  sizeMb?: number | null
}

export interface CollectResult {
  targets?: number
  appsCreated?: number
  versionsAdded?: number
  sourcesAdded?: number
  messages?: string[]
}

// 统一响应信封 Response<T>{ code, data, msg }
export interface ApiResponse<T> {
  code: number
  data: T
  msg?: string
}

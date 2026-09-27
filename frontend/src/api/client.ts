import type {
  ApiResponse,
  AppVo,
  AppInput,
  VersionInput,
  SourceInput,
  ApkImportInput,
  CollectResult,
  ExternalLookupResult,
  ExternalSearchResult,
  ImportSource,
  ImportTaskRef,
  UserVo,
  PageResult,
  CategoryCount
} from '@/types'

// 同源调用后端已有 /api/v1/* 接口；Session Cookie 由浏览器自动携带，无需手动处理鉴权头。
const BASE = '/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    ...init
  })

  // 未登录：后端 /api/** 返回 401。已在 /login 时不重复跳转，避免重定向死循环。
  if (res.status === 401) {
    if (window.location.pathname !== '/login') window.location.href = '/login'
    throw new Error('UNAUTHORIZED')
  }

  const body = (await res.json()) as ApiResponse<T>
  if (body.code !== 0 && body.code !== 200) {
    throw new Error(body.msg || '请求失败')
  }
  return body.data
}

// JSON 体的写操作封装（POST/PUT），自动 stringify。
async function json<T>(method: string, path: string, data?: unknown): Promise<T> {
  return request<T>(path, { method, body: data != null ? JSON.stringify(data) : undefined })
}

export const api = {
  me: () => request<UserVo | null>('/auth/me'),
  login: (username: string, password: string) =>
    json<UserVo>('POST', '/auth/login', { username, password }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  // 门户应用列表：服务端分页。支持 keyword / category / sort / page / size。
  // 返回 PageResult<AppVo>（list + total + page + size），门户据此做滚动加载更多。
  apps: (opts?: {
    keyword?: string
    category?: string
    sort?: string
    page?: number
    size?: number
  }) => {
    const q = new URLSearchParams()
    if (opts?.keyword) q.set('keyword', opts.keyword)
    if (opts?.category) q.set('category', opts.category)
    if (opts?.sort) q.set('sort', opts.sort)
    if (opts?.page != null) q.set('page', String(opts.page))
    if (opts?.size != null) q.set('size', String(opts.size))
    const qs = q.toString()
    return request<PageResult<AppVo>>('/appmarket/apps' + (qs ? `?${qs}` : ''))
  },
  // 门户分类计数（左侧 rail 用），独立接口、一次性拉取
  appCategories: () => request<CategoryCount[]>('/appmarket/apps/categories'),
  appDetail: (id: string) => request<AppVo>('/appmarket/apps/' + id),

  // 后台管理接口（base: /api/v1/appmarket/admin）
    admin: {
    // 管理端应用列表：服务端分页（page / size / keyword）
    listApps: (opts?: { page?: number; size?: number; keyword?: string }) => {
      const q = new URLSearchParams()
      if (opts?.page != null) q.set('page', String(opts.page))
      if (opts?.size != null) q.set('size', String(opts.size))
      if (opts?.keyword) q.set('keyword', opts.keyword)
      const qs = q.toString()
      return request<PageResult<AppVo>>('/appmarket/admin/apps' + (qs ? `?${qs}` : ''))
    },
    getApp: (id: string) => request<AppVo>('/appmarket/admin/apps/' + id),
    createApp: (body: AppInput) => json<unknown>('POST', '/appmarket/admin/apps', body),
    updateApp: (id: string, body: AppInput) =>
      json<unknown>('PUT', `/appmarket/admin/apps/${id}`, body),
    deleteApp: (id: string) =>
      request<unknown>(`/appmarket/admin/apps/${id}`, { method: 'DELETE' }),
    createVersion: (appId: string, body: VersionInput) =>
      json<unknown>('POST', `/appmarket/admin/apps/${appId}/versions`, body),
    deleteVersion: (id: string) =>
      request<unknown>(`/appmarket/admin/versions/${id}`, { method: 'DELETE' }),
    createSource: (versionId: string, body: SourceInput) =>
      json<unknown>('POST', `/appmarket/admin/versions/${versionId}/sources`, body),
    deleteSource: (id: string) =>
      request<unknown>(`/appmarket/admin/sources/${id}`, { method: 'DELETE' }),
    collect: () => request<CollectResult>('/appmarket/admin/collect', { method: 'POST' }),
    importFromApk: (body: ApkImportInput) =>
      json<unknown>('POST', '/appmarket/admin/import-from-apk', body),
    // 外部导入 / 应用宝元数据补全（import-tasks + SSE 实时链路）
    externalSources: () => request<ImportSource[]>('/appmarket/admin/external-sources'),
    externalLookup: (raw: string) =>
      request<ExternalLookupResult>(
        '/appmarket/admin/external-lookup?packageName=' +
          encodeURIComponent(raw) +
          '&keyword=' +
          encodeURIComponent(raw)
      ),
    externalSearch: (raw: string) =>
      request<ExternalSearchResult>(
        '/appmarket/admin/external-search?packageName=' +
          encodeURIComponent(raw) +
          '&keyword=' +
          encodeURIComponent(raw)
      ),
    startImportTask: (body: { kind: string; packageName?: string; source?: string }) => {
      // 对齐遗留脚本：仅当字段为真时才发送（batch-complete 不传 packageName/source）
      const payload: { kind: string; packageName?: string; source?: string } = { kind: body.kind }
      if (body.packageName) payload.packageName = body.packageName
      if (body.source) payload.source = body.source
      return json<ImportTaskRef>('POST', '/appmarket/admin/import-tasks', payload)
    }
  }
}

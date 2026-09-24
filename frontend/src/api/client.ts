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
  UserVo
} from '@/types'

// 同源调用后端已有 /api/v1/* 接口；Session Cookie 由浏览器自动携带，无需手动处理鉴权头。
const BASE = '/api/v1'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    ...init
  })

  // 未登录：后端 /api/** 返回 401，前端直接跳登录页（真正的边界仍在后端）。
  if (res.status === 401) {
    window.location.href = '/login'
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
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  apps: (keyword?: string) =>
    request<AppVo[]>(
      '/appmarket/apps' + (keyword ? `?keyword=${encodeURIComponent(keyword)}` : '')
    ),
  appDetail: (id: string) => request<AppVo>('/appmarket/apps/' + id),

  // 后台管理接口（base: /api/v1/appmarket/admin）
  admin: {
    listApps: () => request<AppVo[]>('/appmarket/admin/apps'),
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
      json<unknown>('/appmarket/admin/import-from-apk', body),
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
    startImportTask: (body: { kind: string; packageName?: string; source?: string }) =>
      json<ImportTaskRef>('POST', '/appmarket/admin/import-tasks', body)
  }
}

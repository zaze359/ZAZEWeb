import { reactive } from 'vue'
import { api } from '@/api/client'
import type { UserVo } from '@/types'

// 极简全局鉴权状态：应用启动拉一次 /auth/me，未登录由 client 统一跳登录页。
// 生产可换成 Pinia，但 POC 阶段 reactive 单例足够。
export const auth = reactive({
  user: null as UserVo | null,
  loading: true,
  async init() {
    try {
      this.user = await api.me()
    } catch {
      this.user = null
    } finally {
      this.loading = false
    }
  },
  async logout() {
    try {
      await api.logout()
    } finally {
      window.location.href = '/login'
    }
  }
})

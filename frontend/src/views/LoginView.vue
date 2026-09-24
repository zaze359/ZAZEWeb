<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/store/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const err = ref('')
const busy = ref(false)

async function submit() {
  err.value = ''
  if (!username.value.trim() || !password.value) {
    err.value = '请输入用户名和密码'
    return
  }
  busy.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    // 登录成功：回到首页（刷新一次以拉取最新鉴权态与导航）
    window.location.href = '/'
  } catch (e) {
    err.value = (e as Error).message || '登录失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-portal-bg px-4">
    <div class="w-full max-w-sm rounded-2xl border border-portal-border bg-portal-surface p-7 shadow-xl">
      <div class="mb-6 text-center">
        <div class="font-display text-2xl font-bold tracking-wide">
          <span class="text-portal-neon">ZAZE</span><span class="text-portal-text"> 门户</span>
        </div>
        <p class="mt-1 text-sm text-portal-muted">登录以管理应用市场</p>
      </div>

      <form class="space-y-3" @submit.prevent="submit">
        <div>
          <label class="mb-1 block text-xs text-portal-muted">用户名</label>
          <div class="flex items-center rounded-lg border border-portal-border bg-portal-surface2 px-3">
            <i class="fas fa-user text-portal-muted"></i>
            <input
              v-model="username"
              type="text"
              autocomplete="username"
              class="w-full bg-transparent px-2 py-2 text-sm text-portal-text outline-none placeholder-portal-muted"
              placeholder="用户名"
            />
          </div>
        </div>
        <div>
          <label class="mb-1 block text-xs text-portal-muted">密码</label>
          <div class="flex items-center rounded-lg border border-portal-border bg-portal-surface2 px-3">
            <i class="fas fa-lock text-portal-muted"></i>
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              class="w-full bg-transparent px-2 py-2 text-sm text-portal-text outline-none placeholder-portal-muted"
              placeholder="密码"
            />
          </div>
        </div>

        <div v-if="err" class="rounded-lg border border-red-400/50 bg-red-500/10 px-3 py-2 text-sm text-red-300">
          {{ err }}
        </div>

        <button
          type="submit"
          :disabled="busy"
          class="w-full rounded-lg bg-portal-neon px-3 py-2 text-sm font-semibold text-white transition hover:opacity-90 disabled:opacity-50"
        >
          <i class="fas" :class="busy ? 'fa-spinner fa-spin' : 'fa-sign-in-alt'"></i>
          {{ busy ? '登录中…' : '登录' }}
        </button>
      </form>

      <p class="mt-5 text-center text-xs text-portal-muted">
        <router-link to="/" class="transition hover:text-portal-text">返回首页</router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { auth } from '@/store/auth'

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
  <div class="relative flex min-h-screen items-center justify-center overflow-hidden bg-portal-bg px-4">
    <!-- 背景：单色薄荷 + 天蓝双柔光 + 极淡点阵，替代原霓虹铺底 -->
    <div
      class="pointer-events-none absolute inset-0"
      style="
        background-image: radial-gradient(60% 50% at 50% 0%, rgba(52, 216, 160, 0.13), transparent 70%),
          radial-gradient(50% 45% at 88% 92%, rgba(76, 141, 246, 0.11), transparent 70%);
      "
    ></div>
    <div
      class="pointer-events-none absolute inset-0 opacity-40"
      style="
        background-image: radial-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px);
        background-size: 20px 20px;
      "
    ></div>

    <div
      class="relative w-full max-w-sm rounded-2xl border border-portal-border bg-portal-surface/95 p-7 shadow-tile backdrop-blur"
    >
      <div class="mb-7 text-center">
        <span
          class="mx-auto grid h-10 w-10 place-items-center rounded-xl bg-portal-mint/15 font-display text-lg font-bold text-portal-mint"
          >Z</span
        >
        <h1 class="mt-3 font-display text-xl font-bold tracking-wide text-portal-text">
          ZAZE <span class="font-medium text-portal-muted">门户</span>
        </h1>
        <p class="mt-1 text-sm text-portal-muted">登录以管理应用市场</p>
      </div>

      <form class="space-y-4" @submit.prevent="submit">
        <div>
          <label class="mb-1.5 block text-xs text-portal-muted">用户名</label>
          <div
            class="flex items-center gap-2 rounded-xl border border-portal-border bg-portal-surface2 px-3 transition focus-within:border-portal-mint/50"
          >
            <i class="fas fa-user text-xs text-portal-muted"></i>
            <input
              v-model="username"
              type="text"
              autocomplete="username"
              class="w-full bg-transparent py-2.5 text-sm text-portal-text outline-none placeholder-portal-muted"
              placeholder="用户名"
            />
          </div>
        </div>

        <div>
          <label class="mb-1.5 block text-xs text-portal-muted">密码</label>
          <div
            class="flex items-center gap-2 rounded-xl border border-portal-border bg-portal-surface2 px-3 transition focus-within:border-portal-mint/50"
          >
            <i class="fas fa-lock text-xs text-portal-muted"></i>
            <input
              v-model="password"
              type="password"
              autocomplete="current-password"
              class="w-full bg-transparent py-2.5 text-sm text-portal-text outline-none placeholder-portal-muted"
              placeholder="密码"
            />
          </div>
        </div>

        <div
          v-if="err"
          class="rounded-xl border border-portal-coral/40 bg-portal-coral/10 px-3 py-2 text-sm text-portal-coral"
        >
          {{ err }}
        </div>

        <button
          type="submit"
          :disabled="busy"
          class="flex w-full items-center justify-center gap-2 rounded-xl bg-portal-mint px-3 py-2.5 text-sm font-semibold text-portal-bg transition hover:bg-portal-mint2 disabled:opacity-50"
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

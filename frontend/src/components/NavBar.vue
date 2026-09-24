<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { auth } from '@/store/auth'

const props = defineProps<{ variant?: 'portal' | 'admin' }>()

const route = useRoute()
const isAdmin = computed(() => props.variant === 'admin')

const items = computed(() =>
  [
    { to: '/', label: '首页' },
    { to: '/appmarket', label: '应用图鉴' },
    ...(auth.user?.role === 'ADMIN' ? [{ to: '/admin', label: '管理后台' }] : [])
  ].filter(Boolean)
)

// 高亮判定：/appmarket/123 也要让「应用图鉴」保持点亮
function isActive(to: string): boolean {
  if (to === '/') return route.path === '/'
  return route.path === to || route.path.startsWith(to + '/')
}

// 导航项：未选中为「幽灵胶囊」，选中为实心块（门户=琥珀，后台=靛蓝）
function navClass(to: string): string {
  if (isActive(to)) {
    return isAdmin.value
      ? 'bg-admin-accent font-semibold text-white'
      : 'bg-portal-amber font-semibold text-portal-bg'
  }
  return isAdmin.value
    ? 'text-admin-muted hover:bg-admin-bg hover:text-admin-text'
    : 'text-portal-muted hover:bg-portal-surface2 hover:text-portal-text'
}

const initial = computed(() =>
  (auth.user?.displayName || auth.user?.username || '?').trim().slice(0, 1).toUpperCase()
)
</script>

<template>
  <header
    class="sticky top-0 z-50 border-b backdrop-blur"
    :class="isAdmin ? 'border-admin-border bg-admin-bg/90' : 'border-portal-border bg-portal-bg/85'"
  >
    <div class="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4">
      <router-link to="/" class="flex shrink-0 items-center gap-2">
        <span
          class="grid h-7 w-7 place-items-center rounded-lg text-[13px] font-bold"
          :class="isAdmin ? 'bg-admin-accent/10 text-admin-accent' : 'bg-portal-mint/15 text-portal-mint'"
          >Z</span
        >
        <span
          class="font-display text-base font-bold tracking-wide"
          :class="isAdmin ? 'text-admin-text' : 'text-portal-text'"
        >
          ZAZE<span class="font-medium" :class="isAdmin ? 'text-admin-muted' : 'text-portal-muted'">
            门户</span
          >
        </span>
      </router-link>

      <nav class="ml-2 hidden items-center gap-1 sm:flex">
        <router-link
          v-for="it in items"
          :key="it.to"
          :to="it.to"
          class="rounded-lg px-3 py-1.5 text-sm transition"
          :class="navClass(it.to)"
          >{{ it.label }}</router-link
        >
      </nav>

      <div class="ml-auto flex items-center gap-2">
        <template v-if="auth.user">
          <span
            v-if="auth.user.role === 'ADMIN'"
            class="hidden rounded-full border px-2.5 py-0.5 text-[11px] sm:inline-block"
            :class="
              isAdmin ? 'border-admin-accent/40 text-admin-accent' : 'border-portal-mint/40 text-portal-mint'
            "
            >管理员</span
          >

          <div
            class="flex items-center gap-2 rounded-full border py-0.5 pl-0.5 pr-3"
            :class="isAdmin ? 'border-admin-border bg-admin-surface' : 'border-portal-border bg-portal-surface'"
          >
            <span
              class="grid h-6 w-6 place-items-center rounded-full text-[11px] font-bold"
              :class="isAdmin ? 'bg-admin-accent/10 text-admin-accent' : 'bg-portal-mint/15 text-portal-mint'"
              >{{ initial }}</span
            >
            <span
              class="max-w-[7rem] truncate text-xs"
              :class="isAdmin ? 'text-admin-text' : 'text-portal-text'"
              >{{ auth.user.displayName || auth.user.username }}</span
            >
          </div>

          <button
            class="rounded-lg border px-2.5 py-1.5 text-xs transition"
            :class="
              isAdmin
                ? 'border-admin-border text-admin-muted hover:text-admin-text'
                : 'border-portal-border text-portal-muted hover:border-portal-border2 hover:text-portal-text'
            "
            @click="auth.logout()"
          >
            退出
          </button>
        </template>
      </div>
    </div>
  </header>
</template>

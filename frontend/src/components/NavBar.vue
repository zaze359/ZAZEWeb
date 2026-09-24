<script setup lang="ts">
import { auth } from '@/store/auth'

defineProps<{ variant?: 'portal' | 'admin' }>()
</script>

<template>
  <header
    class="sticky top-0 z-50 border-b backdrop-blur"
    :class="variant === 'admin' ? 'border-admin-border bg-admin-bg/85' : 'border-portal-border bg-portal-bg/80'"
  >
    <div class="mx-auto flex h-14 max-w-5xl items-center px-4">
      <router-link to="/" class="font-display text-lg font-bold tracking-wide">
        <span :class="variant === 'admin' ? 'text-admin-accent' : 'text-portal-neon'">ZAZE</span>
        <span :class="variant === 'admin' ? 'text-admin-text' : 'text-portal-text'"> 门户</span>
      </router-link>

      <nav class="ml-6 hidden gap-4 text-sm sm:flex">
        <router-link
          to="/"
          class="transition"
          :class="variant === 'admin' ? 'text-admin-muted hover:text-admin-text' : 'text-portal-muted hover:text-portal-text'"
          >首页</router-link
        >
        <router-link
          to="/appmarket"
          class="transition"
          :class="variant === 'admin' ? 'text-admin-muted hover:text-admin-text' : 'text-portal-muted hover:text-portal-text'"
          >应用图鉴</router-link
        >
        <router-link
          v-if="auth.user?.role === 'ADMIN'"
          to="/admin"
          class="transition"
          :class="variant === 'admin' ? 'text-admin-muted hover:text-admin-text' : 'text-portal-muted hover:text-portal-text'"
          >管理后台</router-link
        >
      </nav>

      <div class="ml-auto flex items-center gap-3">
        <template v-if="auth.user">
          <span class="text-sm" :class="variant === 'admin' ? 'text-admin-muted' : 'text-portal-muted'">
            {{ auth.user.displayName || auth.user.username }}
          </span>
          <span
            v-if="auth.user.role === 'ADMIN'"
            class="rounded-full border px-2 py-0.5 text-xs"
            :class="variant === 'admin' ? 'border-admin-accent/40 text-admin-accent' : 'border-portal-neon/40 text-portal-neon'"
            >管理员</span
          >
          <button
            class="rounded-lg border px-3 py-1 text-sm transition"
            :class="
              variant === 'admin'
                ? 'border-admin-border text-admin-muted hover:text-admin-text'
                : 'border-portal-border text-portal-muted hover:text-portal-text'
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

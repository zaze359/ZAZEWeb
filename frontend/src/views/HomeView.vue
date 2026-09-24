<script setup lang="ts">
import { auth } from '@/store/auth'
</script>

<template>
  <section class="relative mb-8 overflow-hidden rounded-3xl border border-portal-border bg-portal-surface p-8">
    <div class="pointer-events-none absolute -right-10 -top-10 h-48 w-48 rounded-full bg-portal-neon/20 blur-3xl"></div>
    <div class="pointer-events-none absolute -bottom-12 left-10 h-40 w-40 rounded-full bg-portal-neon2/20 blur-3xl"></div>

    <p class="text-sm text-portal-neon2">// ZAZE PERSONAL PORTAL</p>
    <h1 class="mt-2 font-display text-3xl font-bold sm:text-4xl">
      欢迎回来，<span class="text-portal-neon">{{ auth.user?.displayName || auth.user?.username || '旅人' }}</span>
    </h1>
    <p class="mt-2 max-w-xl text-portal-muted">
      这里收集了开源应用的下载图谱。挑一个，开启你的获取之旅。
    </p>
  </section>

  <div class="grid gap-4 sm:grid-cols-2">
    <router-link
      to="/appmarket"
      class="group rounded-2xl border border-portal-border bg-portal-surface p-5 transition hover:-translate-y-1 hover:border-portal-neon2/60 hover:shadow-[0_0_22px_rgba(44,232,255,0.22)]"
    >
      <div class="flex items-center gap-3">
        <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-portal-neon2/15 text-portal-neon2">
          <i class="fas fa-store"></i>
        </span>
        <h3 class="font-display text-lg font-semibold text-portal-text">应用图鉴</h3>
      </div>
      <p class="mt-3 text-sm text-portal-muted">
        浏览收集到的应用，查看每个应用下的多个版本，以及各版本不同来源的下载地址。
      </p>
      <span class="mt-4 inline-block text-sm text-portal-neon2 transition group-hover:translate-x-1">进入图鉴 →</span>
    </router-link>

    <template v-if="auth.user?.role === 'ADMIN'">
          <router-link
            to="/admin"
            class="group rounded-2xl border border-portal-border bg-portal-surface p-5 transition hover:-translate-y-1 hover:border-portal-neon/60 hover:shadow-[0_0_22px_rgba(255,78,205,0.25)]"
          >
        <div class="flex items-center gap-3">
          <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-portal-neon/15 text-portal-neon">
            <i class="fas fa-tools"></i>
          </span>
          <h3 class="font-display text-lg font-semibold text-portal-text">管理后台</h3>
        </div>
        <p class="mt-3 text-sm text-portal-muted">
          触发自动采集（GitHub Releases 等公开渠道），并管理应用、版本与下载地址。
        </p>
        <span class="mt-4 inline-block text-sm text-portal-neon transition group-hover:translate-x-1">进入后台 →</span>
      </router-link>
    </template>
    <template v-else>
      <div class="rounded-2xl border border-portal-border bg-portal-surface p-5">
        <div class="flex items-center gap-3">
          <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-portal-surface2 text-portal-muted">
            <i class="fas fa-user"></i>
          </span>
          <h3 class="font-display text-lg font-semibold text-portal-text">我的账号</h3>
        </div>
        <ul class="mt-3 space-y-1 text-sm text-portal-muted">
          <li>用户名：{{ auth.user?.username }}</li>
          <li>角色：普通用户</li>
        </ul>
        <p class="mt-3 text-xs text-portal-muted">需要管理后台权限请联系管理员。</p>
      </div>
    </template>
  </div>
</template>

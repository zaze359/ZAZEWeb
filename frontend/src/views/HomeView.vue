<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { auth } from '@/store/auth'
import { api } from '@/api/client'
import type { AppVo } from '@/types'
import AppCard from '@/components/portal/AppCard.vue'

const name = computed(() => auth.user?.displayName || auth.user?.username || '旅人')
const isAdmin = computed(() => auth.user?.role === 'ADMIN')

// 首页「最近收录」预览：直接拉最新 6 个，复用 AppCard，不新增接口
const recent = ref<AppVo[]>([])
const loadingRecent = ref(false)
onMounted(async () => {
  loadingRecent.value = true
  try {
    recent.value = (await api.apps()).slice(0, 6)
  } catch {
    recent.value = []
  } finally {
    loadingRecent.value = false
  }
})
</script>

<template>
  <section
    class="relative mb-5 overflow-hidden rounded-3xl border border-portal-border bg-portal-surface p-7 sm:p-9"
  >
    <!-- 底纹：极淡点阵 + 单侧薄荷柔光，替代原先的多色霓虹光晕 -->
    <div
      class="pointer-events-none absolute inset-0 opacity-60"
      style="
        background-image: radial-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px);
        background-size: 18px 18px;
      "
    ></div>
    <div
      class="pointer-events-none absolute -right-20 -top-28 h-64 w-64 rounded-full bg-portal-mint/10 blur-3xl"
    ></div>

    <div class="relative">
      <span
        class="inline-flex items-center gap-2 rounded-full border border-portal-mint/30 bg-portal-mint/10 px-3 py-1 text-xs text-portal-mint"
      >
        <i class="fas fa-circle text-[6px]"></i> ZAZE PERSONAL PORTAL
      </span>

      <h1 class="mt-4 font-display text-3xl font-bold leading-tight sm:text-4xl">
        欢迎回来，<span class="text-portal-mint">{{ name }}</span>
      </h1>
      <p class="mt-3 max-w-xl text-sm leading-relaxed text-portal-muted">
        这里收集了开源应用的下载图谱。挑一个，开启你的获取之旅。
      </p>

      <div class="mt-6 flex flex-wrap items-center gap-2.5">
        <router-link
          to="/appmarket"
          class="inline-flex items-center gap-2 rounded-xl bg-portal-mint px-4 py-2 text-sm font-semibold text-portal-bg transition hover:bg-portal-mint2"
        >
          <i class="fas fa-compass"></i> 进入图鉴
        </router-link>
        <router-link
          v-if="isAdmin"
          to="/admin"
          class="inline-flex items-center gap-2 rounded-xl border border-portal-border bg-portal-surface2 px-4 py-2 text-sm font-medium text-portal-text transition hover:border-portal-border2"
        >
          <i class="fas fa-sliders"></i> 管理后台
        </router-link>
      </div>
    </div>
  </section>

  <div class="grid gap-4 sm:grid-cols-2">
    <router-link
      to="/appmarket"
      class="group rounded-2xl border border-portal-border bg-portal-surface p-5 transition duration-200 hover:-translate-y-0.5 hover:border-portal-mint/45 hover:shadow-tile"
    >
      <div class="flex items-center gap-3">
        <span class="grid h-10 w-10 place-items-center rounded-xl bg-portal-mint/15 text-portal-mint">
          <i class="fas fa-layer-group"></i>
        </span>
        <h3 class="font-display text-base font-semibold text-portal-text">应用图鉴</h3>
      </div>
      <p class="mt-3 text-sm leading-relaxed text-portal-muted">
        浏览收集到的应用，查看每个应用下的多个版本，以及各版本不同来源的下载地址。
      </p>
      <span class="mt-4 inline-flex items-center gap-1.5 text-sm text-portal-mint transition-all group-hover:gap-2.5">
        进入图鉴 <i class="fas fa-arrow-right text-xs"></i>
      </span>
    </router-link>

    <template v-if="isAdmin">
      <router-link
        to="/admin"
        class="group rounded-2xl border border-portal-border bg-portal-surface p-5 transition duration-200 hover:-translate-y-0.5 hover:border-portal-sky/45 hover:shadow-tile"
      >
        <div class="flex items-center gap-3">
          <span class="grid h-10 w-10 place-items-center rounded-xl bg-portal-sky/15 text-portal-sky">
            <i class="fas fa-sliders"></i>
          </span>
          <h3 class="font-display text-base font-semibold text-portal-text">管理后台</h3>
        </div>
        <p class="mt-3 text-sm leading-relaxed text-portal-muted">
          触发自动采集（GitHub Releases 等公开渠道），并管理应用、版本与下载地址。
        </p>
        <span class="mt-4 inline-flex items-center gap-1.5 text-sm text-portal-sky transition-all group-hover:gap-2.5">
          进入后台 <i class="fas fa-arrow-right text-xs"></i>
        </span>
      </router-link>
    </template>

    <template v-else>
      <div class="rounded-2xl border border-portal-border bg-portal-surface p-5">
        <div class="flex items-center gap-3">
          <span class="grid h-10 w-10 place-items-center rounded-xl bg-portal-surface2 text-portal-muted">
            <i class="fas fa-user"></i>
          </span>
          <h3 class="font-display text-base font-semibold text-portal-text">我的账号</h3>
        </div>
        <ul class="mt-3 space-y-1 text-sm text-portal-muted">
          <li>用户名：<span class="text-portal-text/80">{{ auth.user?.username }}</span></li>
          <li>角色：普通用户</li>
        </ul>
        <p class="mt-3 text-xs text-portal-muted">需要管理后台权限请联系管理员。</p>
      </div>
    </template>
  </div>

  <section class="mt-9">
    <div class="mb-4 flex items-end justify-between gap-3">
      <div>
        <span class="inline-flex items-center gap-2 text-xs tracking-wide text-portal-mint">
          <i class="fas fa-circle text-[6px]"></i> LATEST
        </span>
        <h2 class="mt-1.5 font-display text-lg font-semibold text-portal-text">最近收录</h2>
      </div>
      <router-link to="/appmarket" class="text-sm text-portal-mint transition hover:text-portal-mint2">
        查看全部 <i class="fas fa-arrow-right text-xs"></i>
      </router-link>
    </div>

    <div v-if="loadingRecent" class="py-10 text-center text-sm text-portal-muted">加载中…</div>
    <div
      v-else-if="!recent.length"
      class="rounded-2xl border border-dashed border-portal-border py-10 text-center text-sm text-portal-muted"
    >
      暂无数据。
    </div>
    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <AppCard v-for="a in recent" :key="a.id" :app="a" class="animate-fade-up" />
    </div>
  </section>
</template>

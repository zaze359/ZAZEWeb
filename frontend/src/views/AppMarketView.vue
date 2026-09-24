<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '@/api/client'
import type { AppVo } from '@/types'
import { deriveHeat } from '@/utils/appMeta'
import AppCard from '@/components/portal/AppCard.vue'
import SortBar, { type SortKey } from '@/components/portal/SortBar.vue'

const apps = ref<AppVo[]>([])
const keyword = ref('')
const loading = ref(false)
const sort = ref<SortKey>('latest')
let timer: ReturnType<typeof setTimeout> | undefined

async function load() {
  loading.value = true
  try {
    apps.value = await api.apps(keyword.value.trim() || undefined)
  } finally {
    loading.value = false
  }
}

const sorted = computed(() => {
  const list = [...apps.value]
  if (sort.value === 'hot') list.sort((a, b) => deriveHeat(b) - deriveHeat(a))
  else if (sort.value === 'name') list.sort((a, b) => a.name.localeCompare(b.name, 'zh'))
  return list
})

function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(load, 250)
}

onMounted(load)
</script>

<template>
  <div>
    <div class="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <span class="inline-flex items-center gap-2 text-xs tracking-wide text-portal-mint">
          <i class="fas fa-circle text-[6px]"></i> APP CODEDEX
        </span>
        <h1 class="mt-2 font-display text-2xl font-bold text-portal-text">应用图鉴</h1>
      </div>

      <div
        class="flex items-center gap-2 rounded-full border border-portal-border bg-portal-surface px-4 py-2 transition focus-within:border-portal-mint/50"
      >
        <i class="fas fa-search text-xs text-portal-muted"></i>
        <input
          v-model="keyword"
          type="search"
          @input="onSearch"
          placeholder="搜索名称 / 开发者…"
          class="w-44 bg-transparent text-sm text-portal-text outline-none placeholder-portal-muted sm:w-60"
        />
      </div>
    </div>

    <div class="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-portal-border pb-4">
      <span class="text-sm text-portal-muted">
        共 <span class="font-medium text-portal-text">{{ sorted.length }}</span> 个应用
      </span>
      <SortBar v-model="sort" />
    </div>

    <div v-if="loading" class="py-16 text-center text-sm text-portal-muted">加载中…</div>
    <div
      v-else-if="!sorted.length"
      class="rounded-2xl border border-dashed border-portal-border py-16 text-center text-sm text-portal-muted"
    >
      暂无应用数据。
    </div>
    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <AppCard v-for="app in sorted" :key="app.id" :app="app" class="animate-fade-up" />
    </div>
  </div>
</template>

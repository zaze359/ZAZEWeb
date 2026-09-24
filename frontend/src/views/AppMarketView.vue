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
    <div class="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="text-sm text-portal-neon2">// APP CODEDEX</p>
        <h1 class="font-display text-2xl font-bold text-portal-text">应用图鉴</h1>
      </div>
      <input
        type="search"
        v-model="keyword"
        @input="onSearch"
        placeholder="搜索名称 / 开发者…"
        class="w-64 rounded-xl border border-portal-border bg-portal-surface2 px-3 py-2 text-sm text-portal-text outline-none placeholder-portal-muted focus:border-portal-neon/60"
      />
    </div>

    <div class="mb-4 flex items-center justify-between">
      <span class="text-sm text-portal-muted">共 {{ sorted.length }} 个应用</span>
      <SortBar v-model="sort" />
    </div>

    <div v-if="loading" class="py-16 text-center text-portal-muted">加载中…</div>
    <div v-else-if="!sorted.length" class="py-16 text-center text-portal-muted">暂无应用数据。</div>
    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <AppCard v-for="app in sorted" :key="app.id" :app="app" class="animate-fade-up" />
    </div>
  </div>
</template>

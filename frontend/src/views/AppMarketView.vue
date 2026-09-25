<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api } from '@/api/client'
import type { AppVo } from '@/types'
import { deriveHeat } from '@/utils/appMeta'
import AppCard from '@/components/portal/AppCard.vue'
import SortBar, { type SortKey } from '@/components/portal/SortBar.vue'
import CategoryRail, { type CatItem } from '@/components/portal/CategoryRail.vue'

const apps = ref<AppVo[]>([])
const keyword = ref('')
const loading = ref(false)
const sort = ref<SortKey>('latest')
const activeCat = ref('全部')
let timer: ReturnType<typeof setTimeout> | undefined

async function load() {
  loading.value = true
  try {
    apps.value = await api.apps(keyword.value.trim() || undefined)
    // 关键词变化时结果集变了，分类筛选回「全部」避免选中项落到空结果
    activeCat.value = '全部'
  } finally {
    loading.value = false
  }
}

// 从已加载应用的 category 派生分类与计数（参考站左侧 rail 的同款组织方式）
const categories = computed<CatItem[]>(() => {
  const map = new Map<string, number>()
  for (const a of apps.value) {
    const c = a.category || '未分类'
    map.set(c, (map.get(c) ?? 0) + 1)
  }
  const list = [...map.entries()]
    .map(([label, count]) => ({ label, count }))
    .sort((x, y) => y.count - x.count)
  return [{ label: '全部', count: apps.value.length }, ...list]
})

// 先按分类客户端过滤，再按排序键排
const sorted = computed(() => {
  let list = apps.value
  if (activeCat.value !== '全部') {
    list = list.filter((a) => (a.category || '未分类') === activeCat.value)
  }
  list = [...list]
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

    <div class="lg:flex lg:gap-6">
      <!-- 左：分类筛选 rail（移动端变为顶部横滑） -->
      <aside class="mb-5 lg:mb-0 lg:w-48 lg:shrink-0">
        <CategoryRail :items="categories" v-model="activeCat" />
      </aside>

      <div class="min-w-0 flex-1">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-portal-border pb-4">
          <span class="text-sm text-portal-muted">
            共 <span class="font-medium text-portal-text">{{ sorted.length }}</span> 个应用
            <span v-if="activeCat !== '全部'" class="text-portal-mint"> · {{ activeCat }}</span>
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
    </div>
  </div>
</template>

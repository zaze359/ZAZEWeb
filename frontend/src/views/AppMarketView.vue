<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { api } from '@/api/client'
import type { AppVo, CategoryCount } from '@/types'
import AppCard from '@/components/portal/AppCard.vue'
import SortBar, { type SortKey } from '@/components/portal/SortBar.vue'
import CategoryRail, { type CatItem } from '@/components/portal/CategoryRail.vue'

const apps = ref<AppVo[]>([]) // 滚动累积分页结果
const total = ref(0)
const page = ref(1)
const PAGE_SIZE = 9
const keyword = ref('')
const loading = ref(false)
const loadingMore = ref(false)
const sort = ref<SortKey>('latest')
const activeCat = ref('全部')
const catCounts = ref<CategoryCount[]>([])
let timer: ReturnType<typeof setTimeout> | undefined

// 分类 rail：从独立接口拉计数，前端补「全部」（count = 各分类之和）
const categories = computed<CatItem[]>(() => {
  const list = catCounts.value.map((c) => ({ label: c.label, count: c.count }))
  const all = catCounts.value.reduce((s, c) => s + c.count, 0)
  return [{ label: '全部', count: all }, ...list]
})

async function fetchPage(p: number) {
  return api.apps({
    keyword: keyword.value.trim() || undefined,
    category: activeCat.value !== '全部' ? activeCat.value : undefined,
    sort: sort.value,
    page: p,
    size: PAGE_SIZE
  })
}

// 首屏 / 筛选变化：重置到第 1 页（服务端分页，过滤也在后端做）
async function loadInitial() {
  loading.value = true
  page.value = 1
  try {
    const res = await fetchPage(1)
    apps.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

const hasMore = computed(() => apps.value.length < total.value)

// 滚动到底：拉下一页并追加（门户主打「炫酷」的无限流手感）
function loadMore() {
  if (loadingMore.value || loading.value || !hasMore.value) return
  loadingMore.value = true
  const next = page.value + 1
  fetchPage(next)
    .then((res) => {
      apps.value = [...apps.value, ...res.list]
      total.value = res.total
      page.value = next
    })
    .catch(() => {})
    .finally(() => {
      loadingMore.value = false
    })
}

function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(loadInitial, 250)
}

// 分类 / 排序变化 → 重新拉第 1 页
watch([activeCat, sort], loadInitial)

// —— 滚动观察哨（IntersectionObserver）——
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let observedEl: Element | null = null

function attachObserver() {
  if (!observer || !sentinel.value || observedEl === sentinel.value) return
  if (observedEl) observer.unobserve(observedEl)
  observer.observe(sentinel.value)
  observedEl = sentinel.value
}

onMounted(async () => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) loadMore()
    },
    { rootMargin: '120px' }
  )
  try {
    catCounts.value = await api.appCategories()
  } catch {
    catCounts.value = []
  }
  await loadInitial()
})

onUnmounted(() => {
  observer?.disconnect()
  observer = null
  observedEl = null
})

// 列表刷新后哨兵元素重建，重新挂载观察哨
watch(loading, () => {
  if (!loading.value) nextTick(attachObserver)
})
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
            共 <span class="font-medium text-portal-text">{{ total }}</span> 个应用
            <span v-if="activeCat !== '全部'" class="text-portal-mint"> · {{ activeCat }}</span>
          </span>
          <SortBar v-model="sort" />
        </div>

        <div v-if="loading" class="py-16 text-center text-sm text-portal-muted">加载中…</div>
        <div
          v-else-if="!total"
          class="rounded-2xl border border-dashed border-portal-border py-16 text-center text-sm text-portal-muted"
        >
          暂无应用数据。
        </div>
        <div v-else>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <AppCard v-for="app in apps" :key="app.id" :app="app" class="animate-fade-up" />
          </div>
          <div v-if="hasMore" ref="sentinel" class="flex justify-center py-8">
            <div v-if="loadingMore" class="flex items-center gap-2 text-xs text-portal-muted">
              <span
                class="h-4 w-4 animate-spin rounded-full border-2 border-portal-mint/40 border-t-portal-mint"
              ></span>
              正在加载更多…
            </div>
            <span v-else class="text-xs text-portal-muted/70">下滑加载更多</span>
          </div>
          <div v-else class="py-8 text-center text-xs text-portal-muted/70">
            已经到底啦 · 共 {{ total }} 个应用
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

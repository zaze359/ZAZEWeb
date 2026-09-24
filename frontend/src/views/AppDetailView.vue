<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '@/api/client'
import type { AppVo } from '@/types'
import AppIcon from '@/components/portal/AppIcon.vue'
import CategoryChip from '@/components/portal/CategoryChip.vue'

const props = defineProps<{ id: string }>()
const app = ref<AppVo | null>(null)
const loading = ref(true)

function fmtDate(ms?: number): string {
  if (!ms) return ''
  const d = new Date(ms)
  return `${d.getFullYear()}-${('0' + (d.getMonth() + 1)).slice(-2)}-${('0' + d.getDate()).slice(-2)}`
}

onMounted(async () => {
  try {
    app.value = await api.appDetail(props.id)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <router-link to="/appmarket" class="text-sm text-portal-muted transition hover:text-portal-text">
      ← 返回图鉴
    </router-link>

    <div v-if="loading" class="py-16 text-center text-portal-muted">加载中…</div>
    <div v-else-if="!app" class="py-16 text-center text-portal-neon">未找到该应用。</div>

    <div v-else>
      <section class="mt-4 flex items-center gap-4 rounded-3xl border border-portal-border bg-portal-surface p-6">
        <AppIcon :src="app.iconUrl" :name="app.name" :size="72" />
        <div class="min-w-0">
          <div class="flex items-center gap-2">
            <h1 class="font-display text-2xl font-bold text-portal-text">{{ app.name }}</h1>
            <CategoryChip :label="app.category" />
          </div>
          <p v-if="app.developer" class="mt-1 text-sm text-portal-muted">{{ app.developer }}</p>
          <p v-if="app.summary" class="mt-2 max-w-xl text-sm text-portal-text/80">{{ app.summary }}</p>
          <a
            v-if="app.officialUrl"
            :href="app.officialUrl"
            target="_blank"
            rel="noopener"
            class="mt-3 inline-block text-sm text-portal-neon2 hover:underline"
            >官方网站 ↗</a
          >
        </div>
      </section>

      <h2 class="mb-3 mt-8 font-display text-lg font-semibold text-portal-text">版本与下载源</h2>
      <div v-if="!app.versions?.length" class="text-portal-muted">暂无版本。</div>

      <div
        v-for="v in app.versions"
        :key="v.id"
        class="mb-4 rounded-2xl border border-portal-border bg-portal-surface p-5"
      >
        <div class="flex items-center justify-between">
          <span class="font-display font-semibold text-portal-text">v{{ v.versionName }}</span>
          <span class="text-xs text-portal-muted">
            {{ fmtDate(v.releaseDate) }}
            <template v-if="v.sizeMb"> · {{ v.sizeMb }} MB</template>
            · {{ v.sourceCount || 0 }} 来源
          </span>
        </div>
        <p v-if="v.changelog" class="mt-2 text-sm text-portal-muted">{{ v.changelog }}</p>
        <div class="mt-3 flex flex-wrap gap-2">
          <a
            v-for="s in v.sources"
            :key="s.id"
            :href="s.downloadUrl"
            target="_blank"
            rel="noopener"
            class="inline-flex items-center gap-1.5 rounded-xl border border-portal-neon/40 bg-portal-neon/10 px-3 py-1.5 text-sm text-portal-neon transition hover:bg-portal-neon/20"
          >
            <i class="fas fa-download"></i>
            {{ s.sourceName || s.sourceType || '下载' }}
            <template v-if="s.region"> · {{ s.region }}</template>
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

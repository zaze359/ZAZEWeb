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
    <router-link
      to="/appmarket"
      class="inline-flex items-center gap-2 rounded-lg border border-portal-border bg-portal-surface px-3 py-1.5 text-sm text-portal-muted transition hover:border-portal-border2 hover:text-portal-text"
    >
      <i class="fas fa-arrow-left text-xs"></i> 返回图鉴
    </router-link>

    <div v-if="loading" class="py-16 text-center text-sm text-portal-muted">加载中…</div>
    <div v-else-if="!app" class="py-16 text-center text-sm text-portal-coral">未找到该应用。</div>

    <div v-else>
      <section
        class="mt-4 flex items-start gap-4 rounded-3xl border border-portal-border bg-portal-surface p-6"
      >
        <AppIcon :src="app.iconUrl" :name="app.name" :size="72" />
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <h1 class="font-display text-2xl font-bold text-portal-text">{{ app.name }}</h1>
            <CategoryChip :label="app.category" />
          </div>
          <p v-if="app.developer" class="mt-1.5 text-sm text-portal-muted">{{ app.developer }}</p>
          <p v-if="app.summary" class="mt-2.5 max-w-xl text-sm leading-relaxed text-portal-text/80">
            {{ app.summary }}
          </p>
          <a
            v-if="app.officialUrl"
            :href="app.officialUrl"
            target="_blank"
            rel="noopener"
            class="mt-3 inline-flex items-center gap-1.5 text-sm text-portal-mint transition hover:text-portal-mint2"
            >官方网站 <i class="fas fa-arrow-up-right-from-square text-xs"></i></a
          >
        </div>
      </section>

      <h2 class="mb-3 mt-8 font-display text-lg font-semibold text-portal-text">版本与下载源</h2>
      <div v-if="!app.versions?.length" class="text-sm text-portal-muted">暂无版本。</div>

      <div
        v-for="v in app.versions"
        :key="v.id"
        class="mb-4 rounded-2xl border border-portal-border bg-portal-surface p-5 transition hover:border-portal-border2"
      >
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="font-display font-semibold text-portal-text">v{{ v.versionName }}</span>
          <span class="text-xs text-portal-muted">
            {{ fmtDate(v.releaseDate) }}
            <template v-if="v.sizeMb"> · {{ v.sizeMb }} MB</template>
            · {{ v.sourceCount || 0 }} 来源
          </span>
        </div>
        <p v-if="v.changelog" class="mt-2 text-sm leading-relaxed text-portal-muted">
          {{ v.changelog }}
        </p>
        <div class="mt-3.5 flex flex-wrap gap-2">
          <a
            v-for="s in v.sources"
            :key="s.id"
            :href="s.downloadUrl"
            target="_blank"
            rel="noopener"
            class="inline-flex items-center gap-1.5 rounded-xl border border-portal-mint/35 bg-portal-mint/10 px-3 py-1.5 text-sm text-portal-mint transition hover:border-portal-mint/60 hover:bg-portal-mint/20"
          >
            <i class="fas fa-download text-xs"></i>
            {{ s.sourceName || s.sourceType || '下载' }}
            <span v-if="s.region" class="text-portal-mint/70">· {{ s.region }}</span>
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

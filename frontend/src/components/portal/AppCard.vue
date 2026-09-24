<script setup lang="ts">
import { computed } from 'vue'
import type { AppVo } from '@/types'
import { deriveGrade, deriveHeat } from '@/utils/appMeta'
import AppIcon from './AppIcon.vue'
import GradeBadge from './GradeBadge.vue'
import CategoryChip from './CategoryChip.vue'
import HeatBar from './HeatBar.vue'

const props = defineProps<{ app: AppVo }>()
const grade = computed(() => deriveGrade(props.app))
const heat = computed(() => deriveHeat(props.app))
</script>

<template>
  <router-link
    :to="`/appmarket/${app.id}`"
    class="group block rounded-2xl border border-portal-border bg-portal-surface p-4 transition hover:-translate-y-1 hover:border-portal-neon/60 hover:shadow-[0_0_22px_rgba(255,78,205,0.25)]"
  >
    <div class="flex items-start gap-3">
      <AppIcon :src="app.iconUrl" :name="app.name" :size="56" />
      <div class="min-w-0 flex-1">
        <div class="flex items-center justify-between gap-2">
          <h3 class="truncate font-display font-semibold text-portal-text">{{ app.name }}</h3>
          <GradeBadge :grade="grade" size="sm" />
        </div>
        <div class="mt-1.5">
          <CategoryChip :label="app.category" />
        </div>
        <p class="mt-1.5 truncate text-xs text-portal-muted">
          {{ app.developer || '未知开发者' }}
        </p>
      </div>
    </div>

    <div class="mt-3 flex items-center justify-between text-xs text-portal-muted">
      <span>{{ app.versionCount || 0 }} 个版本</span>
      <span>热度 {{ heat }}</span>
    </div>
    <div class="mt-1.5">
      <HeatBar :value="heat" />
    </div>
  </router-link>
</template>

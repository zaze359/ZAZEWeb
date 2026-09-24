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
    class="group flex flex-col rounded-2xl border border-portal-border bg-portal-surface p-4 transition duration-200 hover:-translate-y-0.5 hover:border-portal-mint/45 hover:shadow-tile"
  >
    <div class="flex items-start gap-3">
      <AppIcon :src="app.iconUrl" :name="app.name" :size="56" />
      <div class="min-w-0 flex-1">
        <div class="flex items-start justify-between gap-2">
          <h3 class="truncate font-display text-[15px] font-semibold text-portal-text">
            {{ app.name }}
          </h3>
          <GradeBadge :grade="grade" size="sm" />
        </div>
        <div class="mt-2">
          <CategoryChip :label="app.category" />
        </div>
        <p class="mt-2 truncate text-xs text-portal-muted">{{ app.developer || '未知开发者' }}</p>
      </div>
    </div>

    <div class="mt-auto pt-4">
      <div class="flex items-center justify-between text-xs text-portal-muted">
        <span>{{ app.versionCount || 0 }} 个版本</span>
        <span>热度 <span class="font-medium text-portal-text/80">{{ heat }}</span></span>
      </div>
      <div class="mt-2">
        <HeatBar :value="heat" />
      </div>
    </div>
  </router-link>
</template>

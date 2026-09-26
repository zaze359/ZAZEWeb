<script setup lang="ts">
import { computed } from 'vue'
import type { AppVo } from '@/types'
import { deriveGrade, deriveHeat, GRADE_COLOR } from '@/utils/appMeta'
import AppIcon from './AppIcon.vue'
import GradeBadge from './GradeBadge.vue'
import CategoryChip from './CategoryChip.vue'
import HeatBar from './HeatBar.vue'

const props = defineProps<{ app: AppVo }>()
const grade = computed(() => deriveGrade(props.app))
const heat = computed(() => deriveHeat(props.app))
const gradeColor = computed(() => GRADE_COLOR[grade.value])
// 无真封面数据时，用等级色低透渐变做「封面带」占位，读起来像图鉴站大图卡却不造假数据
const coverStyle = computed(() => ({
  background: `linear-gradient(135deg, ${gradeColor.value}24 0%, ${gradeColor.value}0d 55%, transparent 100%)`
}))
const latestVersion = computed(() => props.app.versions?.[0]?.versionName)
</script>

<template>
  <router-link
    :to="`/appmarket/${app.id}`"
    class="group flex flex-col overflow-hidden rounded-2xl border border-portal-border bg-portal-surface transition duration-200 hover:-translate-y-0.5 hover:border-portal-mint/45 hover:shadow-tile"
  >
    <!-- 顶部渐变封面带：等级色低透 + 极淡点阵 + 居中大图标（无真封面时的占位视觉） -->
    <div class="relative h-24 overflow-hidden" :style="coverStyle">
      <div
        class="pointer-events-none absolute inset-0 opacity-50"
        style="
          background-image: radial-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px);
          background-size: 16px 16px;
        "
      ></div>
      <div class="absolute inset-0 flex items-center justify-center">
        <AppIcon
          :src="app.iconUrl"
          :name="app.name"
          :size="60"
          class="rounded-2xl shadow-tile"
        />
      </div>
      <GradeBadge :grade="grade" size="sm" class="absolute right-3 top-3" />
    </div>

    <div class="flex flex-1 flex-col p-4">
      <h3 class="truncate font-display text-[15px] font-semibold text-portal-text">
        {{ app.name }}
      </h3>

      <div class="mt-2 flex flex-wrap items-center gap-2">
        <CategoryChip :label="app.category" />
        <span v-if="latestVersion" class="text-[11px] text-portal-muted">
          v{{ latestVersion }}
        </span>
      </div>

      <p
        v-if="app.summary"
        class="mt-2 line-clamp-2 text-xs leading-relaxed text-portal-muted"
      >
        {{ app.summary }}
      </p>
      <p v-else class="mt-2 truncate text-xs text-portal-muted">
        {{ app.developer || '未知开发者' }}
      </p>

      <div class="mt-auto pt-4">
        <div class="flex items-center justify-between text-xs text-portal-muted">
          <span>{{ app.versionCount || 0 }} 个版本</span>
          <span>热度 <span class="font-medium text-portal-text/80">{{ heat }}</span></span>
        </div>
        <div class="mt-2">
          <HeatBar :value="heat" />
        </div>
      </div>
    </div>
  </router-link>
</template>

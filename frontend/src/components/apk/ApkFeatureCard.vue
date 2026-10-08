<script setup lang="ts">
import { computed } from 'vue'
import type { ApkAnalysis } from '@/utils/apk'

const props = defineProps<{ analysis: ApkAnalysis }>()
const features = computed(() => props.analysis.features.items)
const screens = computed(() => props.analysis.screens)

function yn(v: boolean | undefined): string {
  return v === true ? '是' : v === false ? '否' : '未声明'
}
</script>

<template>
  <section class="rounded-xl border border-admin-border bg-admin-surface p-4">
    <div class="mb-3 flex items-center gap-2">
      <i class="fas fa-microchip text-admin-accent"></i>
      <h3 class="text-sm font-semibold text-admin-text">硬件特性与屏幕</h3>
    </div>

    <div class="mb-3">
      <div class="mb-1 text-xs font-medium text-admin-muted">屏幕支持</div>
      <div class="flex flex-wrap gap-1.5 text-[11px]">
        <span class="rounded border border-admin-border px-1.5 py-0.5 text-admin-text"
          >任意密度 {{ yn(screens.supportsAnyDensity) }}</span
        >
        <span class="rounded border border-admin-border px-1.5 py-0.5 text-admin-text"
          >OpenGL 纹理 {{ yn(screens.supportsGlTextures) }}</span
        >
      </div>
      <div v-if="screens.compatibleScreens.length" class="mt-1.5 text-[11px] text-admin-muted">
        兼容屏幕：<span v-for="(c, i) in screens.compatibleScreens" :key="i" class="mr-1.5"
          >{{ c.screenSize || '-' }} / {{ c.screenDensity || '-' }}</span
        >
      </div>
      <div v-else class="mt-1.5 text-[11px] text-admin-muted">未声明具体兼容屏幕</div>
    </div>

    <div class="text-xs font-medium text-admin-muted">硬件特性（{{ features.length }}）</div>
    <ul v-if="features.length" class="mt-1 space-y-1 text-xs">
      <li v-for="f in features" :key="f.name" class="flex items-center justify-between gap-2">
        <code class="truncate text-admin-text">{{ f.name }}</code>
        <span
          class="shrink-0 rounded px-1.5 py-0.5 text-[10px]"
          :class="f.required ? 'bg-red-50 text-red-600' : 'bg-gray-100 text-gray-500'"
          >{{ f.required ? '必需' : '可选' }}</span
        >
      </li>
    </ul>
    <p v-else class="mt-1 text-[11px] text-admin-muted">未声明硬件特性</p>
  </section>
</template>

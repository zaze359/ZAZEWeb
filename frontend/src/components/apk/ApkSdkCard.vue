<script setup lang="ts">
import { computed } from 'vue'
import type { ApkAnalysis } from '@/utils/apk'

const props = defineProps<{ analysis: ApkAnalysis }>()
const sdk = computed(() => props.analysis.sdk)

function row(label: string, value?: number, android?: string) {
  return { label, value: value != null ? String(value) : '未声明', android: android || '' }
}
const rows = computed(() => [
  row('最低 SDK (minSdk)', sdk.value.minSdk, sdk.value.minAndroid),
  row('目标 SDK (targetSdk)', sdk.value.targetSdk, sdk.value.targetAndroid),
  row('最高 SDK (maxSdk)', sdk.value.maxSdk),
  row('编译 SDK (compileSdk)', sdk.value.compileSdk)
])
</script>

<template>
  <section class="rounded-xl border border-admin-border bg-admin-surface p-4">
    <div class="mb-3 flex items-center gap-2">
      <i class="fas fa-layer-group text-admin-accent"></i>
      <h3 class="text-sm font-semibold text-admin-text">SDK 与兼容性</h3>
    </div>
    <div class="space-y-2 text-xs">
      <div v-for="r in rows" :key="r.label" class="flex items-center justify-between gap-2">
        <span class="text-admin-muted">{{ r.label }}</span>
        <span class="text-right">
          <span class="font-medium text-admin-text">{{ r.value }}</span>
          <span v-if="r.android" class="ml-1 text-admin-muted">{{ r.android }}</span>
        </span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ApkAnalysis } from '@/utils/apk'
import ApkBasicCard from './ApkBasicCard.vue'
import ApkSdkCard from './ApkSdkCard.vue'
import ApkPermissionCard from './ApkPermissionCard.vue'
import ApkFeatureCard from './ApkFeatureCard.vue'
import ApkFileBreakdownCard from './ApkFileBreakdownCard.vue'
import ApkSignatureCard from './ApkSignatureCard.vue'
import ApkRawManifest from './ApkRawManifest.vue'

const props = defineProps<{ analysis: ApkAnalysis; variant?: 'compact' | 'full' }>()
const variant = props.variant || 'full'
</script>

<template>
  <div class="space-y-4">
    <!-- 降级 / 告警区（两形态都展示） -->
    <div
      v-if="analysis.warnings.length"
      class="rounded-xl border border-yellow-300 bg-yellow-50 p-3 text-xs text-yellow-700"
    >
      <div class="mb-1 font-medium"><i class="fas fa-exclamation-triangle"></i> 分析提示 / 降级说明</div>
      <ul class="list-disc space-y-0.5 pl-5">
        <li v-for="(w, i) in analysis.warnings" :key="i">{{ w }}</li>
      </ul>
    </div>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <ApkBasicCard :analysis="analysis" />
      <ApkSdkCard :analysis="analysis" />
      <ApkPermissionCard :analysis="analysis" :variant="variant" />
      <ApkFileBreakdownCard :analysis="analysis" :variant="variant" />
      <ApkSignatureCard :analysis="analysis" />
      <ApkFeatureCard v-if="variant === 'full'" :analysis="analysis" />
    </div>

    <ApkRawManifest v-if="variant === 'full'" :manifest="analysis.raw.manifest" />
  </div>
</template>

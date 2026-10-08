<script setup lang="ts">
import { computed } from 'vue'
import type { ApkAnalysis } from '@/utils/apk'
import { formatBytes } from '@/utils/apk'

const props = defineProps<{ analysis: ApkAnalysis }>()
const basic = computed(() => props.analysis.basic)

const versionText = computed(() => {
  const b = basic.value
  const parts: string[] = []
  if (b.versionName) parts.push(b.versionName)
  if (b.versionCode != null) {
    parts.push(`(${b.versionCode}${b.versionCodeMajor ? ',' + b.versionCodeMajor : ''})`)
  }
  return parts.join(' ') || '-'
})
</script>

<template>
  <section class="rounded-xl border border-admin-border bg-admin-surface p-4">
    <div class="mb-3 flex items-center gap-2">
      <i class="fas fa-cube text-admin-accent"></i>
      <h3 class="text-sm font-semibold text-admin-text">基本信息</h3>
    </div>
    <div class="flex items-start gap-3">
      <img
        v-if="basic.iconDataUri"
        :src="basic.iconDataUri"
        class="h-14 w-14 rounded-lg border border-admin-border object-cover"
        alt="图标"
      />
      <div
        v-else
        class="flex h-14 w-14 items-center justify-center rounded-lg border border-admin-border bg-admin-bg text-admin-muted"
      >
        <i class="fa fa-android text-xl"></i>
      </div>
      <div class="min-w-0 flex-1">
        <div class="truncate text-base font-semibold text-admin-text">
          {{ basic.label || basic.packageName || '未知应用' }}
        </div>
        <code class="block truncate text-xs text-admin-muted">{{ basic.packageName || '-' }}</code>
        <div class="mt-1 text-xs text-admin-muted">
          <span>版本 {{ versionText }}</span>
          <span class="mx-1">·</span>
          <span>{{ formatBytes(basic.fileSizeBytes) }}</span>
        </div>
      </div>
    </div>
    <div class="mt-3 space-y-1 border-t border-admin-border pt-3 text-xs">
      <div class="flex justify-between gap-2">
        <span class="shrink-0 text-admin-muted">文件</span>
        <span class="truncate text-admin-text">{{ basic.fileName }}</span>
      </div>
      <div v-if="basic.sha256" class="flex justify-between gap-2">
        <span class="shrink-0 text-admin-muted">SHA-256</span>
        <span class="break-all font-mono text-[11px] text-admin-text">{{ basic.sha256 }}</span>
      </div>
      <div v-else-if="basic.sha256SkippedReason" class="text-admin-muted">
        <i class="fas fa-info-circle"></i> {{ basic.sha256SkippedReason }}
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ApkAnalysis } from '@/utils/apk'
import { formatBytes } from '@/utils/apk'

const props = defineProps<{ analysis: ApkAnalysis; variant?: 'compact' | 'full' }>()
const variant = computed(() => props.variant || 'full')
const files = computed(() => props.analysis.files)

const PALETTE = ['#4c8df6', '#34d8a0', '#ffd24d', '#f2555a', '#a78bfa', '#f59e0b', '#10b981', '#6366f1']
const totalUncomp = computed(() => files.value.uncompressedBytes || 1)

const segments = computed(() => {
  const out: { label: string; bytes: number; pct: number; color: string }[] = []
  files.value.topGroups.forEach((g, i) => {
    out.push({
      label: g.path,
      bytes: g.bytes,
      pct: Math.round((g.bytes / totalUncomp.value) * 1000) / 10,
      color: PALETTE[i % PALETTE.length]
    })
  })
  return out
})
</script>

<template>
  <section class="rounded-xl border border-admin-border bg-admin-surface p-4">
    <div class="mb-3 flex items-center gap-2">
      <i class="fas fa-box-open text-admin-accent"></i>
      <h3 class="text-sm font-semibold text-admin-text">文件构成</h3>
    </div>

    <div class="grid grid-cols-3 gap-2 text-center">
      <div class="rounded-lg bg-admin-bg p-2">
        <div class="text-sm font-semibold text-admin-text">{{ files.entryCount }}</div>
        <div class="text-[10px] text-admin-muted">条目数</div>
      </div>
      <div class="rounded-lg bg-admin-bg p-2">
        <div class="text-sm font-semibold text-admin-text">{{ formatBytes(files.uncompressedBytes) }}</div>
        <div class="text-[10px] text-admin-muted">解压体积</div>
      </div>
      <div class="rounded-lg bg-admin-bg p-2">
        <div class="text-sm font-semibold text-admin-text">{{ formatBytes(files.compressedBytes) }}</div>
        <div class="text-[10px] text-admin-muted">压缩体积</div>
      </div>
    </div>

    <!-- 占比条（full） -->
    <template v-if="variant === 'full' && segments.length">
      <div class="mt-3 flex h-3 w-full overflow-hidden rounded-full bg-admin-bg">
        <div
          v-for="s in segments"
          :key="s.label"
          class="h-full"
          :style="{ width: s.pct + '%', backgroundColor: s.color }"
          :title="`${s.label} · ${formatBytes(s.bytes)} · ${s.pct}%`"
        ></div>
      </div>
      <ul class="mt-2 space-y-1 text-[11px]">
        <li v-for="s in segments" :key="s.label" class="flex items-center gap-1.5">
          <span class="inline-block h-2.5 w-2.5 rounded-sm" :style="{ backgroundColor: s.color }"></span>
          <span class="truncate text-admin-text">{{ s.label }}</span>
          <span class="ml-auto shrink-0 text-admin-muted">{{ s.pct }}%</span>
        </li>
      </ul>
    </template>

    <!-- ABI -->
    <div class="mt-3">
      <div class="mb-1 text-[11px] font-medium text-admin-muted">ABI 架构（原生库 {{ files.nativeLibCount }}）</div>
      <div v-if="files.abis.length" class="flex flex-wrap gap-1.5">
        <span
          v-for="a in files.abis"
          :key="a.abi"
          class="rounded border border-admin-border px-1.5 py-0.5 text-[10px] text-admin-text"
          >{{ a.abi }} · {{ a.fileCount }} 文件 · {{ formatBytes(a.bytes) }}</span
        >
      </div>
      <p v-else class="text-[11px] text-admin-muted">无原生库（纯 Java/Kotlin）</p>
    </div>

    <!-- DEX -->
    <div class="mt-2">
      <div class="mb-1 text-[11px] font-medium text-admin-muted">DEX（{{ files.dexFiles.length }}）</div>
      <code v-if="files.dexFiles.length" class="text-[11px] text-admin-text">{{
        files.dexFiles.map((d) => d.name).join(' · ')
      }}</code>
      <p v-else class="text-[11px] text-admin-muted">无 DEX（异常）</p>
    </div>

    <!-- 密度（full） -->
    <div v-if="variant === 'full'" class="mt-2">
      <div class="mb-1 text-[11px] font-medium text-admin-muted">资源密度</div>
      <div v-if="files.densities.length" class="flex flex-wrap gap-1.5">
        <span
          v-for="d in files.densities"
          :key="d.density"
          class="rounded border border-admin-border px-1.5 py-0.5 text-[10px] text-admin-text"
          >{{ d.density }} · {{ d.fileCount }} · {{ formatBytes(d.bytes) }}</span
        >
      </div>
      <p v-else class="text-[11px] text-admin-muted">无密度资源</p>
    </div>
  </section>
</template>

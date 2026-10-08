<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ApkAnalysis, ApkPermission, PermissionLevel } from '@/utils/apk'

const props = defineProps<{ analysis: ApkAnalysis; variant?: 'compact' | 'full' }>()
const query = ref('')
const variant = computed(() => props.variant || 'full')
const summary = computed(() => props.analysis.permissions)

const levelMeta: Record<PermissionLevel | 'unknown', { text: string; cls: string }> = {
  dangerous: { text: '危险', cls: 'bg-red-50 text-red-600 border-red-200' },
  normal: { text: '普通', cls: 'bg-gray-100 text-gray-600 border-gray-200' },
  signature: { text: '签名', cls: 'bg-amber-50 text-amber-600 border-amber-200' },
  unknown: { text: '未收录', cls: 'bg-gray-100 text-gray-500 border-gray-200' }
}

function meta(p: ApkPermission) {
  const lvl = p.documented ? p.level : 'unknown'
  return levelMeta[lvl] || levelMeta.unknown
}

const visible = computed<ApkPermission[]>(() => {
  let items = summary.value.items
  if (variant.value === 'compact') {
    items = items.filter((p) => p.level === 'dangerous').slice(0, 5)
  } else if (query.value.trim()) {
    const q = query.value.trim().toLowerCase()
    items = items.filter(
      (p) => p.name.toLowerCase().includes(q) || p.cn.toLowerCase().includes(q)
    )
  }
  return items
})
</script>

<template>
  <section class="rounded-xl border border-admin-border bg-admin-surface p-4">
    <div class="mb-3 flex items-center justify-between gap-2">
      <div class="flex items-center gap-2">
        <i class="fas fa-shield-alt text-admin-accent"></i>
        <h3 class="text-sm font-semibold text-admin-text">权限清单</h3>
      </div>
      <div class="flex gap-1.5 text-[11px]">
        <span class="rounded-full bg-red-50 px-2 py-0.5 text-red-600">危险 {{ summary.dangerousCount }}</span>
        <span class="rounded-full bg-gray-100 px-2 py-0.5 text-gray-600">共 {{ summary.total }}</span>
        <span
          v-if="summary.customCount"
          class="rounded-full bg-gray-100 px-2 py-0.5 text-gray-600"
          >自定义 {{ summary.customCount }}</span
        >
      </div>
    </div>

    <input
      v-if="variant === 'full'"
      v-model="query"
      type="search"
      placeholder="搜索权限名 / 中文名"
      class="mb-3 w-full rounded-lg border border-admin-border bg-admin-surface px-3 py-1.5 text-xs text-admin-text outline-none focus:border-admin-accent"
    />

    <ul class="space-y-1.5 text-xs">
      <li v-for="p in visible" :key="p.name" class="flex flex-wrap items-center gap-1.5">
        <span class="rounded border px-1.5 py-0.5 text-[10px] font-medium" :class="meta(p).cls">{{ meta(p).text }}</span>
        <span class="font-medium text-admin-text">{{ p.cn }}</span>
        <code class="text-admin-muted">{{ p.name }}</code>
        <span v-if="p.custom" class="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">自定义</span>
        <span v-if="p.sinceSdk23" class="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">SDK23+</span>
        <span v-if="p.maxSdkVersion != null" class="text-[10px] text-admin-muted">≤ SDK {{ p.maxSdkVersion }}</span>
      </li>
      <li v-if="!visible.length" class="text-admin-muted">无匹配权限</li>
    </ul>

    <p v-if="variant === 'compact' && summary.dangerousCount > 5" class="mt-2 text-[11px] text-admin-muted">
      仅显示前 5 项危险权限，完整列表见「APK 分析」查看器。
    </p>
  </section>
</template>

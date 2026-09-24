<script setup lang="ts">
export type SortKey = 'latest' | 'hot' | 'name'

defineProps<{ modelValue: SortKey }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: SortKey): void }>()

const options: { key: SortKey; label: string }[] = [
  { key: 'latest', label: '最新' },
  { key: 'hot', label: '最热' },
  { key: 'name', label: '名称' }
]
</script>

<template>
  <!-- 胶囊分段控件：选中态用实心主色块表达，不靠发光 -->
  <div class="inline-flex items-center gap-1 rounded-full border border-portal-border bg-portal-surface p-1">
    <button
      v-for="o in options"
      :key="o.key"
      class="rounded-full px-3.5 py-1.5 text-sm transition"
      :class="
        modelValue === o.key
          ? 'bg-portal-mint font-semibold text-portal-bg shadow-mint'
          : 'text-portal-muted hover:bg-portal-surface2 hover:text-portal-text'
      "
      @click="emit('update:modelValue', o.key)"
    >
      {{ o.label }}
    </button>
  </div>
</template>

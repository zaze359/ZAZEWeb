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
  <div class="inline-flex gap-1 rounded-xl border border-portal-border bg-portal-surface p-1">
    <button
      v-for="o in options"
      :key="o.key"
      class="rounded-lg px-3 py-1 text-sm transition"
      :class="
        modelValue === o.key
          ? 'bg-portal-neon/20 text-portal-neon'
          : 'text-portal-muted hover:text-portal-text'
      "
      @click="emit('update:modelValue', o.key)"
    >
      {{ o.label }}
    </button>
  </div>
</template>

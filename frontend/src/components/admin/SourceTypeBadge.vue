<script setup lang="ts">
defineProps<{ type?: string }>()
const COLORS: Record<string, string> = {
  GITHUB: '#24292e',
  FDROID: '#19722e',
  IZZY: '#6b4fbb',
  APKMIRROR: '#0a84ff',
  OFFICIAL: '#4f46e5',
  MYAPP: '#e0603a',
  OTHER: '#6b7280'
}
</script>

<template>
  <span
    class="inline-flex items-center rounded px-2 py-0.5 text-xs font-medium text-white"
    :style="{ background: COLORS[(type || 'OTHER').toUpperCase()] || COLORS.OTHER }"
    >{{ type || 'OTHER' }}</span
  >
</template>

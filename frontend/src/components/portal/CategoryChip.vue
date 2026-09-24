<script setup lang="ts">
defineProps<{ label?: string }>()
</script>

<template>
  <span
    class="inline-flex items-center rounded-full border border-portal-neon3/40 bg-portal-neon3/10 px-2.5 py-0.5 text-xs font-medium text-portal-neon3"
  >
    {{ label || '未分类' }}
  </span>
</template>

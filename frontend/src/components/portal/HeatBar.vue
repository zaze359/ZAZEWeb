<script setup lang="ts">
defineProps<{ value: number }>() // 0~100
</script>

<template>
  <div class="h-1.5 w-full overflow-hidden rounded-full bg-portal-surface2">
    <div
      class="h-full rounded-full"
      :style="{
        width: Math.max(0, Math.min(100, value)) + '%',
        background: 'linear-gradient(90deg,#2ce8ff,#ff4ecd)'
      }"
    ></div>
  </div>
</template>

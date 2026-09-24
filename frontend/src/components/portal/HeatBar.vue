<script setup lang="ts">
defineProps<{ value: number }>() // 0~100
</script>

<template>
  <!-- 热度条：薄荷 → 天蓝，同为冷色相邻色，比原「青→品红」跨越更收敛 -->
  <div class="h-1.5 w-full overflow-hidden rounded-full bg-portal-surface2">
    <div
      class="h-full rounded-full"
      :style="{
        width: Math.max(0, Math.min(100, value)) + '%',
        background: 'linear-gradient(90deg,#34d8a0,#4c8df6)'
      }"
    ></div>
  </div>
</template>

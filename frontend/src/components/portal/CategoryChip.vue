<script setup lang="ts">
defineProps<{ label?: string }>()
</script>

<template>
  <!-- 中性标签：与参考站一致，分类用「低对比灰片」承载，不抢占主色 -->
  <span
    class="inline-flex max-w-full items-center truncate rounded-md border border-portal-border bg-portal-surface2 px-2 py-0.5 text-[11px] text-portal-text/80"
  >
    {{ label || '未分类' }}
  </span>
</template>

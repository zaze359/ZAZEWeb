<script setup lang="ts">
import { GRADE_COLOR, type Grade } from '@/utils/appMeta'

const props = defineProps<{ grade: Grade; size?: 'sm' | 'md' }>()
const color = GRADE_COLOR[props.grade]
</script>

<template>
  <span
    class="inline-flex items-center justify-center rounded-lg font-display font-bold"
    :class="size === 'sm' ? 'h-6 w-6 text-xs' : 'h-8 w-8 text-sm'"
    :style="{
      color,
      border: `1.5px solid ${color}`,
      boxShadow: `0 0 10px ${color}55`,
      background: `${color}14`
    }"
    >{{ grade }}</span
  >
</template>

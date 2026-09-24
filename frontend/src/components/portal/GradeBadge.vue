<script setup lang="ts">
import { GRADE_COLOR, type Grade } from '@/utils/appMeta'

const props = defineProps<{ grade: Grade; size?: 'sm' | 'md' }>()
const color = GRADE_COLOR[props.grade]
</script>

<template>
  <!-- 等级用「描边 + 极淡底」表达，去掉原霓虹外发光，保持冷调干净 -->
  <span
    class="inline-flex shrink-0 items-center justify-center rounded-lg font-display font-bold leading-none"
    :class="size === 'sm' ? 'h-6 w-6 text-[11px]' : 'h-8 w-8 text-sm'"
    :style="{ color, border: `1px solid ${color}66`, background: `${color}1f` }"
    >{{ grade }}</span
  >
</template>

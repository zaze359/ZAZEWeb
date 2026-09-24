<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ src?: string; name: string; size?: number }>()
const failed = ref(false)
</script>

<template>
  <div
    class="flex shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-portal-border bg-portal-surface2"
    :style="{ width: (size ?? 56) + 'px', height: (size ?? 56) + 'px' }"
  >
    <img
      v-if="src && !failed"
      :src="src"
      :alt="name"
      class="h-full w-full object-cover"
      @error="failed = true"
    />
    <i v-else class="fas fa-cube text-portal-muted"></i>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{ manifest: unknown }>()
const open = ref(false)
const LIMIT = 200_000
const text = computed(() => {
  try {
    const s = JSON.stringify(props.manifest, null, 2)
    if (s.length > LIMIT) {
      return s.slice(0, LIMIT) + `\n\n… 已截断（原始 ${s.length} 字符，超过 ${LIMIT} 字符上限）`
    }
    return s
  } catch {
    return '（无法序列化原始清单）'
  }
})
</script>

<template>
  <section class="rounded-xl border border-admin-border bg-admin-surface p-4">
    <button class="flex w-full items-center justify-between text-left" @click="open = !open">
      <div class="flex items-center gap-2">
        <i class="fas fa-code text-admin-accent"></i>
        <h3 class="text-sm font-semibold text-admin-text">原始清单 (AndroidManifest)</h3>
      </div>
      <i class="fas" :class="open ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
    </button>
    <pre
      v-if="open"
      class="mt-3 max-h-96 overflow-auto rounded-lg bg-admin-bg p-3 text-[11px] leading-relaxed text-admin-muted"
    ><code>{{ text }}</code></pre>
  </section>
</template>

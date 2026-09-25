<script setup lang="ts">
export interface CatItem {
  label: string
  count: number
}

defineProps<{ items: CatItem[]; modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
</script>

<template>
  <!-- 桌面：左侧竖向分类 rail；移动：顶部横滑胶囊。激活态用薄荷细描边 + 极淡底，不发光 -->
  <nav
    class="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:gap-1 lg:overflow-visible lg:pb-0"
  >
    <button
      v-for="it in items"
      :key="it.label"
      class="group flex shrink-0 items-center justify-between gap-2 rounded-lg border px-3 py-2 text-sm transition lg:w-full"
      :class="
        modelValue === it.label
          ? 'border-portal-mint/40 bg-portal-mint/10 text-portal-mint'
          : 'border-transparent text-portal-muted hover:bg-portal-surface2 hover:text-portal-text'
      "
      @click="emit('update:modelValue', it.label)"
    >
      <span class="flex items-center gap-2 truncate">
        <i class="fas fa-hashtag text-[10px] opacity-50"></i>{{ it.label }}
      </span>
      <span
        class="rounded-full px-1.5 text-[11px]"
        :class="modelValue === it.label ? 'bg-portal-mint/15 text-portal-mint' : 'bg-portal-surface2 text-portal-muted'"
        >{{ it.count }}</span
      >
    </button>
  </nav>
</template>

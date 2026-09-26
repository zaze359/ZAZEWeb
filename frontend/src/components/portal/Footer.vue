<script setup lang="ts">
function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <!-- 页脚：低对比描边顶线 + 幽灵链接，复用门户冷调；admin 不挂载此组件 -->
  <footer class="mt-12 border-t border-portal-border bg-portal-bg/60">
    <div
      class="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-7 sm:flex-row sm:items-center sm:justify-between"
    >
      <div class="flex items-center gap-2">
        <span class="grid h-6 w-6 place-items-center rounded-md bg-portal-mint/15 text-[11px] font-bold text-portal-mint"
          >Z</span
        >
        <span class="font-display text-sm font-semibold text-portal-text">ZAZE 门户</span>
        <span class="text-xs text-portal-muted">· 开源应用下载图谱</span>
      </div>

      <nav class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-portal-muted">
        <router-link to="/" class="transition hover:text-portal-text">首页</router-link>
        <router-link to="/appmarket" class="transition hover:text-portal-text">应用图鉴</router-link>
        <button
          type="button"
          class="transition hover:text-portal-text"
          @click="toTop"
        >
          返回顶部
        </button>
      </nav>

      <p class="text-xs text-portal-muted">© {{ new Date().getFullYear() }} ZAZE · 个人项目</p>
    </div>
  </footer>
</template>

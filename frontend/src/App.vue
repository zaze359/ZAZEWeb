<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { auth } from '@/store/auth'
import NavBar from '@/components/NavBar.vue'
import Footer from '@/components/portal/Footer.vue'

onMounted(() => auth.init())

const route = useRoute()
const theme = computed(() => (route.meta.theme === 'admin' ? 'admin' : 'portal'))
// 页脚仅挂在门户页（登录页是满屏居中，不需要；后台保持原样不牵动）
const showFooter = computed(() => theme.value === 'portal' && route.name !== 'login')
</script>

<template>
  <div
    class="flex min-h-screen flex-col"
    :class="theme === 'admin' ? 'bg-admin-bg text-admin-text' : 'bg-portal-bg text-portal-text'"
  >
    <NavBar v-if="route.name !== 'login'" :variant="theme" />
    <main class="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
      <router-view />
    </main>
    <Footer v-if="showFooter" />
  </div>
</template>

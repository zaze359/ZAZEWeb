<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { auth } from '@/store/auth'
import NavBar from '@/components/NavBar.vue'

onMounted(() => auth.init())

const route = useRoute()
const theme = computed(() => (route.meta.theme === 'admin' ? 'admin' : 'portal'))
</script>

<template>
  <div
    class="min-h-screen"
    :class="theme === 'admin' ? 'bg-admin-bg text-admin-text' : 'bg-portal-bg text-portal-text'"
  >
    <NavBar :variant="theme" />
    <main class="mx-auto max-w-5xl px-4 py-6">
      <router-view />
    </main>
  </div>
</template>

import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import AppMarketView from '@/views/AppMarketView.vue'
import AppDetailView from '@/views/AppDetailView.vue'
import AdminView from '@/views/AdminView.vue'
import LoginView from '@/views/LoginView.vue'

// history 模式，根部署（完全迁移后 Vue 即站点本体）。
// meta.theme 控制整体皮肤：门户=portal（暗色霓虹），后台=admin（现代实用浅色）。
const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { theme: 'portal' } },
    { path: '/login', name: 'login', component: LoginView, meta: { theme: 'portal' } },
    { path: '/appmarket', name: 'appmarket', component: AppMarketView, meta: { theme: 'portal' } },
    { path: '/appmarket/:id', name: 'app-detail', component: AppDetailView, props: true, meta: { theme: 'portal' } },
    { path: '/admin', name: 'admin', component: AdminView, meta: { theme: 'admin' } }
  ]
})

export default router

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 根部署配置（完全迁移后，Vue 即站点本体，挂在 /）：
// - base 设为 /，构建产物直接输出到 Spring 静态资源根目录 src/main/resources/static/，
//   由 VueSpaController 在 / 与所有不含点号的前端路由上回退到 index.html。
// - dev 阶段用 proxy 把 /api 转发到后端 8080，避免跨域，生产环境同源无需。
export default defineConfig({
  plugins: [vue()],
  base: '/',
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: false
  }
})

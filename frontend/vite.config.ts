import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 试点 POC 配置：
// - base 设为 /vue/，与现有 Thymeleaf 页面（/、/appmarket、/login）完全隔离，互不冲突。
// - 构建产物直接输出到 Spring 的静态资源目录 src/main/resources/static/vue/，
//   由 Spring Boot 原样托管，无需任何额外部署步骤（java -jar 即可带前端）。
// - dev 阶段用 proxy 把 /api 转发到后端 8080，避免跨域，生产环境同源无需。
export default defineConfig({
  plugins: [vue()],
  base: '/vue/',
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
    outDir: '../src/main/resources/static/vue',
    emptyOutDir: true
  }
})

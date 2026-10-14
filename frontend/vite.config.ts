import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 30011,
    proxy: {
      '/api': {
        target: 'http://localhost:30010',
        changeOrigin: true
      },
      '/ingest': {
        target: 'http://localhost:30010',
        changeOrigin: true
      }
    }
  },
  build: {
    // monaco 体积较大，放宽告警阈值
    chunkSizeWarningLimit: 4000
  }
})

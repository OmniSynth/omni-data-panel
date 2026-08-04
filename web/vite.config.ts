import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      host: true,
      port: 5173,
      strictPort: true,
      proxy: { '/api': { target: env.VITE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true } },
    },
    preview: {
      host: true,
      port: 4173,
      strictPort: true,
      proxy: { '/api': { target: env.VITE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true } },
    },
  }
})

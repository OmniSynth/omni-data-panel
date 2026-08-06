import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'

const rootDir = path.dirname(fileURLToPath(import.meta.url))
const docsAssetsDir = path.resolve(rootDir, '../docs/assets')

/** 开发与构建时提供 docs/assets 为 /docs-assets */
function docsAssetsPlugin(): Plugin {
  return {
    name: 'omni-docs-assets',
    configureServer(server) {
      server.middlewares.use('/docs-assets', (req, res, next) => {
        const rel = decodeURIComponent((req.url || '/').split('?')[0])
        const file = path.normalize(path.join(docsAssetsDir, rel))
        if (!file.startsWith(docsAssetsDir) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
          next()
          return
        }
        res.setHeader('Content-Type', contentType(file))
        fs.createReadStream(file).pipe(res)
      })
    },
    closeBundle() {
      const out = path.resolve(rootDir, 'dist/docs-assets')
      if (!fs.existsSync(docsAssetsDir)) return
      fs.mkdirSync(out, { recursive: true })
      fs.cpSync(docsAssetsDir, out, { recursive: true })
    },
  }
}

function contentType(file: string): string {
  if (file.endsWith('.png')) return 'image/png'
  if (file.endsWith('.jpg') || file.endsWith('.jpeg')) return 'image/jpeg'
  if (file.endsWith('.webp')) return 'image/webp'
  if (file.endsWith('.svg')) return 'image/svg+xml'
  if (file.endsWith('.gif')) return 'image/gif'
  return 'application/octet-stream'
}

/** 开发服务器下发 frame-ancestors，对齐生产 nginx 行为 */
function frameAncestorsPlugin(allowedOrigins: string): Plugin {
  const embedCsp = allowedOrigins.trim()
    ? `frame-ancestors 'self' ${allowedOrigins.trim()}`
    : "frame-ancestors 'self'"
  const selfOnly = "frame-ancestors 'self'"
  return {
    name: 'omni-frame-ancestors',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const pathName = (req.url || '/').split('?')[0]
        const embeddable = pathName.startsWith('/embed/') || pathName.startsWith('/public/')
        res.setHeader('Content-Security-Policy', embeddable ? embedCsp : selfOnly)
        next()
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const embedOrigins = env.VITE_EMBED_ALLOWED_ORIGINS || env.EMBED_ALLOWED_ORIGINS || ''
  return {
    plugins: [vue(), docsAssetsPlugin(), frameAncestorsPlugin(embedOrigins)],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      host: true,
      port: 5173,
      strictPort: true,
      fs: { allow: [rootDir, path.resolve(rootDir, '..')] },
      proxy: {
        '/api': { target: env.VITE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
        '/oauth2': { target: env.VITE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
        '/login/oauth2': { target: env.VITE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
      },
    },
    preview: {
      host: true,
      port: 4173,
      strictPort: true,
      proxy: {
        '/api': { target: env.VITE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
        '/oauth2': { target: env.VITE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
        '/login/oauth2': { target: env.VITE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
      },
    },
  }
})

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const isVercel = process.env.VERCEL === '1'

export default defineConfig({
  base: isVercel ? '/' : '/static/',
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  server: {
    port: 5173,
    proxy: {
      '/datasets': 'http://localhost:8080',
      '/models': 'http://localhost:8080',
      '/drift': 'http://localhost:8080',
      '/inference': 'http://localhost:8080',
      '/reports': 'http://localhost:8080',
      '/history': 'http://localhost:8080',
      '/findings': 'http://localhost:8080',
      '/dashboard': 'http://localhost:8080',
    }
  },
  build: {
    outDir: isVercel ? 'dist' : '../src/main/resources/static',
    emptyOutDir: isVercel,
  }
})

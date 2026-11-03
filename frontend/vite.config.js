import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  base: '/static/',
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
    outDir: '../src/main/resources/static',
    emptyOutDir: false,
  }
})

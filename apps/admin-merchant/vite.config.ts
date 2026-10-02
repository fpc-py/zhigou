import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api/product': { target: 'http://localhost:8083', changeOrigin: true },
      '/api/order':   { target: 'http://localhost:8085', changeOrigin: true },
      '/api/aftersale': { target: 'http://localhost:8090', changeOrigin: true },
      '/api/auth':    { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
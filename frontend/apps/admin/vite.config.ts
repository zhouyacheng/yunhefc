import { fileURLToPath, URL } from 'node:url';

import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vite';

export default defineConfig({
  // 默认挂在 /admin/；部署在 admin 子域根路径时用 ADMIN_BASE_URL=/ 覆盖。
  base: process.env.ADMIN_BASE_URL ?? '/admin/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5274,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          // 浏览器按同源请求；转发时去掉 Origin，避免后端按跨域 Origin 拒绝。
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
  },
});

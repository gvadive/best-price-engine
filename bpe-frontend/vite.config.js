import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Local dev only -- production serves the built bundle through nginx,
      // which does this same split (see nginx/nginx.conf): /api/compare goes
      // to pricing-engine-service, everything else under /api to ingestion-service.
      '/api/compare': 'http://localhost:8082',
      '/api': 'http://localhost:8081',
    },
  },
})

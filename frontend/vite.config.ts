import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Surcharger avec LEARNINK_API=http://localhost:8081 npm run dev pour viser un autre backend
      '/api': {
        target: process.env.LEARNINK_API ?? 'http://localhost:8080',
        // Pour le navigateur, les appels restent sur la même origine : inutile d'exposer l'origine au CORS du backend
        configure: (proxy) => proxy.on('proxyReq', (request) => request.removeHeader('origin')),
      },
    },
  },
});

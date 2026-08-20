import path from 'node:path'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  if (!env.VITE_API_BASE_URL) {
    throw new Error(
      'VITE_API_BASE_URL is not set. Create a .env file from .env.example (VITE_API_BASE_URL=http://localhost:8080/api for local development) or set VITE_API_BASE_URL in the build environment.',
    )
  }
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            charts: ['chart.js', 'react-chartjs-2'],
            scanner: ['html5-qrcode'],
            radix: [
              '@radix-ui/react-dialog',
              '@radix-ui/react-dropdown-menu',
              '@radix-ui/react-select',
              '@radix-ui/react-label',
              '@radix-ui/react-slot',
              '@radix-ui/react-avatar',
            ],
          },
        },
      },
    },
  }
})
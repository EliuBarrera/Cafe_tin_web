import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true }, // CORS_ORIGINS de la API permite este puerto
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/preparar.ts',
  },
})

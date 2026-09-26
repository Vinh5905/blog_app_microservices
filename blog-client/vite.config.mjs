import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: { outDir: 'build' },
  server: {
    port: 3000,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.js'] },
});

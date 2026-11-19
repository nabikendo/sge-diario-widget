import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Em desenvolvimento, o React Refresh injeta um script inline: libera só no servidor do Vite.
const cspDev = {
  name: 'csp-dev',
  apply: 'serve',
  transformIndexHtml: (html) => html.replace("script-src 'self'", "script-src 'self' 'unsafe-inline'"),
};

// base './' para o index.html funcionar via file:// dentro do Electron empacotado.
export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss(), cspDev],
  server: { port: 5173, strictPort: true },
});

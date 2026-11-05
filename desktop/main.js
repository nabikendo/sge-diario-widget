'use strict';
const { app, BrowserWindow, dialog, ipcMain } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');

const SGE = 'https://sge.seduc.to.pontoid.com.br';
let painel;
function createWindow() {
  painel = new BrowserWindow({
    width: 1180, height: 780, minWidth: 780, minHeight: 550,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  painel.loadFile(path.join(__dirname, 'index.html'));
  painel.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
}
app.whenReady().then(() => {
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });

ipcMain.handle('backup:open', async () => {
  const result = await dialog.showOpenDialog(painel, { properties: ['openFile'], filters: [{ name: 'Backup SGE Diário', extensions: ['json'] }] });
  if (result.canceled) return null;
  const stat = await fs.stat(result.filePaths[0]);
  if (stat.size > 10 * 1024 * 1024) throw new Error('Backup maior que 10 MB.');
  return fs.readFile(result.filePaths[0], 'utf8');
});
ipcMain.handle('report:pdf', async () => {
  const result = await dialog.showSaveDialog(painel, { defaultPath: 'SGE-Diario-Relatorio.pdf', filters: [{ name: 'PDF', extensions: ['pdf'] }] });
  if (result.canceled) return false;
  const bytes = await painel.webContents.printToPDF({ printBackground: true, pageSize: 'A4' });
  await fs.writeFile(result.filePath, bytes);
  return true;
});
ipcMain.handle('sge:open', () => {
  const sge = new BrowserWindow({ width: 1250, height: 850, title: 'SGE Web',
    webPreferences: { partition: 'persist:sge-oficial', contextIsolation: true, nodeIntegration: false, sandbox: true } });
  sge.webContents.setWindowOpenHandler(({ url }) => {
    try { if (new URL(url).origin === SGE) return { action: 'allow' }; } catch (_) {}
    return { action: 'deny' };
  });
  sge.webContents.on('will-navigate', (event, url) => {
    try { if (new URL(url).origin !== SGE) event.preventDefault(); } catch (_) { event.preventDefault(); }
  });
  sge.loadURL(SGE);
});

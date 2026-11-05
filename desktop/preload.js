'use strict';
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('sgeDesktop', Object.freeze({
  abrirBackup: () => ipcRenderer.invoke('backup:open'),
  salvarPdf: () => ipcRenderer.invoke('report:pdf'),
  abrirSge: () => ipcRenderer.invoke('sge:open')
}));

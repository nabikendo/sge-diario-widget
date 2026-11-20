'use strict';
/**
 * Atualização automática do desktop (app instalado): procura versão nova nas releases do GitHub,
 * baixa em segundo plano e avisa a interface. Instala ao fechar o app ou quando a pessoa pedir.
 * No .exe portátil ou em desenvolvimento, não faz nada.
 */
const { app } = require('electron');

let atualizador = null;

function iniciar(avisar) {
  if (!app.isPackaged || process.env.PORTABLE_EXECUTABLE_DIR) return; // portátil não se atualiza sozinho
  try { ({ autoUpdater: atualizador } = require('electron-updater')); } catch { return; }
  atualizador.autoDownload = true;
  atualizador.autoInstallOnAppQuit = true;
  atualizador.on('update-available', (i) => avisar({ estado: 'baixando', versao: i.version }));
  atualizador.on('update-downloaded', (i) => avisar({ estado: 'pronta', versao: i.version }));
  atualizador.on('error', (e) => console.error('[atualização]', e?.message));
  const procurar = () => atualizador.checkForUpdates().catch(() => {});
  setTimeout(procurar, 15_000);
  setInterval(procurar, 6 * 60 * 60 * 1000);
}

function instalarAgora() { atualizador?.quitAndInstall(false, true); }

module.exports = { iniciar, instalarAgora };

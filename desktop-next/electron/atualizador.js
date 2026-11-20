'use strict';
/**
 * Atualização automática do desktop (app instalado): procura versão nova nas releases do GitHub,
 * baixa em segundo plano e avisa a interface. Instala ao fechar o app ou quando a pessoa pedir.
 * Em desenvolvimento, não faz nada.
 */
const { app } = require('electron');

let atualizador = null;
let avisar = () => {};

function iniciar(aoMudar) {
  avisar = aoMudar;
  if (!app.isPackaged) return;
  try { ({ autoUpdater: atualizador } = require('electron-updater')); } catch { return; }
  atualizador.autoDownload = true;
  atualizador.autoInstallOnAppQuit = true;
  atualizador.on('checking-for-update', () => avisar({ estado: 'procurando' }));
  atualizador.on('update-available', (i) => avisar({ estado: 'baixando', versao: i.version }));
  atualizador.on('update-not-available', () => avisar({ estado: 'atual', verificadoEm: new Date().toISOString() }));
  atualizador.on('update-downloaded', (i) => avisar({ estado: 'pronta', versao: i.version }));
  atualizador.on('error', (e) => { console.error('[atualização]', e?.message); avisar({ estado: 'erro', mensagem: 'Não foi possível verificar agora. Confira a internet.' }); });
  const procurar = () => atualizador.checkForUpdates().catch(() => {});
  setTimeout(procurar, 15_000);
  setInterval(procurar, 6 * 60 * 60 * 1000);
}

/** Botão "Verificar agora" das Configurações; o resultado chega pelos avisos acima. */
function verificar() {
  if (!atualizador) { avisar({ estado: 'indisponivel', mensagem: 'A atualização automática funciona só no app instalado.' }); return; }
  atualizador.checkForUpdates().catch(() => {});
}

function instalarAgora() { atualizador?.quitAndInstall(false, true); }

module.exports = { iniciar, verificar, instalarAgora };

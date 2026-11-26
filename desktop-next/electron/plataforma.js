'use strict';
/**
 * O que muda entre Windows e Linux: ícone das janelas, nome do sistema nos textos
 * e "abrir junto com o sistema" (no Linux, um atalho em ~/.config/autostart).
 */
const { app } = require('electron');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');

const ehWindows = process.platform === 'win32';
const ehLinux = process.platform === 'linux';
const nomeSistema = ehWindows ? 'Windows' : process.platform === 'darwin' ? 'macOS' : 'Linux';
// O Linux não lê .ico nas janelas; usa o PNG de 256 px.
const icone = path.join(__dirname, '..', 'assets', ehWindows ? 'sge-diario.ico' : 'sge-diario.png');

const pastaAutostart = () => path.join(process.env.XDG_CONFIG_HOME || path.join(os.homedir(), '.config'), 'autostart');
const arquivoAutostart = () => path.join(pastaAutostart(), 'sge-diario.desktop');
/** Aspas da especificação de atalhos .desktop: escapa ", `, $ e \ dentro das aspas. */
const aspas = (s) => `"${String(s).replace(/(["`$\\])/g, '\\$1')}"`;

/** Linha Exec do atalho de início automático (exportada para os testes). */
function linhaExec(executavel) { return `${aspas(executavel)} --oculto`; }

async function abrirComSistema(ligar) {
  if (!ehLinux) { app.setLoginItemSettings({ openAtLogin: Boolean(ligar), args: ['--oculto'] }); return; }
  if (!app.isPackaged) return; // em desenvolvimento, não cria atalho apontando para o electron do node_modules
  try {
    if (!ligar) { await fs.rm(arquivoAutostart(), { force: true }); return; }
    await fs.mkdir(pastaAutostart(), { recursive: true });
    await fs.writeFile(arquivoAutostart(), [
      '[Desktop Entry]',
      'Type=Application',
      'Name=SGE Diário',
      `Exec=${linhaExec(process.env.APPIMAGE || process.execPath)}`,
      `Icon=${icone}`,
      'X-GNOME-Autostart-enabled=true',
      'Terminal=false',
      '',
    ].join('\n'));
  } catch (e) { console.error('[início automático]', e?.message); }
}

module.exports = { ehWindows, ehLinux, nomeSistema, icone, abrirComSistema, linhaExec };

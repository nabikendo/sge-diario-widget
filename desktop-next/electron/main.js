'use strict';
/**
 * Processo principal do SGE Diário Desktop (autônomo).
 *
 *  - Vitrine: janela principal visível (React + Tailwind), carregada do Vite em desenvolvimento
 *    ou de dist/ no app empacotado. Não tem acesso a Node; fala com o main só pelo preload.
 *  - Motor: janela oculta do SGE (sge/janelaOculta.js) + leituras HTTP com a sessão logada
 *    (sge/cliente.js), orquestradas por motor.js.
 *  - Login: janela visível do SGE, só quando a sessão expira. A senha é digitada na própria
 *    página do SGE; o app não lê nem guarda a senha (mesma regra do Android).
 */
const { app, BrowserWindow, Menu, ipcMain, shell, powerMonitor } = require('electron');
const path = require('node:path');
const { SGE, PARTICAO } = require('./sge/constantes');
const { naTelaDeLogin } = require('./sge/janelaOculta');
const janelaOculta = require('./sge/janelaOculta');
const motor = require('./motor');

const icone = path.join(__dirname, '..', 'assets', 'sge-diario.ico');
const DEV_URL = process.env.VITE_DEV_SERVER_URL; // definido pelo script "dev"
const INTERVALO_AUTOMATICO_MS = 30 * 60 * 1000;

let vitrine = null;
let janelaLogin = null;
let ultimoPainel = null;

// ---------- Vitrine (janela visível) ----------
function criarVitrine() {
  vitrine = new BrowserWindow({
    icon: icone,
    width: 1280, height: 820, minWidth: 960, minHeight: 640,
    title: 'SGE Diário',
    backgroundColor: '#eef0fb',
    // Windows 11: material translúcido nativo atrás do gradiente (ignorado em sistemas sem suporte).
    backgroundMaterial: 'acrylic',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  vitrine.once('ready-to-show', () => vitrine.show());
  vitrine.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) shell.openExternal(url);
    return { action: 'deny' };
  });
  vitrine.webContents.on('will-navigate', (e, url) => {
    if (DEV_URL && url.startsWith(DEV_URL)) return;
    e.preventDefault();
  });
  if (DEV_URL) vitrine.loadURL(DEV_URL);
  else vitrine.loadFile(path.join(__dirname, '..', 'dist', 'index.html'));
  vitrine.on('closed', () => { vitrine = null; });
}

const enviar = (canal, dados) => { if (vitrine && !vitrine.isDestroyed()) vitrine.webContents.send(canal, dados); };

// ---------- Login (janela visível do SGE, só quando necessário) ----------
function abrirLogin() {
  if (janelaLogin && !janelaLogin.isDestroyed()) { janelaLogin.focus(); return Promise.resolve(); }
  return new Promise((resolve) => {
    janelaLogin = new BrowserWindow({
      icon: icone, parent: vitrine ?? undefined, modal: false,
      width: 1100, height: 780, title: 'Entrar no SGE',
      webPreferences: { partition: PARTICAO, contextIsolation: true, nodeIntegration: false, sandbox: true },
    });
    const wc = janelaLogin.webContents;
    wc.setWindowOpenHandler(() => ({ action: 'deny' }));
    wc.on('will-navigate', (e, url) => { if (!url.startsWith(SGE)) e.preventDefault(); });
    // Assim que o SGE sair da tela de login, a sessão está pronta: fecha e sincroniza.
    const conferir = (_e, url) => {
      if (url.startsWith(SGE) && !naTelaDeLogin(url)) {
        janelaLogin?.close();
        enviar('sge:sessao', { logado: true });
        sincronizarEAvisar();
      }
    };
    wc.on('did-navigate', conferir);
    wc.on('did-navigate-in-page', conferir);
    janelaLogin.on('closed', () => { janelaLogin = null; resolve(); });
    janelaLogin.loadURL(SGE + '/Acesso/Entrar').catch(() => {
      enviar('sge:erro', { mensagem: 'Não foi possível abrir o SGE. Confira a conexão.' });
      janelaLogin?.close();
    });
  });
}

// ---------- Sincronização ----------
async function sincronizarEAvisar() {
  try {
    ultimoPainel = await motor.sincronizar((p) => enviar('sync:progresso', p));
    enviar('sync:concluida', ultimoPainel);
    return ultimoPainel;
  } catch (e) {
    if (e instanceof motor.SessaoExpirada) {
      enviar('sge:sessao', { logado: false });
      return null;
    }
    console.error('[sync]', e);
    enviar('sge:erro', { mensagem: e.message || 'Falha ao sincronizar.' });
    return null;
  }
}

// ---------- IPC (a única porta entre a vitrine e o motor) ----------
ipcMain.handle('painel:obter', async () => ultimoPainel ?? (ultimoPainel = await motor.lerCache()));
ipcMain.handle('sync:agora', () => sincronizarEAvisar());
ipcMain.handle('sge:entrar', () => abrirLogin());
ipcMain.handle('sge:abrirWeb', () => shell.openExternal(SGE));

// ---------- Ciclo de vida ----------
const unico = app.requestSingleInstanceLock();
if (!unico) app.quit();
app.on('second-instance', () => { if (vitrine) { if (vitrine.isMinimized()) vitrine.restore(); vitrine.focus(); } });

app.whenReady().then(() => {
  app.setAppUserModelId('br.to.seduc.sgediariowidget.desktop');
  Menu.setApplicationMenu(null); // sem a barra "File, Edit, View…" do Electron
  criarVitrine();
  // Primeira leitura ao abrir; depois a cada 30 min e ao voltar da suspensão.
  vitrine.webContents.once('did-finish-load', () => sincronizarEAvisar());
  setInterval(() => sincronizarEAvisar(), INTERVALO_AUTOMATICO_MS);
  powerMonitor.on('resume', () => sincronizarEAvisar());
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) criarVitrine(); });
});

app.on('window-all-closed', () => {
  janelaOculta.fechar();
  if (process.platform !== 'darwin') app.quit();
});

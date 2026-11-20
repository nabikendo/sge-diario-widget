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
const { app, BrowserWindow, Menu, ipcMain, shell, powerMonitor, dialog, session } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');
const { SGE, PARTICAO } = require('./sge/constantes');
const { naTelaDeLogin } = require('./sge/janelaOculta');
const janelaOculta = require('./sge/janelaOculta');
const motor = require('./motor');
const armazenamento = require('./armazenamento');
const relatorio = require('./relatorio');
const janelaSge = require('./sge/janelaSge');
const atualizador = require('./atualizador');

const icone = path.join(__dirname, '..', 'assets', 'sge-diario.ico');
const DEV_URL = process.env.VITE_DEV_SERVER_URL; // definido pelo script "dev"
let relogioAutomatico = null;

let vitrine = null;
let janelaLogin = null;
let ultimoPainel = null;

// ---------- Vitrine (janela visível) ----------
function criarVitrine(config) {
  vitrine = new BrowserWindow({
    icon: icone,
    width: 1280, height: 820, minWidth: 960, minHeight: 640,
    title: 'SGE Diário',
    backgroundColor: '#eef0fb',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  vitrine.once('ready-to-show', () => {
    if (config.abrirMaximizado) vitrine.maximize();
    vitrine.show();
  });
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
let ultimaCoord = null;
async function sincronizarEAvisar({ manual = false } = {}) {
  try {
    ultimoPainel = await motor.sincronizar((p) => enviar('sync:progresso', p), { manual });
    enviar('sge:perfil', { escola: false });
    enviar('sync:concluida', ultimoPainel);
    return ultimoPainel;
  } catch (e) {
    if (e instanceof motor.SessaoExpirada) {
      enviar('sge:sessao', { logado: false });
      return null;
    }
    if (e instanceof motor.PerfilEscola) {
      // Perfil Unidade escolar: o diário pessoal fica como estava e a escola é lida no lugar.
      enviar('sge:perfil', { escola: true });
      await sincronizarEscolaEAvisar({ manual });
      return null;
    }
    console.error('[sync]', e);
    enviar('sge:erro', { mensagem: e.message || 'Falha ao sincronizar.' });
    return null;
  }
}

async function sincronizarEscolaEAvisar({ manual = true } = {}) {
  try {
    if (!manual) {
      // Leitura automática da escola inteira é pesada: só se a lista de turmas já existe e a última leitura tem mais de 1 hora.
      const conhecidas = await armazenamento.lerTurmasEscola();
      const ultima = ultimaCoord ?? (ultimaCoord = await armazenamento.lerCoordenacao());
      const recente = ultima && Date.now() - Date.parse(ultima.atualizadoEm) < 60 * 60_000;
      if (!conhecidas.length || recente) { enviar('sync:fim', {}); return null; }
    }
    ultimaCoord = await motor.sincronizarEscola((p) => enviar('sync:progresso', p));
    enviar('coord:concluida', ultimaCoord);
    enviar('sync:fim', {});
    return ultimaCoord;
  } catch (e) {
    if (e instanceof motor.SessaoExpirada) { enviar('sge:sessao', { logado: false }); return null; }
    console.error('[coord]', e);
    enviar('sge:erro', { mensagem: e.message || 'Falha ao ler a escola.' });
    return null;
  }
}

// SGE Web dentro do app: guarda a lista de turmas da escola e avisa quando a pessoa entrou.
let logadoNaJanela = false;
janelaSge.configurar({
  turmasEscola: async (lista) => {
    const n = await armazenamento.gravarTurmasEscola(lista);
    if (n) enviar('coord:turmas', { quantidade: n });
  },
  login: () => { if (!logadoNaJanela) { logadoNaJanela = true; enviar('sge:sessao', { logado: true }); } },
});

// ---------- IPC (a única porta entre a vitrine e o motor) ----------
ipcMain.handle('painel:obter', async () => ultimoPainel ?? (ultimoPainel = await motor.lerCache()));
ipcMain.handle('sync:agora', () => sincronizarEAvisar({ manual: true }));
ipcMain.handle('sge:entrar', () => abrirLogin());
ipcMain.handle('sge:abrirWeb', (_e, atalho) => {
  // Só aceita atalhos no formato esperado (vêm da interface, mas a ponte é a fronteira).
  const ok = atalho && typeof atalho.turmaId === 'string' && typeof atalho.turma === 'string' && typeof atalho.componente === 'string'
    && Number.isInteger(atalho.modulo) && atalho.modulo >= 0 && atalho.modulo <= 4;
  janelaSge.abrir(ok ? { turmaId: atalho.turmaId, turma: atalho.turma, componente: atalho.componente, modulo: atalho.modulo,
    datas: Array.isArray(atalho.datas) ? atalho.datas.filter((d) => /^\d{4}-\d{2}-\d{2}$/.test(d)).slice(0, 5) : [],
    horario: typeof atalho.horario === 'string' ? atalho.horario.slice(0, 5) : '' } : null);
});
ipcMain.handle('coord:obter', async () => ({ painel: ultimaCoord ?? (ultimaCoord = await armazenamento.lerCoordenacao()), turmasEscola: (await armazenamento.lerTurmasEscola()).length }));
ipcMain.handle('coord:sincronizar', () => sincronizarEscolaEAvisar({ manual: true }));
ipcMain.handle('app:atualizar', () => atualizador.instalarAgora());
ipcMain.handle('sge:sair', async () => {
  // Apaga só a sessão do SGE (cookies e armazenamento da partição); a próxima sincronização pede login.
  await session.fromPartition(PARTICAO).clearStorageData();
  logadoNaJanela = false;
  janelaOculta.fechar();
  enviar('sge:sessao', { logado: false });
});
ipcMain.handle('historico:obter', () => armazenamento.lerHistorico());
ipcMain.handle('config:obter', async () => ({ ...(await armazenamento.lerConfig()), versao: app.getVersion(), pastaDados: app.getPath('userData') }));
ipcMain.handle('config:salvar', async (_e, parcial) => {
  const config = await armazenamento.gravarConfig(parcial);
  app.setLoginItemSettings({ openAtLogin: config.abrirComWindows });
  agendar(config);
  return { ...config, versao: app.getVersion(), pastaDados: app.getPath('userData') };
});
ipcMain.handle('dados:abrirPasta', () => shell.openPath(app.getPath('userData')));
ipcMain.handle('dados:apagar', async () => { await armazenamento.apagarDados(); ultimoPainel = null; ultimaCoord = null; enviar('coord:concluida', null); });
ipcMain.handle('relatorio:salvar', async (_e, formato) => {
  const escola = formato === 'coord-csv' || formato === 'coord-pdf';
  const painel = escola ? (ultimaCoord ?? await armazenamento.lerCoordenacao()) : (ultimoPainel ?? await motor.lerCache());
  if (!painel) return { ok: false, mensagem: escola ? 'Leia a escola na Coordenação antes de gerar o relatório.' : 'Sincronize antes de gerar o relatório.' };
  const pdf = formato === 'pdf' || formato === 'coord-pdf';
  const dia = new Date().toISOString().slice(0, 10);
  const { canceled, filePath } = await dialog.showSaveDialog(vitrine, {
    title: 'Salvar relatório',
    defaultPath: path.join(app.getPath('documents'), `SGE Diario - ${escola ? 'pendencias da escola' : 'conteudo aplicado'} ${dia}.${pdf ? 'pdf' : 'csv'}`),
    filters: [pdf ? { name: 'PDF', extensions: ['pdf'] } : { name: 'Planilha (CSV)', extensions: ['csv'] }],
  });
  if (canceled || !filePath) return { ok: false };
  const conteudo = escola ? (pdf ? await relatorio.pdfCoordenacao(painel) : relatorio.csvCoordenacao(painel)) : (pdf ? await relatorio.pdf(painel) : relatorio.csv(painel));
  await fs.writeFile(filePath, conteudo);
  shell.showItemInFolder(filePath);
  return { ok: true, caminho: filePath };
});

/** (Re)agenda a sincronização automática conforme a configuração (0 = só manual). */
function agendar(config) {
  clearInterval(relogioAutomatico);
  relogioAutomatico = config.intervaloMin > 0 ? setInterval(() => sincronizarEAvisar(), config.intervaloMin * 60_000) : null;
}

// ---------- Ciclo de vida ----------
const unico = app.requestSingleInstanceLock();
if (!unico) app.exit(0); // segunda cópia: sai já, sem abrir janela nem sincronizar
app.on('second-instance', () => { if (vitrine) { if (vitrine.isMinimized()) vitrine.restore(); vitrine.focus(); } });

if (unico) app.whenReady().then(async () => {
  const config = await armazenamento.lerConfig();
  app.setAppUserModelId('br.to.seduc.sgediariowidget.desktop');
  Menu.setApplicationMenu(null); // sem a barra "File, Edit, View…" do Electron
  criarVitrine(config);
  // Primeira leitura ao abrir; depois no intervalo escolhido e ao voltar da suspensão.
  vitrine.webContents.once('did-finish-load', () => sincronizarEAvisar());
  agendar(config);
  atualizador.iniciar((info) => enviar('app:atualizacao', info));
  powerMonitor.on('resume', () => setTimeout(() => sincronizarEAvisar(), 20_000)); // dá tempo da rede voltar
  app.on('activate', async () => { if (BrowserWindow.getAllWindows().length === 0) criarVitrine(await armazenamento.lerConfig()); });
});

app.on('window-all-closed', () => {
  janelaOculta.fechar();
  if (process.platform !== 'darwin') app.quit();
});

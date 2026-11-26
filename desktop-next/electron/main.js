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
const { app, BrowserWindow, Menu, Tray, Notification, nativeImage, nativeTheme, ipcMain, shell, powerMonitor, dialog, session, net, screen } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');
const { SGE, PARTICAO } = require('./sge/constantes');
const { naTelaDeLogin } = require('./sge/janelaOculta');
const janelaOculta = require('./sge/janelaOculta');
const motor = require('./motor');
const armazenamento = require('./armazenamento');
const relatorio = require('./relatorio');
const relatorioProfessor = require('./relatorioProfessor');
const janelaSge = require('./sge/janelaSge');
const atualizador = require('./atualizador');
const avisos = require('./avisos');
const calendario = require('./calendario');
const nuvem = require('./nuvem');
const { gerarBitmap } = require('./iconeContador');
const plataforma = require('./plataforma');

const { icone } = plataforma;
const DEV_URL = process.env.VITE_DEV_SERVER_URL; // definido pelo script "dev"
let relogioAutomatico = null;
let semRede = false; // a última sincronização automática foi pulada por falta de rede

let vitrine = null;
let janelaLogin = null;
let ultimoPainel = null;
let bandeja = null;
let configAtual = null;
let saindo = false; // true só quando a pessoa escolheu Sair (senão fechar a janela esconde o app na bandeja)
const iniciouOculto = process.argv.includes('--oculto'); // aberto junto com o sistema
const TEMAS = { sistema: 'system', claro: 'light', escuro: 'dark' };

// ---------- Vitrine (janela visível) ----------
function criarVitrine(config) {
  // Linux (Wayland/XWayland): o pedido de maximizar logo ao abrir às vezes é ignorado pelo gerenciador de janelas.
  // Por isso a janela já nasce do tamanho da área útil da tela e o maximize vem depois que ela aparece.
  const area = plataforma.ehLinux && config.abrirMaximizado ? screen.getPrimaryDisplay().workArea : null;
  vitrine = new BrowserWindow({
    icon: icone,
    ...(area ? { x: area.x, y: area.y, width: area.width, height: area.height } : { width: 1280, height: 820 }),
    minWidth: 960, minHeight: 640,
    title: 'SGE Diário',
    backgroundColor: nativeTheme.shouldUseDarkColors ? '#17171b' : '#eef0fb',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  vitrine.once('ready-to-show', () => {
    // No Windows, maximizar antes de mostrar evita o "pulo" de tamanho. No Linux (X11/Wayland),
    // maximize() numa janela escondida é ignorado: lá maximiza na primeira vez que a janela aparece.
    if (config.abrirMaximizado && !plataforma.ehLinux) vitrine.maximize();
    if (config.abrirMaximizado && plataforma.ehLinux) vitrine.once('show', () => setTimeout(() => vitrine?.isDestroyed() || vitrine.maximize(), 150));
    if (!(iniciouOculto && bandeja)) vitrine.show();
  });
  // Fechar a janela mantém o app na bandeja (ao lado do relógio), lendo e avisando. Sair pelo menu da bandeja.
  vitrine.on('close', (e) => {
    if (!saindo && configAtual?.fecharNaBandeja && bandeja) { e.preventDefault(); vitrine.hide(); }
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
  vitrine.on('closed', () => { vitrine = null; app.quit(); }); // as janelas ocultas do motor não podem manter o app vivo
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

// ---------- Bandeja, ícone com contador e notificações ----------
const mostrarVitrine = () => {
  if (!vitrine) { criarVitrine(configAtual ?? {}); return; }
  if (vitrine.isMinimized()) vitrine.restore();
  vitrine.show(); vitrine.focus();
};

function criarBandeja() {
  bandeja = new Tray(nativeImage.createFromBitmap(gerarBitmap(0, 32), { width: 32, height: 32, scaleFactor: 2 }));
  bandeja.setToolTip('SGE Diário');
  bandeja.on('click', mostrarVitrine);
  bandeja.setContextMenu(Menu.buildFromTemplate([
    { label: 'Abrir SGE Diário', click: mostrarVitrine },
    { label: 'Sincronizar agora', click: () => { mostrarVitrine(); sincronizarEAvisar({ manual: true }); } },
    { type: 'separator' },
    { label: 'Sair', click: () => { saindo = true; app.quit(); } },
  ]));
}

/** Número de pendências no ícone da bandeja e na barra de tarefas. */
function atualizarContador(n, rotulo) {
  const img = (t, f) => nativeImage.createFromBitmap(gerarBitmap(n, t), { width: t, height: t, scaleFactor: f });
  bandeja?.setImage(img(32, 2));
  bandeja?.setToolTip(`SGE Diário · ${rotulo}`);
  if (process.platform === 'win32' && vitrine && !vitrine.isDestroyed()) vitrine.setOverlayIcon(n > 0 ? img(16, 1) : null, rotulo);
}
function contadorDoProfessor(painel) {
  const n = avisos.totalPainel(painel);
  atualizarContador(n, n ? `${n} ${n === 1 ? 'pendência' : 'pendências'}` : 'tudo em dia');
}
async function contadorDaEscola(coord) {
  const { visaoPeriodo } = await import('./escolaVisao.mjs');
  const n = (visaoPeriodo(coord, 'bimestre')?.professores ?? []).reduce((s, p) => s + p.total, 0);
  atualizarContador(n, n ? `${n} pendências na escola` : 'escola em dia');
}

function notificar({ titulo, corpo }) {
  if (!Notification.isSupported()) return;
  const n = new Notification({ title: titulo, body: corpo, icon: icone });
  n.on('click', mostrarVitrine);
  n.show();
}

/** Aviso do fim da tarde: confere a cada minuto; uma vez por dia útil depois da hora combinada. */
async function conferirFimDoDia() {
  const config = configAtual ?? await armazenamento.lerConfig();
  if (!config.avisoFimDia) return;
  const estado = await armazenamento.lerEstado();
  if (!avisos.deveAvisarFimDia(new Date(), config.horaAviso, estado.ultimoAvisoFimDia)) return;
  if (calendario.motivoSemAula(hojeLocal(), config)) return; // feriado ou dia sem aula: não incomoda
  let painel = ultimoPainel ?? await motor.lerCache();
  if (!painel || Date.now() - Date.parse(painel.atualizadoEm) > 2 * 3600_000) painel = (await sincronizarEAvisar()) ?? painel; // dados frescos, se a sessão permitir
  await armazenamento.gravarEstado({ ultimoAvisoFimDia: hojeLocal() });
  if (!painel) return;
  // Com o horário do dia em mãos, o aviso diz quantas aulas foram dadas e o que falta registrar delas.
  let resumo = null;
  try { const hoje = (await motor.agenda()).dias.find((d) => d.data === hojeLocal()); resumo = avisos.resumoDoDia(hoje?.aulas, painel, hojeLocal()); } catch { /* sem sessão: usa o aviso geral */ }
  notificar(resumo ? avisos.textoResumoDia(resumo, painel) : avisos.textoFimDia(painel));
}
const hojeLocal = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };

// ---------- Sincronização ----------
/** Com o Google conectado e a opção ligada, manda o backup depois de cada leitura. Falha em silêncio: o botão da tela mostra o erro. */
let ultimoEnvioNuvem = 0;
async function backupAutomatico() {
  try {
    if (!(configAtual ?? await armazenamento.lerConfig()).nuvemAuto) return;
    if (Date.now() - ultimoEnvioNuvem < 5 * 60_000) return; // no máximo a cada 5 minutos
    if (!(await nuvem.status()).conectado) return;
    ultimoEnvioNuvem = Date.now();
    await nuvem.enviar();
  } catch { /* tenta de novo na próxima leitura */ }
}

let ultimaCoord = null;
/** Espera a rede voltar (até `limiteMs`). Devolve true se há rede. */
async function esperarRede(limiteMs) {
  const fim = Date.now() + limiteMs;
  while (!net.isOnline() && Date.now() < fim) await new Promise((r) => setTimeout(r, 2_000));
  return net.isOnline();
}

async function sincronizarEAvisar({ manual = false } = {}) {
  // Automática sem rede: não mostra erro; a próxima tentativa vem no intervalo ou quando a rede voltar.
  if (!manual && !net.isOnline()) { semRede = true; return null; }
  semRede = false;
  try {
    const anterior = ultimoPainel ?? await motor.lerCache();
    ultimoPainel = await motor.sincronizar((p) => enviar('sync:progresso', p), { manual });
    enviar('sge:perfil', { escola: false });
    armazenamento.gravarEstado({ perfil: 'professor' });
    enviar('sync:concluida', ultimoPainel);
    contadorDoProfessor(ultimoPainel);
    backupAutomatico();
    // Pendência nova vira notificação, a não ser que a pessoa já esteja olhando o app.
    const novas = avisos.novasPendencias(anterior, ultimoPainel);
    if (novas.length && configAtual?.notificarNovas && !(vitrine?.isVisible() && vitrine.isFocused())) notificar(avisos.textoNovas(novas));
    motor.agenda().then((a) => enviar('agenda:pronta', a)).catch(() => {});
    return ultimoPainel;
  } catch (e) {
    if (e instanceof motor.SessaoExpirada) {
      enviar('sge:sessao', { logado: false });
      return null;
    }
    if (e instanceof motor.PerfilEscola) {
      // Perfil Unidade escolar: o diário pessoal fica como estava e a escola é lida no lugar.
      enviar('sge:perfil', { escola: true });
      armazenamento.gravarEstado({ perfil: 'escola' });
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
    contadorDaEscola(ultimaCoord);
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
ipcMain.handle('professor:periodo', async (_e, n) => {
  if (!Number.isInteger(n) || n < 1 || n > 4) return null;
  try { return await motor.sincronizar((p) => enviar('sync:progresso', p), { periodo: n }); } catch (e) {
    enviar('sge:erro', { mensagem: e.message || 'Falha ao ler o bimestre.' }); return null;
  } finally { enviar('sync:fim'); }
});
ipcMain.handle('nuvem:status', () => nuvem.status());
ipcMain.handle('nuvem:entrar', async () => { try { return { ok: true, ...(await nuvem.entrar()) }; } catch (e) { return { ok: false, mensagem: e.message }; } });
ipcMain.handle('nuvem:sair', () => nuvem.sair());
ipcMain.handle('nuvem:enviar', async () => { try { return { ok: true, ...(await nuvem.enviar()) }; } catch (e) { return { ok: false, mensagem: e.message }; } });
ipcMain.handle('nuvem:restaurar', async () => {
  try {
    const r = await nuvem.restaurar();
    ultimoPainel = await motor.lerCache(); ultimaCoord = await armazenamento.lerCoordenacao();
    if (ultimoPainel) { enviar('sync:concluida', ultimoPainel); contadorDoProfessor(ultimoPainel); }
    enviar('coord:concluida', ultimaCoord);
    return { ok: true, ...r };
  } catch (e) { return { ok: false, mensagem: e.message }; }
});
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
ipcMain.handle('app:verificar', () => atualizador.verificar());
ipcMain.handle('agenda:obter', async () => {
  try { return await motor.agenda(); } catch (e) {
    if (e instanceof motor.SessaoExpirada) return (await armazenamento.lerAgenda()) ?? null;
    return (await armazenamento.lerAgenda()) ?? null;
  }
});
ipcMain.handle('perfil:obter', async () => {
  const salvo = (await armazenamento.lerEstado()).perfil;
  if (salvo) return salvo;
  // Instalações anteriores ainda sem o perfil guardado: deduz pelo que já foi lido.
  if (await motor.lerCache()) return 'professor';
  return (await armazenamento.lerCoordenacao()) ? 'escola' : null;
});
ipcMain.handle('coord:historico', () => armazenamento.lerHistoricoCoordenacao());
ipcMain.handle('sge:sair', async () => {
  // Apaga só a sessão do SGE (cookies e armazenamento da partição); a próxima sincronização pede login.
  await session.fromPartition(PARTICAO).clearStorageData();
  logadoNaJanela = false;
  janelaOculta.fechar();
  enviar('sge:sessao', { logado: false });
});
ipcMain.handle('historico:obter', () => armazenamento.lerHistorico());
ipcMain.handle('config:obter', async () => ({ ...(await armazenamento.lerConfig()), versao: app.getVersion(), pastaDados: app.getPath('userData'), sistema: plataforma.nomeSistema }));
ipcMain.handle('config:salvar', async (_e, parcial) => {
  const config = await armazenamento.gravarConfig(parcial);
  configAtual = config;
  nativeTheme.themeSource = TEMAS[config.tema] ?? 'system';
  plataforma.abrirComSistema(config.abrirComWindows);
  agendar(config);
  return { ...config, versao: app.getVersion(), pastaDados: app.getPath('userData'), sistema: plataforma.nomeSistema };
});
ipcMain.handle('dados:abrirPasta', () => shell.openPath(app.getPath('userData')));
ipcMain.handle('dados:apagar', async () => { await armazenamento.apagarDados(); ultimoPainel = null; ultimaCoord = null; enviar('coord:concluida', null); atualizarContador(0, 'sem dados'); });
ipcMain.handle('relatorio:salvar', async (_e, formato, opcoes) => {
  const porProfessor = formato === 'coord-prof-pdf' || formato === 'coord-prof-pasta';
  const escola = formato === 'coord-csv' || formato === 'coord-pdf' || porProfessor;
  let painel = escola ? (ultimaCoord ?? await armazenamento.lerCoordenacao()) : (ultimoPainel ?? await motor.lerCache());
  if (escola && painel) { const { visaoPeriodo } = await import('./escolaVisao.mjs'); painel = visaoPeriodo(painel, /^(ano|bimestre|[1-4])$/.test(opcoes?.periodo) ? opcoes.periodo : 'bimestre'); } // o relatório sai igual à tela
  if (!painel) return { ok: false, mensagem: escola ? 'Leia a escola na Coordenação antes de gerar o relatório.' : 'Sincronize antes de gerar o relatório.' };
  const dia = new Date().toISOString().slice(0, 10);
  const rotuloPeriodo = painel?.janela ? `${painel.janela.numero} bimestre` : 'ano letivo';
  if (formato === 'coord-prof-pasta') { // um PDF para cada professor, numa pasta escolhida
    const { canceled: cancelou, filePaths } = await dialog.showOpenDialog(vitrine, { title: 'Escolha a pasta para os PDFs dos professores', defaultPath: app.getPath('documents'), properties: ['openDirectory', 'createDirectory'] });
    if (cancelou || !filePaths?.[0]) return { ok: false };
    const profs = (painel.professores ?? []).filter((p) => p.id);
    if (!profs.length) return { ok: false, mensagem: 'Nenhum professor com pendência neste período.' };
    const pasta = path.join(filePaths[0], `SGE Diario - pendencias por professor ${rotuloPeriodo} ${dia}`);
    await fs.mkdir(pasta, { recursive: true });
    for (const p of profs) await fs.writeFile(path.join(pasta, `${relatorioProfessor.nomeDeArquivo(p.nome)}.pdf`), await relatorio.pdfDoProfessor(painel, p));
    shell.openPath(pasta);
    return { ok: true, caminho: pasta, total: profs.length };
  }
  const pdf = formato === 'pdf' || formato === 'coord-pdf' || formato === 'coord-prof-pdf';
  const { canceled, filePath } = await dialog.showSaveDialog(vitrine, {
    title: 'Salvar relatório',
    defaultPath: path.join(app.getPath('documents'), `SGE Diario - ${porProfessor ? `pendencias por professor ${rotuloPeriodo}` : escola ? `pendencias da escola ${rotuloPeriodo}` : 'conteudo aplicado'} ${dia}.${pdf ? 'pdf' : 'csv'}`),
    filters: [pdf ? { name: 'PDF', extensions: ['pdf'] } : { name: 'Planilha (CSV)', extensions: ['csv'] }],
  });
  if (canceled || !filePath) return { ok: false };
  const conteudo = escola ? (porProfessor ? await relatorio.pdfProfessores(painel) : pdf ? await relatorio.pdfCoordenacao(painel) : relatorio.csvCoordenacao(painel)) : (pdf ? await relatorio.pdf(painel) : relatorio.csv(painel));
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
app.on('second-instance', mostrarVitrine);
app.on('before-quit', () => { saindo = true; }); // atualização e Sair precisam poder fechar de verdade

if (unico) app.whenReady().then(async () => {
  const config = configAtual = await armazenamento.lerConfig();
  app.setAppUserModelId('br.to.seduc.sgediariowidget.desktop');
  Menu.setApplicationMenu(null); // sem a barra "File, Edit, View…" do Electron
  nativeTheme.themeSource = TEMAS[config.tema] ?? 'system';
  criarBandeja();
  criarVitrine(config);
  setInterval(() => conferirFimDoDia().catch(() => {}), 60_000);
  motor.lerCache().then((p) => p && contadorDoProfessor(p));
  // Primeira leitura ao abrir; depois no intervalo escolhido e ao voltar da suspensão.
  vitrine.webContents.once('did-finish-load', () => sincronizarEAvisar());
  agendar(config);
  atualizador.iniciar((info) => enviar('app:atualizacao', info));
  // Ao voltar da suspensão, sincroniza assim que a rede voltar (Wi-Fi costuma levar alguns segundos).
  powerMonitor.on('resume', () => setTimeout(async () => { if (await esperarRede(90_000)) sincronizarEAvisar(); else semRede = true; }, 10_000));
  // Uma sincronização automática pulada por falta de rede roda quando a conexão volta.
  setInterval(() => { if (semRede && net.isOnline()) sincronizarEAvisar(); }, 30_000);
  app.on('activate', async () => { if (BrowserWindow.getAllWindows().length === 0) criarVitrine(await armazenamento.lerConfig()); });
});

app.on('window-all-closed', () => {
  janelaOculta.fechar();
  if (process.platform !== 'darwin') app.quit();
});

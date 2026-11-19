'use strict';
/**
 * Janela oculta do SGE (o "motor invisível").
 *
 * Ela NÃO raspa telas para extrair as pendências (isso fica com as rotas AJAX, em leitor.js).
 * Serve para o que só um navegador de verdade faz:
 *  1. Conferir se a sessão está válida, carregando a página inicial com JavaScript.
 *  2. Ler a lista de turmas (#TurmaContextoId), que algumas instalações do SGE montam por
 *     JavaScript. No Android, é o "contextoSalvo" lido do WebView.
 *  3. Manter a sessão viva entre sincronizações.
 */
const { BrowserWindow } = require('electron');
const { SGE, PARTICAO, CAMINHO_LOGIN } = require('./constantes');

let janela = null;

function obterJanela() {
  if (janela && !janela.isDestroyed()) return janela;
  janela = new BrowserWindow({
    show: false,
    width: 1280, height: 900,
    webPreferences: {
      partition: PARTICAO,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: false, // a janela fica escondida, mas o JS do SGE precisa rodar
    },
  });
  janela.webContents.setAudioMuted(true);
  // Nada de pop-ups nem navegação para fora do SGE.
  janela.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  janela.webContents.on('will-navigate', (e, url) => { if (!url.startsWith(SGE)) e.preventDefault(); });
  return janela;
}

const naTelaDeLogin = (url) => new URL(url).pathname.toLowerCase().includes(CAMINHO_LOGIN.toLowerCase());

/** Espera um seletor aparecer na página (o SGE carrega partes por AJAX). */
async function esperarSeletor(wc, seletor, limiteMs = 15_000) {
  const fim = Date.now() + limiteMs;
  while (Date.now() < fim) {
    const ok = await wc.executeJavaScript(`!!document.querySelector(${JSON.stringify(seletor)})`, true).catch(() => false);
    if (ok) return true;
    await new Promise((r) => setTimeout(r, 400));
  }
  return false;
}

/**
 * Carrega a página inicial escondida.
 * @returns {Promise<{logado: boolean, opcoesTurma: Array<{v:string,d:string,e:string,t:string}>}>}
 */
async function lerPaginaInicial() {
  const w = obterJanela();
  await w.loadURL(SGE + '/');
  if (naTelaDeLogin(w.webContents.getURL())) return { logado: false, opcoesTurma: [] };
  await esperarSeletor(w.webContents, '#TurmaContextoId option[value]:not([value=""])', 10_000);
  // Leitura somente: o script retorna dados e não clica em nada.
  const opcoesTurma = await w.webContents.executeJavaScript(`
    Array.from(document.querySelectorAll('#TurmaContextoId option'))
      .filter(o => o.value && o.value.trim())
      .map(o => ({ v: o.value, d: o.dataset.turmadescricao || o.textContent || '',
                   e: o.dataset.escoladescricao || '', t: o.dataset.tipodaturmaenum || '' }))
  `, true).catch(() => []);
  return { logado: true, opcoesTurma };
}

function fechar() { if (janela && !janela.isDestroyed()) janela.destroy(); janela = null; }

module.exports = { lerPaginaInicial, fechar, naTelaDeLogin };

'use strict';
/**
 * SGE Web dentro do app: janela visível com a mesma sessão do motor, para o professor fazer o diário.
 *
 * - Abre já logado (mesma partição). Se a sessão expirou, mostra o login do SGE nela mesma.
 * - Atalhos levam direto à tela certa da turma (Conteúdo, Chamada, Planos, PEI), pelo mesmo caminho
 *   que a pessoa faria à mão. É a porta de SgeAtalhoWeb.kt: nunca clica em Salvar nem gera dados.
 * - Em Escola → Conteúdo Aplicado (perfil Unidade escolar), lê sozinha a lista de turmas da escola
 *   (#SelecaoDeTurma) para a Coordenação.
 */
const { BrowserWindow, shell } = require('electron');
const { SGE, PARTICAO } = require('./constantes');
const { naTelaDeLogin } = require('./janelaOculta');

let janela = null;
let pendente = null; // atalho esperando a página carregar (ex.: depois do login)
let aoTurmasEscola = () => {};
let aoLogin = () => {};

const ROTULOS = ['Conteúdo', 'Chamada', 'Plano de aula', 'Plano de curso', 'PEI'];
const PADROES = [['/conteudo'], ['/frequenciadiaria', '/notasfaltas/frequencia'], ['/planejamentoturma'], ['/planejamentoturma'], ['/planejamentoturma']];
const MARCADOR = ['#formConteudoAplicado', '#tableListaAlunos,#gridFrequencia,#divTabelaFrequencia', '#formPlanejamentoLista', '#formPlanejamentoLista', '#formPlanejamentoLista'];
const SEL_COMPONENTE = ['#GradeEscolarId', '#GradeEscolarIdPreenchimento,#DisciplinaId,#GradeEscolarId', '#GradeEscolarId', '#GradeEscolarId', '#GradeEscolarId'];
const PESQUISAR = ['', '#btnGerar', '#btn_search', '#btn_search', '#btn_search'];
const TIPO = ['', '', '2', '3', '4'];

/** Script do atalho (mesma lógica do app Android). */
function jsAtalho(a) {
  const m = a.modulo;
  const cfg = {
    turmaId: a.turmaId, turma: a.turma, comp: a.componente, rotulo: ROTULOS[m], modulo: m,
    padroes: PADROES[m], marcador: MARCADOR[m], selComp: SEL_COMPONENTE[m], pesquisar: PESQUISAR[m], tipo: TIPO[m],
    datas: (a.datas ?? []).map((iso) => iso.slice(8, 10) + '/' + iso.slice(5, 7)), horarioPref: a.horario ?? '',
  };
  return `(async function(){
    function norm(s){return(s||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase().replace(/\\s+/g,' ').trim();}
    function aviso(t){var d=document.getElementById('__sgeAviso');if(!d){d=document.createElement('div');d.id='__sgeAviso';
      d.style.cssText='position:fixed;left:50%;transform:translateX(-50%);bottom:24px;max-width:85%;z-index:99999;background:#1c1340;color:#fff;padding:12px 18px;border-radius:14px;font:14px "Segoe UI",sans-serif;box-shadow:0 8px 24px rgba(0,0,0,.25);text-align:center';
      document.body.appendChild(d);}d.textContent=t;clearTimeout(window.__sgeAvisoT);window.__sgeAvisoT=setTimeout(function(){if(d.parentNode)d.parentNode.removeChild(d);},9000);}
    var dorme=function(ms){return new Promise(function(r){setTimeout(r,ms);});};
    async function esperar(teste,ms){var fim=Date.now()+ms;while(Date.now()<fim){try{var v=teste();if(v)return v;}catch(e){}await dorme(250);}return null;}
    function disparar(el,ev){if(window.jQuery)window.jQuery(el).trigger(ev);else el.dispatchEvent(new Event(ev,{bubbles:true}));}
    function escolherValor(s,valor){if(s.value===valor)return true;s.value=valor;disparar(s,'change');return s.value===valor;}
    function escolherTexto(s,comp){var ops=[].slice.call(s.options).filter(function(o){return o.value&&comp&&norm(o.text)===comp;});if(ops.length!==1)return false;return escolherValor(s,ops[0].value);}
    var cfg=${JSON.stringify(cfg)}; cfg.comp=norm(cfg.comp);
    aviso('Abrindo '+cfg.rotulo+' de '+cfg.turma+'…');
    var ctx=await esperar(function(){return document.getElementById('TurmaContextoId');},10000);
    if(!ctx){aviso('Seletor de turma indisponível. Escolha a turma no SGE.');return;}
    function nomeTurma(o){var s=norm(o.getAttribute('data-turmadescricao')||o.text);var alvo=norm(cfg.turma);return s===alvo||s.indexOf(alvo+'/')===0||s.indexOf(alvo+' -')===0;}
    var sel=ctx.options[ctx.selectedIndex];
    var idOk=sel&&(sel.value===cfg.turmaId||sel.value.split('_')[0]===cfg.turmaId);
    var trocou=false;
    if(!idOk||!nomeTurma(sel)){
      var ops=[].slice.call(ctx.options).filter(function(o){return nomeTurma(o)&&(o.value===cfg.turmaId||o.value.split('_')[0]===cfg.turmaId);});
      if(ops.length===0)ops=[].slice.call(ctx.options).filter(nomeTurma);
      if(ops.length!==1){aviso('Turma não identificada de forma única. Selecione-a no SGE.');return;}
      if(!escolherValor(ctx,ops[0].value)){aviso('Não foi possível selecionar a turma.');return;}
      trocou=true;await dorme(1800);
    }
    sel=ctx.options[ctx.selectedIndex];
    if(!sel||!nomeTurma(sel)){aviso('A turma selecionada não corresponde.');return;}
    if(trocou||!cfg.marcador.split(',').some(function(s){return document.querySelector(s);})){
      var acoes=[].slice.call(document.querySelectorAll('#div-contexto-acoes-turma a,#div-contexto-turma-visao-professor a'));
      var el=null;
      for(var i=0;i<cfg.padroes.length&&!el;i++){el=acoes.find(function(a){return norm(a.getAttribute('onclick')||'').indexOf(cfg.padroes[i])>=0;});}
      if(!el){aviso('Não achei "'+cfg.rotulo+'" no menu desta turma.');return;}
      var oc=el.getAttribute('onclick')||'';var mm=oc.match(/CarregarPaginaAjaxComLoading\\(('[^']*'|"[^"]*")/);
      if(mm){try{window.CarregarPaginaAjaxComLoading(mm[1].slice(1,-1));}catch(e){el.click();}}else el.click();
      var marcadores=cfg.marcador.split(',');
      var abriu=await esperar(function(){return marcadores.some(function(s){return document.querySelector(s);});},15000);
      if(!abriu){aviso('A tela de '+cfg.rotulo+' não abriu. Use o menu da turma.');return;}
      await dorme(600);
    }
    if(cfg.tipo){var t=document.getElementById('TipoDeRegistroPedagogicoId');if(t&&t.value!==cfg.tipo){escolherValor(t,cfg.tipo);await dorme(600);}}
    var sels=cfg.selComp.split(',').reduce(function(acc,s){return acc.concat([].slice.call(document.querySelectorAll(s.trim())));},[]).filter(function(s){return s.tagName==='SELECT';});
    sels.sort(function(a,b){return b.options.length-a.options.length;});
    var feito=false;
    for(var i=0;i<sels.length&&!feito;i++){feito=await esperar(function(s){return function(){return escolherTexto(s,cfg.comp);};}(sels[i]),8000);}
    // Só botões de pesquisar (nunca salvar nem gerar em lote).
    if(feito&&cfg.pesquisar){await dorme(400);var b=document.querySelector(cfg.pesquisar);if(b&&b.id!=='btn-submit-procedimento-lote'&&!b.id.startsWith('btnGerarFrequencia'))b.click();}
    if(feito&&cfg.modulo===0&&cfg.datas.length>0){
      var alvo=cfg.datas[0];await dorme(400);
      var clicou=await esperar(function(){var dias=[].slice.call(document.querySelectorAll('.calendario .semana .vermelho'));
        var d=dias.find(function(el){var t=el.getAttribute('data-original-title')||el.getAttribute('title')||'';var m=t.match(/(\\d{2}\\/\\d{2})\\/\\d{4}/);return m&&m[1]===alvo;});
        if(!d)return false;d.click();return true;},8000);
      aviso(clicou?'Dia '+alvo+' selecionado. Confira o componente e adicione o conteúdo no SGE.':cfg.rotulo+' de '+cfg.turma+' pronto. Clique no dia '+alvo+' (em vermelho).');
    } else if(feito&&cfg.modulo===1){
      await dorme(600);
      var hs=[].slice.call(document.querySelectorAll('#_ListaDeHorariosTurma button')).filter(function(b){return cfg.horarioPref&&(b.textContent||'').indexOf(cfg.horarioPref)>=0;});
      if(hs.length===1){hs[0].click();aviso('Horário '+cfg.horarioPref+' selecionado. Confira a chamada antes de salvar.');}
      else aviso('Chamada de '+cfg.turma+' aberta. Selecione o horário e confira antes de salvar.');
    } else if(!feito) aviso('Turma pronta. Escolha o componente '+${JSON.stringify(a.componente)}+'.');
    else aviso(cfg.rotulo+' de '+cfg.turma+' pronto.');
  })()`;
}

/** Lê as turmas da escola quando a página Escola → Conteúdo Aplicado estiver aberta. */
const JS_TURMAS_ESCOLA = `(function(){var s=document.querySelector('#SelecaoDeTurma');if(!s)return null;
  return Array.prototype.slice.call(s.options).filter(function(o){return o.value;}).map(function(o){
  return {v:o.value,t:o.text.trim(),tp:o.getAttribute('data-tipodeturma')||'0'};});})()`;

function criar(pai) {
  janela = new BrowserWindow({
    title: 'SGE Web', width: 1280, height: 860, show: false, autoHideMenuBar: true,
    icon: require('../plataforma').icone,
    webPreferences: { partition: PARTICAO, contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  janela.removeMenu();
  janela.once('ready-to-show', () => { janela.maximize(); janela.show(); });
  const wc = janela.webContents;
  // Links para fora do SGE (ex.: manuais) vão para o navegador do computador.
  wc.setWindowOpenHandler(({ url }) => {
    if (url.startsWith(SGE)) { wc.loadURL(url); } else if (url.startsWith('https://')) shell.openExternal(url);
    return { action: 'deny' };
  });
  wc.on('will-navigate', (e, url) => { if (!url.startsWith(SGE)) { e.preventDefault(); if (url.startsWith('https://')) shell.openExternal(url); } });
  wc.on('did-stop-loading', async () => {
    const url = wc.getURL();
    if (naTelaDeLogin(url)) return;
    aoLogin();
    if (pendente) { const a = pendente; pendente = null; wc.executeJavaScript(jsAtalho(a), true).catch(() => {}); }
  });
  // A lista de turmas da escola aparece por AJAX: confere de tempos em tempos enquanto a janela está aberta.
  const relogio = setInterval(async () => {
    if (!janela || janela.isDestroyed()) return;
    const lista = await wc.executeJavaScript(JS_TURMAS_ESCOLA, true).catch(() => null);
    if (Array.isArray(lista) && lista.length) aoTurmasEscola(lista);
  }, 4000);
  janela.on('closed', () => { clearInterval(relogio); janela = null; pendente = null; });
  return janela;
}

/**
 * Abre (ou traz para frente) o SGE Web.
 * @param {null | {turmaId:string, turma:string, componente:string, modulo:number, datas?:string[], horario?:string}} atalho
 */
function abrir(atalho = null) {
  const nova = !janela || janela.isDestroyed();
  if (nova) criar();
  else { if (janela.isMinimized()) janela.restore(); janela.focus(); }
  const url = nova ? '' : janela.webContents.getURL();
  if (atalho && url.startsWith(SGE) && !naTelaDeLogin(url)) {
    janela.webContents.executeJavaScript(jsAtalho(atalho), true).catch(() => {});
  } else {
    pendente = atalho;
    if (nova || naTelaDeLogin(url) || !url.startsWith(SGE)) janela.loadURL(SGE + '/').catch(() => {});
  }
}

function configurar({ turmasEscola, login }) {
  if (turmasEscola) aoTurmasEscola = turmasEscola;
  if (login) aoLogin = login;
}

module.exports = { abrir, configurar, jsAtalho };

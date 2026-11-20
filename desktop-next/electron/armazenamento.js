'use strict';
/** Arquivos JSON do app em userData: configurações, painel (cache) e histórico de sincronizações. */
const { app } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');

const arquivo = (nome) => path.join(app.getPath('userData'), nome);

async function ler(nome, padrao) {
  try { return JSON.parse(await fs.readFile(arquivo(nome), 'utf8')); } catch { return padrao; }
}
async function gravar(nome, dados) {
  const destino = arquivo(nome);
  await fs.mkdir(path.dirname(destino), { recursive: true });
  await fs.writeFile(destino + '.tmp', JSON.stringify(dados, null, 2));
  await fs.rename(destino + '.tmp', destino); // gravação atômica
}

const CONFIG_PADRAO = Object.freeze({
  intervaloMin: 30,          // 0 = só manual
  incluirDiversificada: false,
  incluirAee: false,
  abrirComWindows: false,
  abrirMaximizado: true,
});

async function lerConfig() { return { ...CONFIG_PADRAO, ...(await ler('config.json', {})) }; }
async function gravarConfig(parcial) {
  const atual = await lerConfig();
  const nova = { ...atual };
  for (const [k, v] of Object.entries(parcial ?? {})) {
    if (!(k in CONFIG_PADRAO) || typeof v !== typeof CONFIG_PADRAO[k]) continue; // só chaves e tipos conhecidos
    nova[k] = k === 'intervaloMin' ? [0, 15, 30, 60, 120].includes(v) ? v : atual[k] : v;
  }
  await gravar('config.json', nova);
  return nova;
}

const LIMITE_HISTORICO = 180;

/** Registra um resumo de cada sincronização (para a tela Histórico). */
async function registrarHistorico(painel) {
  const lista = await ler('historico.json', []);
  const turmas = painel.turmas ?? [];
  lista.push({
    em: painel.atualizadoEm,
    dias: turmas.reduce((s, t) => s + (t.pendentes?.length ?? 0), 0),
    pendentes: turmas.filter((t) => t.status === 'pendente').length,
    emDia: turmas.filter((t) => t.status === 'em-dia').length,
    erros: turmas.filter((t) => t.status === 'erro').length,
    total: turmas.length,
    porTurma: Object.fromEntries(turmas.map((t) => [`${t.nome} · ${t.disciplina}`, t.pendentes?.length ?? 0])),
  });
  await gravar('historico.json', lista.slice(-LIMITE_HISTORICO));
}
const lerHistorico = () => ler('historico.json', []);
const lerPainel = () => ler('painel.json', null);
const gravarPainel = (p) => gravar('painel.json', p);

const lerCoordenacao = () => ler('coordenacao.json', null);
const gravarCoordenacao = (p) => gravar('coordenacao.json', p);
/** Turmas da escola lidas do seletor #SelecaoDeTurma (Escola → Conteúdo Aplicado). */
const lerTurmasEscola = () => ler('turmas-escola.json', []);
async function gravarTurmasEscola(lista) {
  const validas = (Array.isArray(lista) ? lista : []).filter((o) => o && typeof o.v === 'string' && o.v && typeof o.t === 'string')
    .map((o) => ({ v: o.v, t: o.t.trim(), tp: typeof o.tp === 'string' ? o.tp : '0' }));
  if (!validas.length) return 0;
  const atual = await lerTurmasEscola();
  if (JSON.stringify(atual) === JSON.stringify(validas)) return 0; // nada mudou: não regrava nem avisa
  await gravar('turmas-escola.json', validas);
  return validas.length;
}

async function apagarDados() {
  await Promise.all(['painel.json', 'historico.json', 'coordenacao.json', 'turmas-escola.json'].map((n) => fs.rm(arquivo(n), { force: true })));
}

module.exports = { lerCoordenacao, gravarCoordenacao, lerTurmasEscola, gravarTurmasEscola, lerConfig, gravarConfig, registrarHistorico, lerHistorico, lerPainel, gravarPainel, apagarDados, CONFIG_PADRAO };

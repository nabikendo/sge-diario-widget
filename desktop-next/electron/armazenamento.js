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
  tema: 'sistema',           // 'sistema' | 'claro' | 'escuro'
  fecharNaBandeja: true,     // fechar a janela mantém o app ao lado do relógio
  notificarNovas: true,      // notificação do Windows quando aparece pendência nova
  avisoFimDia: true,         // resumo no fim da tarde (dias úteis)
  horaAviso: '17:30',
  coordChamada: true,        // Coordenação: ler também a Chamada de cada professor (mais consultas)
  nuvemAuto: true,           // com o Google conectado, envia o backup a cada sincronização
  introVista: false,         // tutorial de boas-vindas já visto
  diasSemAula: '',           // datas sem aula além dos feriados (uma por linha: "AAAA-MM-DD motivo")
  diasLetivos: '',           // sábados letivos e feriados trabalhados (uma data por linha)
  coordPlanos: false,        // Coordenação: ler também Plano de aula, Plano de curso e PEI
});

async function lerConfig() { return { ...CONFIG_PADRAO, ...(await ler('config.json', {})) }; }
async function gravarConfig(parcial) {
  const atual = await lerConfig();
  const nova = { ...atual };
  for (const [k, v] of Object.entries(parcial ?? {})) {
    if (!(k in CONFIG_PADRAO) || typeof v !== typeof CONFIG_PADRAO[k]) continue; // só chaves e tipos conhecidos
    if (k === 'intervaloMin') nova[k] = [0, 15, 30, 60, 120].includes(v) ? v : atual[k];
    else if (k === 'tema') nova[k] = ['sistema', 'claro', 'escuro'].includes(v) ? v : atual[k];
    else if (k === 'horaAviso') nova[k] = /^([01]\d|2[0-3]):[0-5]\d$/.test(v) ? v : atual[k];
    else if (k === 'diasSemAula' || k === 'diasLetivos') nova[k] = String(v).slice(0, 4000);
    else nova[k] = v;
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

const lerAgenda = () => ler('agenda.json', null);
const gravarAgenda = (a) => gravar('agenda.json', a);
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

/** Histórico da Coordenação: dias sem conteúdo (e total de pendências) por professor, a cada leitura da escola. */
const lerHistoricoCoordenacao = () => ler('historico-coordenacao.json', []);
async function registrarHistoricoCoordenacao(painel) {
  const lista = await ler('historico-coordenacao.json', []);
  lista.push({ em: painel.atualizadoEm, total: painel.professores.reduce((s, p) => s + (p.total ?? p.dias), 0),
    porProfessor: Object.fromEntries(painel.professores.filter((p) => p.id).map((p) => [p.nome, p.total ?? p.dias])) });
  await gravar('historico-coordenacao.json', lista.slice(-120));
}

/** Pequenos estados do app (ex.: dia do último aviso do fim da tarde). */
const lerEstado = () => ler('estado.json', {});
async function gravarEstado(parcial) { await gravar('estado.json', { ...(await lerEstado()), ...parcial }); }

async function apagarDados() {
  await Promise.all(['painel.json', 'historico.json', 'coordenacao.json', 'historico-coordenacao.json', 'turmas-escola.json', 'agenda.json'].map((n) => fs.rm(arquivo(n), { force: true })));
}

/** Grava o resultado da fusão com a cópia da nuvem (painéis e históricos). */
async function restaurarCopia({ painel, coordenacao, historico, historicoCoordenacao }) {
  if (painel) await gravar('painel.json', painel);
  if (coordenacao) await gravar('coordenacao.json', coordenacao);
  await gravar('historico.json', (historico ?? []).slice(-LIMITE_HISTORICO));
  await gravar('historico-coordenacao.json', (historicoCoordenacao ?? []).slice(-120));
}

module.exports = { restaurarCopia, lerAgenda, gravarAgenda, lerHistoricoCoordenacao, registrarHistoricoCoordenacao, lerEstado, gravarEstado, lerCoordenacao, gravarCoordenacao, lerTurmasEscola, gravarTurmasEscola, lerConfig, gravarConfig, registrarHistorico, lerHistorico, lerPainel, gravarPainel, apagarDados, CONFIG_PADRAO };

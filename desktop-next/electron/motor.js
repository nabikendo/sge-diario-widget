'use strict';
/**
 * Motor de sincronização: junta janela oculta + cliente HTTP + leitor e entrega à interface
 * painéis já limpos (professor e coordenação). A interface nunca vê HTML do SGE.
 */
const { session } = require('electron');
const { PARTICAO } = require('./sge/constantes');
const { criarCliente, SessaoExpirada } = require('./sge/cliente');
const { criarLeitor } = require('./sge/leitor');
const P = require('./sge/parsing');
const janelaOculta = require('./sge/janelaOculta');
const armazenamento = require('./armazenamento');

class PerfilEscola extends Error {
  constructor() { super('Perfil Unidade escolar ativo no SGE.'); this.name = 'PerfilEscola'; }
}

const lerCache = () => armazenamento.lerPainel();
const PLANOS = [
  { chave: 'planoAula', tipo: 'PLANO DE AULA' },
  { chave: 'planoCurso', tipo: 'PLANO DE CURSO' },
  { chave: 'pei', tipo: 'PEI - Plano de Ensino Individualizado' },
];
const VALIDADE_PLANOS_MS = 24 * 60 * 60 * 1000; // planos mudam pouco: na sincronização automática, relê 1x por dia

/** Executa tarefas com no máximo `limite` ao mesmo tempo (para não sobrecarregar o SGE). */
async function emParalelo(itens, limite, fn) {
  const resultados = new Array(itens.length);
  let proximo = 0;
  await Promise.all(Array.from({ length: Math.min(limite, itens.length) }, async () => {
    while (proximo < itens.length) { const i = proximo++; resultados[i] = await fn(itens[i], i); }
  }));
  return resultados;
}

/** Quantidade pendente de cada módulo (null = não lido). */
function contagens(m) {
  return {
    conteudo: m.conteudo?.erro ? null : m.conteudo?.pendentes?.length ?? null,
    frequencia: m.frequencia?.erro ? null : m.frequencia?.datas?.length ?? null,
    planoAula: m.planoAula?.erro || m.planoAula?.indisponivel ? null : m.planoAula?.pendentes ?? null,
    planoCurso: m.planoCurso?.erro || m.planoCurso?.indisponivel ? null : m.planoCurso?.pendentes ?? null,
    pei: m.pei?.erro || m.pei?.indisponivel ? null : m.pei?.pendentes ?? null,
  };
}

async function prepararSessao(aoProgresso) {
  const sessao = session.fromPartition(PARTICAO);
  const cliente = criarCliente(sessao);
  const config = await armazenamento.lerConfig();
  const incluir = new Set([config.incluirDiversificada && 'DIVERSIFICADA', config.incluirAee && 'AEE'].filter(Boolean));
  const leitor = criarLeitor(cliente, { incluir });
  aoProgresso({ etapa: 'Conferindo a sessão do SGE', feitas: 0, total: null });
  const inicial = await janelaOculta.lerPaginaInicial();
  if (!inicial.logado) throw new SessaoExpirada();
  return { leitor, config, inicial };
}

let emAndamento = null;
const exclusivo = (fn) => {
  if (emAndamento) return emAndamento; // duas chamadas viram uma só
  emAndamento = fn().finally(() => { emAndamento = null; });
  return emAndamento;
};

/**
 * Painel do professor: Conteúdo, Chamada, Plano de aula, Plano de curso e PEI de cada turma.
 * @param {(p: {etapa: string, feitas: number, total: number|null}) => void} aoProgresso
 * @param {{manual?: boolean}} opcoes manual = relê também os planos
 */
function sincronizar(aoProgresso = () => {}, { manual = false } = {}) {
  return exclusivo(async () => {
    const { leitor, config, inicial } = await prepararSessao(aoProgresso);
    if ((await leitor.perfil()) === 'escola') throw new PerfilEscola();

    aoProgresso({ etapa: 'Lendo suas turmas', feitas: 0, total: null });
    const { turmas } = await leitor.turmas(inicial.opcoesTurma);
    const bimestre = await leitor.bimestreAtual(turmas[0].id);
    const anterior = await armazenamento.lerPainel();
    const anteriores = new Map((anterior?.turmas ?? []).map((t) => [P.chaveVinculo(t.id, t.disciplina), t]));

    let feitas = 0;
    const total = turmas.length;
    aoProgresso({ etapa: 'Lendo Conteúdo, Chamada e Planos', feitas, total });
    const resultado = await emParalelo(turmas, 2, async (t) => {
      const m = {};
      const ler = async (chave, fn) => {
        try { m[chave] = { ...(await fn()), lidoEm: new Date().toISOString() }; } catch (e) {
          if (e instanceof SessaoExpirada) throw e;
          m[chave] = { erro: e.message };
        }
      };
      await ler('conteudo', async () => { const c = await leitor.conteudos(t, bimestre); return { pendentes: c.pendentes, registradas: c.registradas.length }; });
      await ler('frequencia', () => leitor.frequencia(t, bimestre));
      const prev = anteriores.get(P.chaveVinculo(t.id, t.disciplina))?.modulos;
      for (const { chave, tipo } of PLANOS) {
        const antigo = prev?.[chave];
        const recente = antigo && !antigo.erro && antigo.lidoEm && Date.now() - Date.parse(antigo.lidoEm) < VALIDADE_PLANOS_MS;
        if (!manual && recente && !m.conteudo?.erro) m[chave] = antigo;
        else await ler(chave, () => leitor.planos(t, tipo));
      }
      const n = contagens(m);
      const totalPendencias = Object.values(n).reduce((s, v) => s + (v ?? 0), 0);
      const reg = m.conteudo?.registradas ?? 0;
      const pend = m.conteudo?.pendentes?.length ?? 0;
      aoProgresso({ etapa: 'Lendo Conteúdo, Chamada e Planos', feitas: ++feitas, total });
      return {
        ...t,
        modulos: m, contagens: n, totalPendencias,
        // Campos de resumo usados nos cartões (Conteúdo Aplicado).
        pendentes: m.conteudo?.pendentes ?? [], registradas: reg,
        progresso: reg + pend ? reg / (reg + pend) : 1,
        status: m.conteudo?.erro ? 'erro' : totalPendencias > 0 ? 'pendente' : 'em-dia',
        erro: m.conteudo?.erro,
      };
    });

    const painel = { versao: 2, atualizadoEm: new Date().toISOString(), perfil: 'professor', bimestre, incluiu: [...(config.incluirDiversificada ? ['DIVERSIFICADA'] : []), ...(config.incluirAee ? ['AEE'] : [])], turmas: resultado };
    await armazenamento.gravarPainel(painel);
    await armazenamento.registrarHistorico(painel);
    return painel;
  });
}

/**
 * Painel da coordenação (perfil Unidade escolar): aulas sem conteúdo de todas as turmas da escola,
 * atribuídas a cada professor pelo horário dele.
 */
function sincronizarEscola(aoProgresso = () => {}) {
  return exclusivo(async () => {
    const { leitor } = await prepararSessao(aoProgresso);
    const perfil = await leitor.perfil();
    if (perfil !== 'escola') throw new Error('O perfil ativo no SGE é Servidor (professor). Troque para Unidade escolar no SGE e sincronize de novo.');
    const turmasEscola = await armazenamento.lerTurmasEscola();
    if (!turmasEscola.length) throw new Error('Ainda não conheço as turmas da escola. Abra o SGE pelo app, vá em Escola → Conteúdo Aplicado e volte aqui.');

    aoProgresso({ etapa: 'Lendo os professores', feitas: 0, total: null });
    const professores = await leitor.professoresDaEscola();
    const total = professores.length + turmasEscola.length;
    let feitas = 0;
    const passo = (etapa) => aoProgresso({ etapa, feitas: ++feitas, total });

    const vinculos = new Map();
    const diagnosticos = [];
    await emParalelo(professores, 2, async (p) => {
      try {
        const r = await leitor.vinculosDocente(p, turmasEscola);
        r.vinculos.forEach((v) => vinculos.set(P.chaveVinculo(v.turmaId, v.componente), v));
        diagnosticos.push(...r.diagnosticos);
      } catch (e) { if (e instanceof SessaoExpirada) throw e; diagnosticos.push({ professor: p.nome, tipo: 'erro', erro: e.message }); }
      passo('Lendo o horário de cada professor');
    });

    const turmas = await emParalelo(turmasEscola, 2, async (t) => {
      let item;
      try { item = { id: t.v, nome: t.t, pendencias: await leitor.pendenciasConteudoTurma(t.v, t.tp) }; }
      catch (e) { if (e instanceof SessaoExpirada) throw e; item = { id: t.v, nome: t.t, pendencias: [], erro: e.message }; }
      passo('Lendo o Conteúdo Aplicado das turmas');
      return item;
    });

    // Agrupa por professor → (turma, componente) → datas.
    const porProfessor = new Map();
    for (const t of turmas) {
      for (const p of t.pendencias) {
        const v = vinculos.get(P.chaveVinculo(t.id, p.disciplina));
        const prof = v?.professor ?? { id: '', nome: 'Professor não identificado' };
        if (!porProfessor.has(prof.id)) porProfessor.set(prof.id, { ...prof, itens: new Map() });
        const itens = porProfessor.get(prof.id).itens;
        const k = P.chaveVinculo(t.id, p.disciplina);
        if (!itens.has(k)) itens.set(k, { turmaId: t.id, turma: t.nome, componente: p.disciplina, datas: [] });
        const datas = itens.get(k).datas;
        if (!datas.includes(p.data)) datas.push(p.data);
      }
    }
    const listaProfessores = [...porProfessor.values()].map((p) => {
      const itens = [...p.itens.values()].map((i) => ({ ...i, datas: i.datas.sort() })).sort((a, b) => b.datas.length - a.datas.length || a.turma.localeCompare(b.turma));
      return { id: p.id, nome: p.nome, itens, dias: itens.reduce((s, i) => s + i.datas.length, 0) };
    }).sort((a, b) => (a.id === '') - (b.id === '') || b.dias - a.dias || a.nome.localeCompare(b.nome));
    const semPendencia = professores.filter((p) => !porProfessor.has(p.id)).map((p) => p.nome);

    const painel = {
      versao: 1, atualizadoEm: new Date().toISOString(),
      turmas: turmas.map((t) => ({ id: t.id, nome: t.nome, dias: new Set(t.pendencias.map((p) => `${p.data}|${p.disciplina}`)).size, erro: t.erro })),
      professores: listaProfessores, semPendencia, totalProfessores: professores.length,
      diagnosticos: diagnosticos.slice(0, 200),
    };
    await armazenamento.gravarCoordenacao(painel);
    return painel;
  });
}

module.exports = { sincronizar, sincronizarEscola, lerCache, SessaoExpirada, PerfilEscola };

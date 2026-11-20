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

// Duas chamadas do mesmo tipo viram uma só; tipos diferentes esperam a vez (a janela oculta é uma só).
const emAndamento = new Map();
let fila = Promise.resolve();
const exclusivo = (tipo, fn) => {
  if (emAndamento.has(tipo)) return emAndamento.get(tipo);
  const p = fila.catch(() => {}).then(fn).finally(() => emAndamento.delete(tipo));
  fila = p;
  emAndamento.set(tipo, p);
  return p;
};

/**
 * Painel do professor: Conteúdo, Chamada, Plano de aula, Plano de curso e PEI de cada turma.
 * @param {(p: {etapa: string, feitas: number, total: number|null}) => void} aoProgresso
 * @param {{manual?: boolean}} opcoes manual = relê também os planos
 */
function sincronizar(aoProgresso = () => {}, { manual = false } = {}) {
  return exclusivo('professor', async () => {
    const { leitor, config, inicial } = await prepararSessao(aoProgresso);
    if ((await leitor.perfil()) === 'escola') throw new PerfilEscola();

    aoProgresso({ etapa: 'Lendo suas turmas', feitas: 0, total: null });
    const { turmas } = await leitor.turmas(inicial.opcoesTurma);
    if (!turmas.length) throw new Error('Nenhuma turma sua foi encontrada no horário do SGE. Confira se o perfil ativo é Servidor (professor).');
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
 * Painel da coordenação (perfil Unidade escolar): pendências da escola inteira por professor,
 * atribuídas pelo horário de cada um. Conteúdo Aplicado sempre; Chamada e planos só se ligados nas
 * Configurações (são muitas consultas: uma ou mais por professor e componente).
 */
function sincronizarEscola(aoProgresso = () => {}) {
  return exclusivo('escola', async () => {
    const { leitor, config } = await prepararSessao(aoProgresso);
    const perfil = await leitor.perfil();
    if (perfil !== 'escola') throw new Error('O perfil ativo no SGE é Servidor (professor). Troque para Unidade escolar no SGE e sincronize de novo.');
    const turmasEscola = await armazenamento.lerTurmasEscola();
    if (!turmasEscola.length) throw new Error('Ainda não conheço as turmas da escola. Abra o SGE pelo app, vá em Escola → Conteúdo Aplicado e volte aqui.');

    aoProgresso({ etapa: 'Lendo os professores', feitas: 0, total: null });
    const professores = await leitor.professoresDaEscola();
    const nomeDaTurma = new Map(turmasEscola.map((t) => [t.v, t.t]));
    const detalhes = Boolean(config.coordChamada || config.coordPlanos);
    // Cada professor leva 1 consulta de horário; cada turma, 1 de Conteúdo. Os detalhes somam as consultas por vínculo (abaixo).
    let total = professores.length + turmasEscola.length;
    let feitas = 0;
    const passo = (etapa) => aoProgresso({ etapa, feitas: ++feitas, total });

    const vinculos = new Map();
    const diagnosticos = [];
    const comHorario = new Set(); // quem tem aulas no horário de hoje; o SGE também lista professores que já saíram da escola
    await emParalelo(professores, 2, async (p) => {
      try {
        const r = await leitor.vinculosDocente(p, turmasEscola);
        if (r.vinculos.length || r.diagnosticos.length) comHorario.add(p.id);
        r.vinculos.forEach((v) => vinculos.set(P.chaveVinculo(v.turmaId, v.componente), v));
        diagnosticos.push(...r.diagnosticos);
      } catch (e) { if (e instanceof SessaoExpirada) throw e; comHorario.add(p.id); diagnosticos.push({ professor: p.nome, tipo: 'erro', erro: e.message }); }
      passo('Lendo o horário de cada professor');
    });

    const turmas = await emParalelo(turmasEscola, 2, async (t) => {
      let item;
      try { item = { id: t.v, nome: t.t, pendencias: await leitor.pendenciasConteudoTurma(t.v, t.tp) }; }
      catch (e) { if (e instanceof SessaoExpirada) throw e; item = { id: t.v, nome: t.t, pendencias: [], erro: e.message }; }
      passo('Lendo o Conteúdo Aplicado das turmas');
      return item;
    });

    // Um item por (turma, componente): vem do horário dos professores e das aulas sem conteúdo.
    const naoIdentificado = { id: '', nome: 'Professor não identificado' };
    const itens = new Map();
    const garantir = (turmaId, componente, professor) => {
      const k = P.chaveVinculo(turmaId, componente);
      if (!itens.has(k)) itens.set(k, { turmaId, turma: nomeDaTurma.get(turmaId) ?? turmaId, componente, professor: professor ?? naoIdentificado, datas: [], chamada: null, planos: null });
      return itens.get(k);
    };
    for (const v of vinculos.values()) garantir(v.turmaId, v.componente, v.professor);
    for (const t of turmas) {
      for (const p of t.pendencias) {
        const it = garantir(t.id, p.disciplina, vinculos.get(P.chaveVinculo(t.id, p.disciplina))?.professor);
        if (!it.datas.includes(p.data)) it.datas.push(p.data);
      }
    }

    // Bimestres: a interface mostra por padrão o atual e deixa escolher o 1º, 2º, 3º, 4º ou o ano inteiro.
    let bimestres = [];
    try { bimestres = await leitor.bimestres(turmasEscola[0].v); } catch (e) { if (e instanceof SessaoExpirada) throw e; }
    const hoje = P.hojeIso();
    const bimestre = bimestres.find((b) => hoje >= b.inicio && hoje <= b.fim) ?? null;

    if (detalhes) {
      const lista = [...itens.values()].filter((it) => it.professor.id);
      total += lista.length; // os detalhes entram na barra de progresso
      aoProgresso({ etapa: 'Lendo Chamada e Planos', feitas, total });
      await emParalelo(lista, 2, async (it) => {
        const t = { id: it.turmaId, disciplina: it.componente };
        const tentar = async (fn) => { try { return await fn(); } catch (e) { if (e instanceof SessaoExpirada) throw e; return { erro: e.message }; } };
        if (config.coordChamada) {
          const r = await tentar(() => leitor.frequencia(t, bimestre));
          it.chamada = r.erro ? null : r.datas;
          if (r.erro) it.erroChamada = r.erro;
        }
        if (config.coordPlanos) {
          await tentar(() => leitor.conteudos(t, bimestre)); // o calendário de aulas é a base da cobertura dos planos
          it.planos = {};
          for (const { chave, tipo } of PLANOS) {
            const r = await tentar(() => leitor.planos(t, tipo));
            it.planos[chave] = r.erro || r.indisponivel ? null : r.pendentes ?? 0;
          }
        }
        passo('Lendo Chamada e Planos');
      });
    }

    const soma = (o) => Object.values(o ?? {}).reduce((s, v) => s + (v ?? 0), 0);
    const pendenciasDe = (it) => it.datas.length + (it.chamada?.length ?? 0) + soma(it.planos);
    const porProfessor = new Map();
    for (const it of itens.values()) {
      if (!pendenciasDe(it)) continue;
      const { professor, ...resto } = it;
      if (!porProfessor.has(professor.id)) porProfessor.set(professor.id, { ...professor, itens: [] });
      porProfessor.get(professor.id).itens.push({ ...resto, datas: resto.datas.sort() });
    }
    const listaProfessores = [...porProfessor.values()].map((p) => {
      const lista = p.itens.sort((a, b) => pendenciasDe(b) - pendenciasDe(a) || a.turma.localeCompare(b.turma));
      return {
        id: p.id, nome: p.nome, itens: lista,
        dias: lista.reduce((s, i) => s + i.datas.length, 0),
        chamadas: lista.reduce((s, i) => s + (i.chamada?.length ?? 0), 0),
        planos: lista.reduce((s, i) => s + soma(i.planos), 0),
        total: lista.reduce((s, i) => s + pendenciasDe(i), 0),
      };
    }).sort((a, b) => (a.id === '') - (b.id === '') || b.total - a.total || a.nome.localeCompare(b.nome));
    const ativos = professores.filter((p) => comHorario.has(p.id));
    const semHorario = professores.filter((p) => !comHorario.has(p.id)).map((p) => p.nome);
    const semPendencia = ativos.filter((p) => !porProfessor.has(p.id)).map((p) => p.nome);

    const painel = {
      versao: 4, atualizadoEm: new Date().toISOString(), bimestre, bimestres, detalhes: { chamada: Boolean(config.coordChamada), planos: Boolean(config.coordPlanos) },
      turmas: turmas.map((t) => ({ id: t.id, nome: t.nome, dias: new Set(t.pendencias.map((p) => `${p.data}|${p.disciplina}`)).size, erro: t.erro })),
      professores: listaProfessores, semPendencia, semHorario, totalProfessores: ativos.length,
      diagnosticos: diagnosticos.slice(0, 200),
    };
    await armazenamento.gravarCoordenacao(painel);
    const { visaoPeriodo } = await import('./escolaVisao.mjs');
    await armazenamento.registrarHistoricoCoordenacao(visaoPeriodo(painel, 'bimestre')); // o histórico acompanha o bimestre atual
    return painel;
  });
}

/** Aulas de hoje e de amanhã do professor (quadro de horário do SGE). A interface cruza com o painel. */
function agenda() {
  return exclusivo('agenda', async () => {
    const sessao = session.fromPartition(PARTICAO);
    const leitor = criarLeitor(criarCliente(sessao));
    const dia = (n) => { const d = new Date(); d.setDate(d.getDate() + n); return P.hojeIso(d); };
    const dias = [];
    for (const n of [0, 1]) dias.push({ data: dia(n), aulas: await leitor.aulasDoDia(dia(n)) });
    const resultado = { geradoEm: new Date().toISOString(), dias };
    await armazenamento.gravarAgenda(resultado);
    return resultado;
  });
}

module.exports = { agenda, sincronizar, sincronizarEscola, lerCache, SessaoExpirada, PerfilEscola };

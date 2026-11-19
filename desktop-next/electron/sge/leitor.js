'use strict';
/**
 * Leitura do SGE: porta de `SgeReadClient.turmas()`, `grade()`, `conteudos()` e `janelaDoFiltro()`.
 * Rotas e parâmetros são exatamente os do Android, para os dois apps verem os mesmos dados.
 */
const P = require('./parsing');

function criarLeitor(cliente, { incluir = new Set() } = {}) {
  const grades = new Map();
  const janelas = new Map();
  let bimestreZero = null;

  /** Turmas do professor = quadro de horário × opções de turma (HTML ou lidas pela janela oculta). */
  async function turmas(opcoesDaJanela = []) {
    const home = await cliente.requisicao('/', {}, { ajax: false });
    const { professor, tipo, perfilEscola } = P.identidadeHorario(await cliente.requisicao('/horariodeauladoprofessor'));
    const hoje = P.paraBr(P.hojeIso());
    const quadro = await cliente.json('/HorarioDeAulaDoProfessor/QuadroHorarioProfessor', {
      dataReferencia: hoje, tipo, colaboradorId: professor, escolaId: '', turno: '', somenteConflitos: 'false',
    });
    let opcoes = P.opcoesTurma(home);
    if (!opcoes.length) opcoes = opcoesDaJanela.map((o) => ({ valor: o.v, desc: P.normalizar(o.d), escola: o.e, tipo: o.t }));
    if (!opcoes.length) throw new Error('O SGE não listou as turmas na página inicial. Entre no SGE e tente sincronizar de novo.');
    return { perfilEscola, turmas: P.turmasDoQuadro(quadro.html, opcoes, incluir) };
  }

  /** Código do componente curricular (GradeEscolarId) da turma. */
  async function grade(t) {
    const chave = `${t.id}/${t.disciplina}`;
    if (!grades.has(chave)) {
      const { grades: lista = [] } = await cliente.json('/PlanejamentoTurma/CarregueGradesEscolares', { turmaId: t.id });
      const achada = lista.filter((g) => String(g.Descricao || '').trim().toLowerCase() === t.disciplina.toLowerCase());
      if (achada.length !== 1) throw new Error(`Componente não identificado: ${t.disciplina}`);
      grades.set(chave, String(achada[0].Codigo));
    }
    return grades.get(chave);
  }

  /** Datas de um bimestre pedidas ao SGE (null se não fizer sentido como bimestre: 30 a 140 dias). */
  async function janelaDoBimestre(param, turmaId) {
    try {
      const j = await cliente.json('/NotasFaltas/ObtenhaInicioFimParametros', { turmaId, bimestre: param, mes: '' });
      const inicio = P.dataIso(j.dataInicio); const fim = P.dataIso(j.dataFim);
      const dias = (Date.parse(fim) - Date.parse(inicio)) / 86_400_000;
      return fim >= inicio && dias >= 30 && dias <= 140 ? { inicio, fim } : null;
    } catch { return null; }
  }

  /** Bimestre que contém hoje (a numeração 0- ou 1-based é detectada uma vez, como no Android). */
  async function bimestreAtual(turmaId) {
    if (bimestreZero === null) bimestreZero = (await janelaDoBimestre('0', turmaId)) !== null;
    const hoje = P.hojeIso();
    for (let n = 1; n <= 4; n++) {
      const chave = `${turmaId}|${n}`;
      if (!janelas.has(chave)) janelas.set(chave, await janelaDoBimestre(String(bimestreZero ? n - 1 : n), turmaId));
      const j = janelas.get(chave);
      if (j && hoje >= j.inicio && hoje <= j.fim) return { numero: n, ...j };
    }
    return null;
  }

  /** Conteúdo Aplicado da turma no bimestre atual. */
  async function conteudos(t, janela) {
    const j = await cliente.json('/Conteudo/CarregarDadosConteudoAplicado', {
      TurmaId: t.id, TipoDeTurma: '0', GradeEscolarId: await grade(t),
      StatusConteudoAplicado: '', DataInicio: '', DataFinal: '',
    });
    return P.conteudos(j.html, t.disciplina, P.hojeIso(), janela);
  }

  return { turmas, grade, bimestreAtual, conteudos };
}

module.exports = { criarLeitor };

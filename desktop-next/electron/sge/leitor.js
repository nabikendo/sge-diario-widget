'use strict';
/**
 * Leitura do SGE: porta de `SgeReadClient.turmas()`, `grade()`, `conteudos()` e `janelaDoFiltro()`.
 * Rotas e parâmetros são exatamente os do Android, para os dois apps verem os mesmos dados.
 */
const cheerio = require('cheerio');
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

  const calendarios = new Map(); // HTML do Conteúdo Aplicado, reaproveitado na cobertura dos planos

  /** Conteúdo Aplicado da turma no bimestre atual. */
  async function conteudos(t, janela) {
    const j = await cliente.json('/Conteudo/CarregarDadosConteudoAplicado', {
      TurmaId: t.id, TipoDeTurma: '0', GradeEscolarId: await grade(t),
      StatusConteudoAplicado: '', DataInicio: '', DataFinal: '',
    });
    calendarios.set(`${t.id}/${t.disciplina}`, j.html);
    return P.conteudos(j.html, t.disciplina, P.hojeIso(), janela);
  }

  const limitesLetivos = new Map();
  async function limites(turmaId) {
    if (!limitesLetivos.has(turmaId)) {
      const j = await cliente.json('/NotasFaltas/ObtenhaInicioFimParametros', { turmaId, bimestre: '', mes: '' });
      limitesLetivos.set(turmaId, { inicio: P.dataIso(j.dataInicio), fim: P.dataIso(j.dataFim) });
    }
    return limitesLetivos.get(turmaId);
  }

  /** Frequência não concluída (filtro oficial do SGE) no bimestre atual. Porta de `frequencia()`. */
  async function frequencia(t, janela) {
    const letivo = await limites(t.id);
    const hoje = P.hojeIso();
    const fim = letivo.fim < hoje ? letivo.fim : hoje;
    if (fim < letivo.inicio) return { datas: [], detalhe: 'Período letivo ainda não iniciado.' };
    const ini = janela && janela.inicio > letivo.inicio ? janela.inicio : letivo.inicio;
    const ate = janela && janela.fim < fim ? janela.fim : fim;
    if (ate < ini) return { datas: [], detalhe: 'Ainda não há aulas neste bimestre até hoje.' };
    const r = await cliente.json('/NotasFaltas/ListarFrequencia', {
      turmaId: t.id, gradeEscolarId: await grade(t), bimestre: '', mes: '',
      dataInicial: P.paraBr(ini), dataFinal: P.paraBr(ate), frequenciaNaoConcluida: 'true',
    }, { post: true });
    if (!String(r.html || '').trim()) {
      const msg = ['mensagem', 'Mensagem', 'message', 'erro'].map((k) => r[k]).find((v) => v && String(v).trim());
      if (msg && /nenhum|não há|nao ha|sem registro|não encontr|não (foram|foi) encontr/i.test(msg)) return { datas: [], detalhe: `O SGE informou: ${String(msg).slice(0, 120)}` };
      throw new Error(`O SGE respondeu sem a tabela de frequência${msg ? `: ${String(msg).slice(0, 120)}` : ''}`);
    }
    return { datas: P.frequencia(r.html, ate, ini) };
  }

  /** Planos de aula, de curso e PEI: registros existentes + documentos ausentes (cobertura). Porta de `planos()`/`ausentes()`. */
  async function planos(t, tipoNome) {
    const pagina = cheerio.load(await cliente.requisicao('/planejamentoturma', { turmaId: t.id }));
    const opcoes = pagina('#TipoDeRegistroPedagogicoId option').toArray().map((o) => pagina(o));
    const igual = opcoes.filter((o) => o.text().trim().toLowerCase() === tipoNome.toLowerCase());
    const prefixo = opcoes.filter((o) => o.text().trim().toLowerCase().startsWith(tipoNome.toLowerCase()));
    const escolhido = igual.length === 1 ? igual[0] : prefixo.length === 1 ? prefixo[0] : null;
    if (!escolhido) return { indisponivel: true, detalhe: `Tipo "${tipoNome}" não existe para esta turma no SGE.` };
    const tipo = escolhido.attr('value');
    const r = await cliente.json('/PlanejamentoTurma/PartialPlanejamentos', {
      TurmaId: t.id, TipoDeRegistroPedagogicoId: tipo, GradeEscolarId: await grade(t),
      AtividadeDeAEEId: '', ModuloUnidadeCurricularId: '', Situacao: '', Periodos: '[]',
    });
    const existentes = P.planos(r.html);
    try {
      const { ausentes, semDatas } = await ausentesPlanos(t, tipo, r.html);
      return { ...existentes, ausentes, pendentes: existentes.pendentes + ausentes, parcial: semDatas.length > 0, semDatas };
    } catch (e) {
      if (e.name === 'SessaoExpirada') throw e;
      return { ...existentes, ausentes: null, coberturaErro: e.message };
    }
  }

  async function ausentesPlanos(t, tipo, listaHtml) {
    const gradeId = await grade(t);
    const form = cheerio.load(await cliente.requisicao('/PlanejamentoTurma/Editar/0', { tipoDeTurma: '0', tipoDeRegistroPedagogicoId: tipo, gradeEscolarId: gradeId, turmaId: t.id }));
    const config = await cliente.json('/PlanejamentoTurma/CarregarConfiguracoes', { gradeEscolarId: gradeId, turmaId: t.id, tipoDeRegistroPedagogicoId: tipo, componentesAssociados: '[]' });
    if (!('exibirPeriodicidade' in config) || config.exibirPeriodicidade) throw new Error('A periodicidade precisa ser definida pelo professor');
    const periodicidade = (form('#TipoPeriodicidade').attr('value') || '').trim();
    if (!periodicidade) throw new Error('Periodicidade não identificada');
    const { periodos = [] } = await cliente.json('/PlanejamentoTurma/CarreguePeriodos', { GradeEscolarId: gradeId, Periodicidade: '', TurmaId: t.id, TipoDeRegistroPedagogicoId: tipo });
    const calendario = calendarios.get(`${t.id}/${t.disciplina}`);
    if (!calendario) throw new Error('Calendário de aulas não verificado');
    const meses = P.mesesComAula(calendario);
    const letivo = await limites(t.id);
    const hoje = P.hojeIso();
    const lista = cheerio.load(listaHtml);
    const tabela = lista('#tabela_planejamentos');
    const cab = tabela.find('thead th').toArray().map((th) => lista(th).text().trim().toLowerCase());
    const iPeriodo = cab.indexOf('período');
    if (iPeriodo < 0) throw new Error('Coluna Período ausente');
    const existentes = new Set(tabela.find('tbody tr').toArray().map((tr) => lista(tr).find('td').eq(iPeriodo).text().trim().toLowerCase()).filter(Boolean));
    const pei = form('#AlunosTurmaId').length > 0;

    // PEI: alunos já cobertos em cada período (lidos dos registros existentes).
    const atribuicoes = new Map();
    if (pei) {
      const ids = new Set(tabela.find('.checkbox_item[data-id]').toArray().map((el) => lista(el).attr('data-id')));
      tabela.find('[onclick]').each((_, el) => { const m = /carregarPaginaEditarPlanejamento\((\d+)\)/.exec(lista(el).attr('onclick')); if (m) ids.add(m[1]); });
      for (const id of ids) {
        const reg = cheerio.load(await cliente.requisicao('/PlanejamentoTurma/Editar', { id, acessoPelaTurma: 'true', tipoDeTurma: '0' }));
        const alunos = reg('#AlunosTurmaId option[selected]').toArray().map((o) => reg(o).attr('value')).filter(Boolean);
        let pers = reg('#Periodo option[selected]').toArray().map((o) => reg(o).attr('value'));
        if (!pers.length) pers = reg('input#Periodo[value]').toArray().map((o) => reg(o).attr('value')).filter(Boolean);
        if (!alunos.length || !pers.length) throw new Error('Vínculos do PEI não identificados');
        for (const p of pers) { if (!atribuicoes.has(p)) atribuicoes.set(p, new Set()); alunos.forEach((a) => atribuicoes.get(p).add(a)); }
      }
    }

    let ausentes = 0;
    const semDatas = [];
    for (const periodo of periodos) {
      const codigo = String(periodo.Codigo);
      const descricao = String(periodo.Descricao ?? '');
      let janela = P.janelaOuNull(descricao, codigo, Number(letivo.inicio.slice(0, 4)));
      if (!janela) { const n = P.numeroBimestre(descricao); if (n) janela = await janelaNumero(n, t.id); }
      if (!janela) { semDatas.push(`${descricao.trim().slice(0, 30)} (cód. ${codigo})`); continue; }
      if (janela.inicio > hoje || janela.inicio > letivo.fim || janela.fim < letivo.inicio) continue;
      const mesHoje = hoje.slice(0, 7);
      const temAula = [...meses].some((m) => m >= janela.inicio.slice(0, 7) && m <= janela.fim.slice(0, 7) && m <= mesHoje);
      if (!temAula) continue;
      if (!pei) { if (!existentes.has(descricao.trim().toLowerCase())) ausentes++; continue; }
      const { alunos = [] } = await cliente.json('/PlanejamentoTurma/ObtenhaAlunosElegiveis', {
        planejamentoId: '0', turmaId: t.id, tipoDeRegistroPedagogicoId: tipo, gradeEscolarId: gradeId, periodicidade, periodos: codigo,
      });
      const cobertos = atribuicoes.get(codigo) ?? new Set();
      ausentes += new Set(alunos.map((a) => String(a.Codigo)).filter((a) => !cobertos.has(a))).size;
    }
    return { ausentes, semDatas };
  }

  async function janelaNumero(n, turmaId) {
    if (bimestreZero === null) bimestreZero = (await janelaDoBimestre('0', turmaId)) !== null;
    return janelaDoBimestre(String(bimestreZero ? n - 1 : n), turmaId);
  }

  /* ---------- Coordenação (perfil Unidade escolar) ---------- */
  async function perfil() { return P.perfilAtivo(await cliente.requisicao('/horariodeauladoprofessor')); }

  async function professoresDaEscola() {
    const lista = P.professores(await cliente.requisicao('/horariodeauladoprofessor'));
    if (!lista.length) throw new Error('O SGE não retornou a lista de professores do perfil Unidade escolar.');
    return lista;
  }

  async function vinculosDocente(professor, turmasEscola) {
    const formResp = await cliente.requisicao('/horariodeauladoprofessor');
    const form = cheerio.load(P.htmlDaResposta(formResp));
    const escola = (form('#EscolaId').attr('value') || form('#EscolaId option[selected]').attr('value') || '').trim();
    const q = await cliente.json('/HorarioDeAulaDoProfessor/QuadroHorarioProfessor', {
      dataReferencia: P.paraBr(P.hojeIso()), tipo: P.tipoHorario(formResp), colaboradorId: professor.id,
      escolaId: escola, turno: '', somenteConflitos: 'false',
    });
    return P.analisarVinculos(q.html, professor, turmasEscola);
  }

  /** Aulas sem conteúdo de uma turma, em todos os componentes (uma consulta por turma). */
  async function pendenciasConteudoTurma(turmaId, tipo) {
    const j = await cliente.json('/Conteudo/CarregarDadosConteudoAplicado', {
      TurmaId: turmaId, TipoDeTurma: tipo || '0', GradeEscolarId: '', StatusConteudoAplicado: '', DataInicio: '', DataFinal: '',
    });
    return P.pendenciasCoordenacao(j.html, P.hojeIso());
  }

  return { perfil, turmas, grade, bimestreAtual, conteudos, frequencia, planos, professoresDaEscola, vinculosDocente, pendenciasConteudoTurma };
}

module.exports = { criarLeitor };

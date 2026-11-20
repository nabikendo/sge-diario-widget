'use strict';
/**
 * Parsing puro (sem rede): porta de trechos de `SgeReadClient.kt` / `SgeParsing`.
 * Jsoup → cheerio: os seletores CSS são os mesmos, então a tradução é quase linha a linha.
 * Datas circulam como texto ISO (AAAA-MM-DD): comparar strings ISO equivale a comparar datas.
 */
const cheerio = require('cheerio');

const normalizar = (x = '') => x.replace(/ /g, ' ').trim().replace(/\s+/g, ' ').toLowerCase();
/** Como `normalizarSge` do Android: também tira acentos. */
const normalizarSge = (x = '') => normalizar(x.normalize('NFD').replace(/\p{M}+/gu, ''));
const chaveVinculo = (turmaId, componente) => `${turmaId}|${normalizarSge(componente)}`;

/** "dd/mm/aaaa" em qualquer lugar do texto → "aaaa-mm-dd". */
function dataIso(texto = '') {
  const m = /(\d{2})\/(\d{2})\/(\d{4})/.exec(texto);
  if (!m) throw new Error('Data ausente');
  return `${m[3]}-${m[2]}-${m[1]}`;
}
const paraBr = (iso) => iso.split('-').reverse().join('/');
const hojeIso = (agora = new Date()) =>
  `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, '0')}-${String(agora.getDate()).padStart(2, '0')}`;

/** Categoria pelo nome da turma: 1–2 dígitos = Escolarização; 3 = Diversificada; 4 = AEE. */
function categoriaTurma(nome) {
  const digitos = /^(\d+)\./.exec(nome.trim())?.[1]?.length ?? 0;
  return digitos === 3 ? 'DIVERSIFICADA' : digitos === 4 ? 'AEE' : 'ESCOLARIZACAO';
}

/** Algumas instalações embrulham o fragmento em JSON ({html} ou {data}). */
function htmlDaResposta(resposta) {
  try {
    const j = JSON.parse(resposta);
    return (j.html && String(j.html)) || (j.data && String(j.data)) || resposta;
  } catch { return resposta; }
}

/** Opções do seletor #TurmaContextoId (HTML da página inicial). */
function opcoesTurma(homeHtml) {
  const $ = cheerio.load(homeHtml);
  return $('#TurmaContextoId option').toArray()
    .map((el) => $(el))
    .filter((o) => (o.attr('value') || '').trim())
    .map((o) => ({
      valor: o.attr('value'),
      desc: normalizar(o.attr('data-turmadescricao') || o.text()),
      escola: o.attr('data-escoladescricao') || '',
      tipo: o.attr('data-tipodaturmaenum') || '',
    }));
}

function valorCampo($, el) {
  if (!el.length) return '';
  return (el.attr('value') || el.find('option[selected]').attr('value') || el.find("option[value]:not([value=''])").attr('value') || '').trim();
}

/** Identidade do professor e tipo de horário, lidos do fragmento /horariodeauladoprofessor. */
function identidadeHorario(horarioResposta) {
  const $ = cheerio.load(htmlDaResposta(horarioResposta));
  if (!$('#idProfessor').length && !$('#ProfessorId').length)
    throw new Error('A página de horário não retornou o formulário esperado (resposta AJAX do SGE).');
  const prof = $('#idProfessor');
  const professor = (prof.attr('value') || prof.attr('data-id') || '').trim()
    || valorCampo($, $('#ProfessorId'))
    || ($("[name='colaboradorId'], [name='ColaboradorId']").first().attr('value') || '').trim();
  if (!professor) throw new Error('O SGE não informou o identificador do professor no horário.');
  const tipo = valorCampo($, $('#Tipo')) || ($("[name='Tipo'] option[selected]").first().attr('value') || '').trim();
  if (!tipo) throw new Error('O SGE não informou o tipo de horário selecionado.');
  return { professor, tipo, perfilEscola: $('#ehPerfilEscola').attr('value')?.toLowerCase() === 'true' };
}

/**
 * Cruza o quadro de horário (linhas "07:00 às 07:50 | disciplina | turma") com as opções de turma.
 * Padrão: só Escolarização. Diversificada e AEE entram se estiverem em `incluir`.
 */
function turmasDoQuadro(quadroHtml, opcoes, incluir = new Set()) {
  const $ = cheerio.load(quadroHtml);
  const achadas = new Map();
  $('tr').each((_, tr) => {
    const c = $(tr).find('td');
    if (c.length !== 3 || !$(c[0]).text().includes('às')) return;
    const nome = $(c[2]).text().trim();
    const alvo = normalizar(nome);
    const exatas = opcoes.filter((o) => o.desc === alvo);
    const parecidas = exatas.length ? exatas : opcoes.filter((o) => o.desc && (o.desc.startsWith(alvo) || alvo.startsWith(o.desc)));
    if (!parecidas.length) throw new Error(`Turma "${nome}" do quadro não encontrada na lista de turmas do SGE`);
    const categoria = categoriaTurma(nome);
    const escolarizacao = parecidas.filter((o) => o.tipo === '0' || !o.tipo);
    const escolhidas = categoria === 'ESCOLARIZACAO' ? escolarizacao : incluir.has(categoria) ? parecidas : [];
    if (!escolhidas.length) return;
    const ids = [...new Set(escolhidas.map((o) => o.valor.split('_')[0]))];
    if (ids.length !== 1) throw new Error(`Identificação ambígua da turma ${nome}`);
    const turma = { id: ids[0], nome, escola: escolhidas[0].escola, disciplina: $(c[1]).text().trim(), tipo: escolhidas[0].tipo };
    achadas.set(`${turma.id}/${turma.disciplina}`, turma);
  });
  const lista = [...achadas.values()];
  if (!lista.length) throw new Error('Horário sem turmas para a data de referência');
  return lista.sort((a, b) => a.escola.localeCompare(b.escola) || a.nome.localeCompare(b.nome) || a.disciplina.localeCompare(b.disciplina));
}

/**
 * Mapa de Conteúdo Aplicado: dia vermelho com aula da disciplina sem ícone de editar = conteúdo pendente.
 * Porta direta de `SgeParsing.conteudos`.
 * @returns {{quantidade:number, pendentes:string[], registradas:string[], ate:string}}
 */
function conteudos(html, disciplina, hoje = hojeIso(), janela = null) {
  const ate = janela && janela.fim < hoje ? janela.fim : hoje;
  const desde = janela?.inicio ?? '0000-01-01';
  const $ = cheerio.load(html);
  if (!$('.calendario .semana').length) throw new Error('Calendário ausente');
  const mesmaDisciplina = (linha$, linha) => linha$(linha).find('td').first().text().trim().toLowerCase() === disciplina.toLowerCase();
  const tituloDia = (el) => $(el).attr('data-original-title') || $(el).attr('title') || '';

  const pendentes = new Set();
  $('.calendario .semana .vermelho').each((_, dia) => {
    const d = dataIso(tituloDia(dia));
    const popup = cheerio.load($(dia).attr('data-content') || '');
    if (!popup('tr td:first-child').length) throw new Error('Componente ausente no detalhe');
    const linhas = popup('tr').toArray().filter((tr) => mesmaDisciplina(popup, tr));
    if (d > ate || d < desde || !linhas.length) return;
    if (linhas.some((tr) => !popup(tr).find('i.fa-edit').length)) pendentes.add(d);
  });

  const registradas = new Set();
  $('.calendario .semana [data-content]').each((_, dia) => {
    let d; try { d = dataIso(tituloDia(dia)); } catch { return; }
    if (d < desde || d > ate) return;
    const popup = cheerio.load($(dia).attr('data-content') || '');
    if (popup('tr').toArray().some((tr) => mesmaDisciplina(popup, tr) && popup(tr).find('i.fa-edit').length)) registradas.add(d);
  });

  return { quantidade: pendentes.size, pendentes: [...pendentes].sort(), registradas: [...registradas].sort(), ate };
}

/* ------------------------------------------------------------------ */
/* Frequência, planos e coordenação (porta de SgeParsing / SgeDocentesParsing) */

/** Dias com frequência não concluída (filtro oficial do SGE), dentro da janela. */
function frequencia(html, ate, desde = null) {
  const $ = cheerio.load(html);
  const tabela = $('#gridFrequencia');
  if (!tabela.length) throw new Error('Grade de frequência ausente');
  const datas = new Set();
  tabela.find('thead .cabecalhoMes').each((_, el) => {
    const d = dataIso($(el).attr('title') || '');
    if (d <= ate && (!desde || d >= desde)) datas.add(d);
  });
  return [...datas].sort();
}

const SITUACOES = ['não enviado', 'aguardando avaliação', 'aprovado', 'reprovado'];
/** Planos/PEI existentes: pendentes = Não Enviado + Reprovado. */
function planos(html) {
  const $ = cheerio.load(html);
  const tabela = $('#tabela_planejamentos');
  if (!tabela.length) throw new Error('Grade de planos ausente');
  const i = tabela.find('thead th').toArray().findIndex((th) => normalizar($(th).text()) === 'situação');
  if (i < 0) throw new Error('Coluna Situação ausente');
  const situacoes = tabela.find('tbody tr').toArray()
    .map((tr) => $(tr).find('td').eq(i)).filter((td) => td.length).map((td) => normalizar(td.text()));
  if (!situacoes.every((s) => SITUACOES.includes(s))) throw new Error('Situação desconhecida');
  return {
    total: situacoes.length,
    pendentes: situacoes.filter((s) => s === 'não enviado' || s === 'reprovado').length,
    aguardando: situacoes.filter((s) => s === 'aguardando avaliação').length,
    aprovados: situacoes.filter((s) => s === 'aprovado').length,
  };
}

const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
const ABREV = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const mesIso = (ano, mes) => `${ano}-${String(mes).padStart(2, '0')}`;

/** Meses do calendário de Conteúdo Aplicado que têm aulas a registrar ("AAAA-MM"). */
function mesesComAula(html) {
  const $ = cheerio.load(html);
  const celulas = $('.calendario .semana [data-original-title], .calendario .semana [title]');
  if (!celulas.length) throw new Error('Datas do calendário ausentes');
  const ano = dataIso(celulas.first().attr('data-original-title') || celulas.first().attr('title')).slice(0, 4);
  const blocos = $('.calendario .mes');
  if (!blocos.length) throw new Error('Meses do calendário ausentes');
  const meses = new Set();
  blocos.each((_, m) => {
    const numero = MESES.indexOf(normalizar($(m).find('.mes-titulo').text())) + 1;
    if (numero < 1) throw new Error('Mês do calendário não reconhecido');
    const resumo = $(m).find('.mes-subtitulo').first().text().trim();
    const total = /(\d+)\s+de\s+(\d+)\s+registrados/i.exec(resumo)?.[2];
    if (total != null) { if (Number(total) > 0) meses.add(mesIso(ano, numero)); }
    else if (!/nenhum registro a ser feito|sem dias letivos/i.test(resumo)) throw new Error('Resumo mensal do calendário não reconhecido');
  });
  return meses;
}

const ORDINAIS = { primeiro: 1, segundo: 2, terceiro: 3, quarto: 4, i: 1, ii: 2, iii: 3, iv: 4 };
/** "1º Bimestre", "Bimestre 1", "II Bim." → 1..4 (ou null). */
function numeroBimestre(descricao) {
  const antes = /(?<![\p{L}\d])(\d|primeiro|segundo|terceiro|quarto|iv|iii|ii|i)\s*[º°ªo]?\s*[-.]?\s*(?:bimestre|bim)(?![\p{L}])/iu;
  const depois = /(?<![\p{L}])(?:bimestre|bim)\.?\s*[-:nº°.]*\s*(\d|primeiro|segundo|terceiro|quarto|iv|iii|ii|i)(?![\p{L}\d])/iu;
  const bruto = (antes.exec(descricao) ?? depois.exec(descricao))?.[1]?.toLowerCase();
  if (!bruto) return null;
  const n = Number(bruto) || ORDINAIS[bruto];
  return n >= 1 && n <= 4 ? n : null;
}

const isoDe = (d) => `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
function mesInteiro(ano, mes) {
  if (!(mes >= 1 && mes <= 12)) return null;
  return { inicio: isoDe(new Date(Date.UTC(ano, mes - 1, 1))), fim: isoDe(new Date(Date.UTC(ano, mes, 0))) };
}

/** Janela de datas de um período de planejamento; null quando o formato não é reconhecido (nunca estima). */
function janelaOuNull(descricao, codigo, ano) {
  const texto = descricao.trim().toLowerCase();
  const anoTexto = Number(/\b(20\d{2})\b/.exec(texto)?.[1]) || null;
  let mes = MESES.findIndex((m) => texto.startsWith(m)) + 1;
  if (!mes) mes = ABREV.findIndex((m) => texto.startsWith(m)) + 1;
  if (mes) return mesInteiro(anoTexto ?? ano, mes);
  const semana = /(\d{1,2})\s*[ªº°a]?\s*semana(?:\s+de)?\s*(20\d{2})?/.exec(texto);
  if (semana) {
    // Semana ISO 8601 (segunda a domingo).
    const a = Number(semana[2]) || anoTexto || ano;
    const jan4 = new Date(Date.UTC(a, 0, 4));
    const segunda1 = new Date(jan4.getTime() - ((jan4.getUTCDay() + 6) % 7) * 86_400_000);
    const ini = new Date(segunda1.getTime() + (Number(semana[1]) - 1) * 7 * 86_400_000);
    return { inicio: isoDe(ini), fim: isoDe(new Date(ini.getTime() + 6 * 86_400_000)) };
  }
  const sem = /(1|2|primeiro|segundo)\s*[º°o]?\s*semestre/.exec(texto);
  if (sem) return ['1', 'primeiro'].includes(sem[1]) ? { inicio: `${ano}-01-01`, fim: `${ano}-06-30` } : { inicio: `${ano}-07-01`, fim: `${ano}-12-31` };
  const completas = [...descricao.matchAll(/(\d{2})\/(\d{2})\/(\d{4})/g)].map((m) => `${m[3]}-${m[2]}-${m[1]}`);
  if (completas.length >= 2) return completas[0] <= completas[1] ? { inicio: completas[0], fim: completas[1] } : { inicio: completas[1], fim: completas[0] };
  if (!completas.length) {
    let m;
    if ((m = /\b(\d{1,2})\/(20\d{2})\b/.exec(descricao))) return mesInteiro(Number(m[2]), Number(m[1]));
    if ((m = /\b(20\d{2})-(\d{2})\b/.exec(descricao))) return mesInteiro(Number(m[1]), Number(m[2]));
    if ((m = /^(20\d{2})(0[1-9]|1[0-2])$/.exec(String(codigo).trim()))) return mesInteiro(Number(m[1]), Number(m[2]));
    const curtas = [...descricao.matchAll(/\b(\d{1,2})\/(\d{1,2})\b/g)];
    if (curtas.length === 2) {
      const a = isoDe(new Date(Date.UTC(ano, Number(curtas[0][2]) - 1, Number(curtas[0][1]))));
      let b = new Date(Date.UTC(ano, Number(curtas[1][2]) - 1, Number(curtas[1][1])));
      if (isoDe(b) < a) b = new Date(Date.UTC(ano + 1, b.getUTCMonth(), b.getUTCDate()));
      return { inicio: a, fim: isoDe(b) };
    }
  }
  return null;
}

/**
 * Coordenação: dias vermelhos, em todos os componentes da turma. No perfil Unidade escolar o detalhe do dia lista cada aula
 * com um lápis (fa-edit) quando há conteúdo registrado; a linha sem lápis é a pendência (confirmado no SGE real).
 */
function pendenciasCoordenacao(html, hoje = hojeIso()) {
  const $ = cheerio.load(html);
  if (!$('.calendario .semana').length) throw new Error('Calendário ausente');
  const vistos = new Set();
  const lista = [];
  $('.calendario .semana .vermelho').each((_, dia) => {
    const d = dataIso($(dia).attr('data-original-title') || $(dia).attr('title') || '');
    if (d > hoje) return;
    const popup = cheerio.load($(dia).attr('data-content') || '');
    popup('tr').each((__, tr) => {
      const tds = popup(tr).find('td');
      const disciplina = tds.first().text().trim();
      if (!disciplina || popup(tr).find('i.fa-edit').length) return;
      const item = { data: d, disciplina, hora: tds.eq(1).text().trim() };
      const k = `${item.data}|${item.disciplina}|${item.hora}`;
      if (!vistos.has(k)) { vistos.add(k); lista.push(item); }
    });
  });
  return lista.sort((a, b) => a.data.localeCompare(b.data) || a.hora.localeCompare(b.hora));
}

/** Perfil ativo no SGE: 'escola' (Unidade escolar), 'professor' (Servidor) ou null se não confirmado. */
function perfilAtivo(horarioResposta) {
  const $ = cheerio.load(htmlDaResposta(horarioResposta));
  const v = (id) => ($(`#${id}`).attr('value') || '').trim().toLowerCase() === 'true';
  const escola = v('ehPerfilEscola'); const professor = v('ehPerfilProfessor');
  return escola && !professor ? 'escola' : professor && !escola ? 'professor' : null;
}

/** Professores do seletor #ProfessorId (perfil Unidade escolar). */
function professores(horarioResposta) {
  const $ = cheerio.load(htmlDaResposta(horarioResposta));
  const vistos = new Set();
  return $('#ProfessorId option[value]').toArray().map((o) => $(o))
    .filter((o) => o.attr('value').trim() && o.attr('value') !== '0' && o.attr('disabled') == null)
    .filter((o) => !normalizarSge(o.text()).startsWith('selecione'))
    .map((o) => ({ id: o.attr('value'), nome: o.text().trim() }))
    .filter((p) => p.nome && !vistos.has(p.id) && vistos.add(p.id))
    .sort((a, b) => normalizarSge(a.nome).localeCompare(normalizarSge(b.nome)));
}

function tipoHorario(horarioResposta) {
  const $ = cheerio.load(htmlDaResposta(horarioResposta));
  const campo = $('#Tipo').length ? $('#Tipo') : $('[name=Tipo]');
  const v = (campo.attr('value') || campo.find('option[selected]').attr('value') || campo.find("option[value]:not([value=''])").attr('value') || '').trim();
  if (!v) throw new Error('Tipo de horário não identificado no SGE.');
  return v;
}

/** Vínculos professor → (turma, componente) a partir do quadro de horário dele. Linhas ambíguas ficam de fora. */
function analisarVinculos(quadroHtml, professor, turmas) {
  const $ = cheerio.load(quadroHtml);
  const linhas = $('tr').toArray().map((tr) => $(tr).find('td'))
    .filter((c) => c.length === 3 && normalizarSge(c.eq(0).text()).includes(' as ') && /\d{1,2}:\d{2}/.test(c.eq(0).text()));
  if (!linhas.length) return { vinculos: [], diagnosticos: [] };
  const vinculos = new Map();
  const diagnosticos = [];
  for (const c of linhas) {
    const nome = normalizarSge(c.eq(2).text());
    const componente = c.eq(1).text().trim();
    let candidatas = turmas.filter((t) => normalizarSge(t.t) === nome);
    if (!candidatas.length) candidatas = turmas.filter((t) => normalizarSge(t.t).split('/')[0] === nome.split('/')[0]);
    const ids = [...new Set(candidatas.map((t) => t.v))];
    if (ids.length === 1 && componente) vinculos.set(chaveVinculo(ids[0], componente), { turmaId: ids[0], componente, professor });
    else diagnosticos.push({ professor: professor.nome, turma: c.eq(2).text().trim(), componente, tipo: ids.length > 1 ? 'ambiguo' : !ids.length ? 'sem_correspondencia' : 'componente_ausente' });
  }
  return { vinculos: [...vinculos.values()], diagnosticos };
}

/** Aulas do quadro de horário de um dia: linhas "07:00 às 07:50 | componente | turma". */
function aulasDoQuadro(quadroHtml) {
  const $ = cheerio.load(quadroHtml);
  const aulas = [];
  $('tr').each((_, tr) => {
    const c = $(tr).find('td');
    if (c.length !== 3) return;
    const m = /(\d{1,2}:\d{2})\s*(?:às|as|a)\s*(\d{1,2}:\d{2})/i.exec($(c[0]).text());
    if (!m) return;
    aulas.push({ inicio: m[1].padStart(5, '0'), fim: m[2].padStart(5, '0'), disciplina: $(c[1]).text().trim(), turma: $(c[2]).text().trim() });
  });
  return aulas;
}

module.exports = { aulasDoQuadro, perfilAtivo, normalizarSge, chaveVinculo, frequencia, planos, mesesComAula, numeroBimestre, janelaOuNull, pendenciasCoordenacao, professores, tipoHorario, analisarVinculos, normalizar, dataIso, paraBr, hojeIso, categoriaTurma, htmlDaResposta, opcoesTurma, identidadeHorario, turmasDoQuadro, conteudos };

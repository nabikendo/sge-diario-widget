'use strict';
/**
 * Parsing puro (sem rede): porta de trechos de `SgeReadClient.kt` / `SgeParsing`.
 * Jsoup → cheerio: os seletores CSS são os mesmos, então a tradução é quase linha a linha.
 * Datas circulam como texto ISO (AAAA-MM-DD): comparar strings ISO equivale a comparar datas.
 */
const cheerio = require('cheerio');

const normalizar = (x = '') => x.replace(/ /g, ' ').trim().replace(/\s+/g, ' ').toLowerCase();

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

module.exports = { normalizar, dataIso, paraBr, hojeIso, categoriaTurma, htmlDaResposta, opcoesTurma, identidadeHorario, turmasDoQuadro, conteudos };

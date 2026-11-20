'use strict';
/**
 * Relatório da Coordenação separado por professor (HTML puro, sem Electron; o PDF é gerado em relatorio.js).
 * Cada professor tem a sua página: resumo em cinco números e, por turma e componente, o que falta em
 * Conteúdo, Chamada, Plano de aula, Plano de curso e PEI, com as datas.
 */

const brData = (iso) => iso.slice(8, 10) + '/' + iso.slice(5, 7);
const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
const plural = (n, um, varios) => `${n} ${n === 1 ? um : varios}`;

/** Os cinco itens do diário, na ordem do SGE. `dado(it)` devolve a lista de datas, um número, ou null quando não foi lido. */
const ITENS = [
  { chave: 'conteudo', rotulo: 'Conteúdo Aplicado', frase: (n) => plural(n, 'aula sem conteúdo registrado', 'aulas sem conteúdo registrado'), dado: (it) => it.datas ?? [] },
  { chave: 'chamada', rotulo: 'Chamada (frequência)', frase: (n) => plural(n, 'dia sem chamada', 'dias sem chamada'), dado: (it) => it.chamada ?? null },
  { chave: 'planoAula', rotulo: 'Plano de aula', frase: (n) => plural(n, 'plano pendente', 'planos pendentes'), dado: (it) => it.planos?.planoAula ?? null },
  { chave: 'planoCurso', rotulo: 'Plano de curso', frase: (n) => plural(n, 'plano pendente', 'planos pendentes'), dado: (it) => it.planos?.planoCurso ?? null },
  { chave: 'pei', rotulo: 'PEI', frase: (n) => plural(n, 'PEI pendente', 'PEIs pendentes'), dado: (it) => it.planos?.pei ?? null },
];

const quantidade = (d) => (Array.isArray(d) ? d.length : d);

/** Datas agrupadas por mês: "outubro: 19, 20, 21 · novembro: 03, 04". */
function datasPorMes(datas) {
  const grupos = new Map();
  for (const d of [...datas].sort()) {
    const m = Number(d.slice(5, 7)) - 1;
    if (!grupos.has(m)) grupos.set(m, []);
    grupos.get(m).push(d.slice(8, 10));
  }
  return [...grupos].map(([m, dias]) => `<span class="mes"><b>${MESES[m]}</b> ${dias.join(', ')}</span>`).join('');
}

/** Totais do professor nos cinco itens (null quando o item não foi lido para ninguém). */
function totais(prof) {
  const t = Object.fromEntries(ITENS.map((i) => [i.chave, null]));
  for (const it of prof.itens) for (const i of ITENS) {
    const q = quantidade(i.dado(it));
    if (q != null) t[i.chave] = (t[i.chave] ?? 0) + q;
  }
  return t;
}

function linhaItem(i, it) {
  const d = i.dado(it);
  const q = quantidade(d);
  if (q == null) return `<tr class="nl"><th>${i.rotulo}</th><td>Não verificado neste relatório</td></tr>`;
  if (q === 0) return `<tr class="ok"><th>${i.rotulo}</th><td>Em dia</td></tr>`;
  return `<tr class="pend"><th>${i.rotulo}</th><td><b>${i.frase(q)}</b>${Array.isArray(d) ? `<div class="datas">${datasPorMes(d)}</div>` : ''}</td></tr>`;
}

function blocoProfessor(painel, prof, { quebra }) {
  const t = totais(prof);
  const total = ITENS.reduce((s, i) => s + (t[i.chave] ?? 0), 0);
  const itens = [...prof.itens].sort((a, b) => a.turma.localeCompare(b.turma) || a.componente.localeCompare(b.componente));
  const caixas = ITENS.map((i) => `<div class="caixa ${t[i.chave] ? 'pend' : t[i.chave] === null ? 'nl' : 'ok'}"><b>${t[i.chave] ?? '—'}</b>${i.rotulo}</div>`).join('');
  const temPend = (it) => ITENS.some((i) => (quantidade(i.dado(it)) ?? 0) > 0);
  const emDia = itens.filter((it) => !temPend(it));
  const turmas = itens.filter(temPend).map((it) => `<section class="turma"><h3>Turma ${esc(it.turma)} <span>· ${esc(it.componente)}</span></h3><table>${ITENS.map((i) => linhaItem(i, it)).join('')}</table></section>`).join('');
  return `<article class="${quebra ? 'quebra' : ''}">
    <header><p class="escola">${esc(painel.escola ?? 'Relatório de pendências do diário')}</p><h1>${esc(prof.nome)}</h1>
      <p>${periodoTexto(painel)} · dados do SGE lidos em ${new Date(painel.atualizadoEm).toLocaleString('pt-BR')}</p></header>
    <p class="chamado">Você tem <b>${plural(total, 'pendência', 'pendências')}</b> no diário, em ${plural(itens.length, 'turma/componente', 'turmas/componentes')}. Veja abaixo o que falta em cada um.</p>
    <div class="caixas">${caixas}</div>
    ${turmas}
    ${emDia.length ? `<p class="emdia"><b>Em dia (nada a fazer):</b> ${emDia.map((it) => `${esc(it.turma)} · ${esc(it.componente)}`).join('; ')}.</p>` : ''}
    <p class="como">Como regularizar: entre no SGE com o perfil Servidor, abra a turma e o componente indicados e registre o que falta nas datas listadas. Depois da correção, a pendência some na próxima leitura.</p>
  </article>`;
}

function periodoTexto(painel) {
  return painel.janela ? `${painel.janela.numero}º bimestre (${brData(painel.janela.inicio)} a ${brData(painel.janela.fim)})` : 'Ano letivo de 2026';
}

const ESTILO = `
  @page{size:A4;margin:14mm}
  body{font:10.5pt "Segoe UI",system-ui,sans-serif;color:#14121c;margin:0}
  article.quebra{page-break-before:always}article:first-of-type{page-break-before:auto}
  .escola{margin:0;font-size:9pt;letter-spacing:.06em;text-transform:uppercase;color:#6b54ee;font-weight:600}
  h1{font-size:21pt;margin:2px 0 2px}header p{margin:0;color:#5f5a78;font-size:9.5pt}
  .chamado{margin:12px 0 10px;font-size:11pt}
  .caixas{display:grid;grid-template-columns:repeat(5,1fr);gap:8px;margin-bottom:14px}
  .caixa{border-radius:10px;padding:8px 6px;text-align:center;font-size:8.5pt;line-height:1.25}.caixa b{display:block;font-size:19pt}
  .caixa.pend{background:#fdecea;color:#a4352e}.caixa.ok{background:#e6f5ee;color:#17714c}.caixa.nl{background:#f0eff5;color:#7b7790}
  .turma{break-inside:avoid;border:1px solid #e1def0;border-radius:10px;padding:8px 12px 6px;margin-bottom:9px}
  h3{margin:0 0 4px;font-size:11.5pt}h3 span{font-weight:400;color:#5f5a78}
  table{width:100%;border-collapse:collapse}th{width:27%;text-align:left;font-weight:600;font-size:9.5pt;padding:4px 8px 4px 0;vertical-align:top}
  td{padding:4px 0;font-size:9.5pt}tr+tr th,tr+tr td{border-top:1px solid #efedf7}
  tr.ok td{color:#17714c}tr.nl td{color:#8a869c;font-style:italic}tr.pend td b{color:#a4352e}
  .datas{margin-top:2px}.mes{display:inline-block;margin:0 14px 2px 0;font-size:9pt;color:#3a3650}.mes b{text-transform:capitalize;color:#14121c;margin-right:3px}
  .emdia{margin:2px 0 0;font-size:9pt;color:#17714c}
  .como{margin-top:12px;padding:8px 12px;background:#f5f3ff;border-radius:10px;font-size:9pt;color:#4a4566}
  table.resumo th{width:auto}table.resumo th:first-child{width:36%}
  table.resumo th,table.resumo td{font-size:9.5pt;padding:5px 6px;border-top:1px solid #efedf7;text-align:right}table.resumo th:first-child,table.resumo td:first-child{text-align:left;width:auto}
  table.resumo thead th{font-size:8.5pt;color:#5f5a78;text-transform:uppercase;letter-spacing:.03em;border-top:0}
`;

/** Capa: quadro com todos os professores e os cinco números de cada um. */
function capa(painel, profs) {
  const linhas = profs.map((p) => { const t = totais(p); const tot = ITENS.reduce((s, i) => s + (t[i.chave] ?? 0), 0);
    return `<tr><td>${esc(p.nome)}</td>${ITENS.map((i) => `<td>${t[i.chave] ?? '—'}</td>`).join('')}<td><b>${tot}</b></td></tr>`; }).join('');
  const naoLidos = ITENS.filter((i) => profs.length && profs.every((p) => totais(p)[i.chave] === null)).map((i) => i.rotulo);
  return `<article><header><p class="escola">${esc(painel.escola ?? 'Relatório de pendências do diário')}</p><h1>Pendências do diário por professor</h1><p>${periodoTexto(painel)} · dados do SGE lidos em ${new Date(painel.atualizadoEm).toLocaleString('pt-BR')}</p></header>
    <p class="chamado">${plural(profs.length, 'professor com pendência', 'professores com pendência')} de ${painel.totalProfessores ?? profs.length}. Cada professor tem uma página própria a seguir.</p>
    <table class="resumo"><thead><tr><th>Professor</th>${ITENS.map((i) => `<th>${i.rotulo.replace(' (frequência)', '')}</th>`).join('')}<th>Total</th></tr></thead><tbody>${linhas}</tbody></table>
    ${naoLidos.length ? `<p class="como">Não verificados neste relatório: ${naoLidos.join(', ')}. Ative a leitura em Configurações → Coordenação para incluí-los.</p>` : ''}
    ${painel.semPendencia?.length ? `<p class="como"><b>Em dia:</b> ${esc(painel.semPendencia.join(', '))}.</p>` : ''}</article>`;
}

const doc = (corpo) => `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><style>${ESTILO}</style><body>${corpo}</body></html>`;

/** Um único documento: capa e uma página (ou mais) por professor. */
function htmlProfessores(painel) {
  const profs = (painel.professores ?? []).filter((p) => p.id);
  return doc(capa(painel, profs) + profs.map((p) => blocoProfessor(painel, p, { quebra: true })).join(''));
}

/** Documento de um só professor (para enviar individualmente). */
const htmlUmProfessor = (painel, prof) => doc(blocoProfessor(painel, prof, { quebra: false }));

/** Nome de arquivo seguro para o Windows. */
const nomeDeArquivo = (t) => String(t).normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^A-Za-z0-9 ._-]/g, '').replace(/\s+/g, ' ').trim().slice(0, 80) || 'professor';

module.exports = { htmlProfessores, htmlUmProfessor, nomeDeArquivo, totais, ITENS };

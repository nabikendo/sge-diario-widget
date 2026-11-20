'use strict';
/** Relatórios a partir do painel: CSV (abre no Excel) e PDF (gerado numa janela oculta). */
const { BrowserWindow } = require('electron');

const brData = (iso) => iso.split('-').reverse().join('/');
const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const STATUS = { 'em-dia': 'Em dia', pendente: 'Pendente', erro: 'Não lido' };

function csv(painel) {
  const campo = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const linhas = [['Escola', 'Turma', 'Componente', 'Situação', 'Aulas registradas', 'Dias sem conteúdo', 'Datas sem conteúdo', '% registrado']];
  for (const t of painel.turmas ?? []) {
    linhas.push([t.escola, t.nome, t.disciplina, STATUS[t.status] ?? t.status, t.registradas,
      t.pendentes?.length ?? 0, (t.pendentes ?? []).map(brData).join(', '), t.status === 'erro' ? '' : Math.round((t.progresso ?? 0) * 100)]);
  }
  // BOM + ponto e vírgula: o Excel em português abre com acentos e colunas certas.
  return '﻿' + linhas.map((l) => l.map(campo).join(';')).join('\r\n');
}

/** Coordenação: uma linha por professor × turma × componente, com as datas sem conteúdo. */
function csvCoordenacao(painel) {
  const campo = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const linhas = [['Professor', 'Turma', 'Componente', 'Aulas sem conteúdo', 'Datas sem conteúdo']];
  for (const p of painel.professores ?? []) {
    for (const i of p.itens) linhas.push([p.nome, i.turma, i.componente, i.datas.length, i.datas.map(brData).join(', ')]);
  }
  for (const nome of painel.semPendencia ?? []) linhas.push([nome, '', '', 0, '']);
  return '﻿' + linhas.map((l) => l.map(campo).join(';')).join('\r\n');
}

function htmlCoordenacao(painel) {
  const profs = painel.professores ?? [];
  const dias = profs.reduce((s, p) => s + p.dias, 0);
  const corpo = profs.map((p) => `<h2>${esc(p.nome)} <small>${p.dias} ${p.dias === 1 ? 'aula' : 'aulas'} sem conteúdo</small></h2><table><tbody>${
    p.itens.map((i) => `<tr><td>${esc(i.turma)}</td><td>${esc(i.componente)}</td><td>${i.datas.map(brData).join(', ')}</td></tr>`).join('')}</tbody></table>`).join('');
  return `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><style>
    body{font:11pt "Segoe UI",system-ui,sans-serif;color:#14121c;margin:28px}h1{font-size:20pt;margin:0 0 4px}p{margin:2px 0;color:#5f5a78}
    h2{font-size:13pt;margin:18px 0 4px}h2 small{font-weight:400;color:#c8463e;font-size:10pt;margin-left:8px}
    table{width:100%;border-collapse:collapse}td{padding:5px 8px;border-bottom:1px solid #e6e3f0;vertical-align:top}
  </style><body><h1>Pendências de Conteúdo Aplicado da escola</h1>
  <p>${painel.janela ? `${painel.janela.numero}º bimestre (${brData(painel.janela.inicio)} a ${brData(painel.janela.fim)})` : 'Ano letivo'}</p>
  <p>${dias} aulas sem conteúdo · ${profs.length} professores com pendência de ${painel.totalProfessores}</p>
  <p>Dados do SGE lidos em ${new Date(painel.atualizadoEm).toLocaleString('pt-BR')}</p>${corpo}
  ${painel.semPendencia?.length ? `<p style="margin-top:18px"><b>Em dia:</b> ${esc(painel.semPendencia.join(', '))}.</p>` : ''}</body></html>`;
}

function html(painel) {
  const turmas = painel.turmas ?? [];
  const dias = turmas.reduce((s, t) => s + (t.pendentes?.length ?? 0), 0);
  const b = painel.bimestre;
  const linhas = turmas.map((t) => `<tr>
      <td>${esc(t.nome)}</td><td>${esc(t.disciplina)}</td>
      <td class="s ${t.status}">${STATUS[t.status] ?? ''}</td>
      <td class="n">${t.registradas ?? 0}</td><td class="n">${t.pendentes?.length ?? 0}</td>
      <td>${t.status === 'erro' ? esc(t.erro) : (t.pendentes ?? []).map(brData).join(', ') || '—'}</td></tr>`).join('');
  return `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><style>
    body{font:11pt "Segoe UI",system-ui,sans-serif;color:#14121c;margin:28px}
    h1{font-size:20pt;margin:0 0 4px} p{margin:2px 0;color:#5f5a78}
    .resumo{display:flex;gap:24px;margin:18px 0}.resumo b{display:block;font-size:20pt;color:#14121c}
    table{width:100%;border-collapse:collapse;margin-top:8px}th,td{text-align:left;padding:7px 8px;border-bottom:1px solid #e6e3f0;vertical-align:top}
    th{font-size:9pt;text-transform:uppercase;letter-spacing:.04em;color:#5f5a78}.n{text-align:right}
    .s.pendente{color:#c8463e;font-weight:600}.s.em-dia{color:#1f8a5e;font-weight:600}.s.erro{color:#b7791f;font-weight:600}
  </style><body>
  <h1>Relatório de Conteúdo Aplicado</h1>
  <p>${esc(turmas[0]?.escola ?? '')}${b ? ` · ${b.numero}º bimestre (${brData(b.inicio)} a ${brData(b.fim)})` : ''}</p>
  <p>Dados do SGE lidos em ${new Date(painel.atualizadoEm).toLocaleString('pt-BR')}</p>
  <div class="resumo"><div><b>${dias}</b>dias sem conteúdo</div><div><b>${turmas.filter((t) => t.status === 'pendente').length}</b>turmas com pendência</div><div><b>${turmas.filter((t) => t.status === 'em-dia').length}</b>turmas em dia</div></div>
  <table><thead><tr><th>Turma</th><th>Componente</th><th>Situação</th><th class="n">Registradas</th><th class="n">Sem conteúdo</th><th>Datas</th></tr></thead><tbody>${linhas}</tbody></table>
  </body></html>`;
}

const pdf = (painel) => paraPdf(html(painel));
const pdfCoordenacao = (painel) => paraPdf(htmlCoordenacao(painel));
async function paraPdf(documento) {
  const w = new BrowserWindow({ show: false, webPreferences: { sandbox: true, javascript: false } });
  try {
    await w.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(documento));
    return await w.webContents.printToPDF({ pageSize: 'A4', printBackground: true, margins: { marginType: 'default' } });
  } finally { w.destroy(); }
}

module.exports = { csv, pdf, csvCoordenacao, pdfCoordenacao };

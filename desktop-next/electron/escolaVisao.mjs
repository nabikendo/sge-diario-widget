/**
 * Visões da Coordenação sobre o painel da escola: período (bimestre atual ou ano), agrupamentos por professor, turma e componente.
 * Módulo sem dependências, usado pela interface, pelo processo principal (relatórios, contador) e pelos testes.
 */
const fmt = (iso) => iso.slice(8, 10) + '/' + iso.slice(5, 7);
const somaPlanos = (p) => Object.values(p ?? {}).reduce((s, v) => s + (v ?? 0), 0);
export const norm = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();

/** Janela do período: 'ano' = sem limite; 'bimestre' = o atual; '1'..'4' = o bimestre escolhido. */
export function janelaDoPeriodo(painel, periodo) {
  if (periodo === 'ano') return null;
  if (periodo === 'bimestre') return painel.bimestre ?? null;
  return painel.bimestres?.find((b) => String(b.numero) === String(periodo)) ?? null;
}
export const noPeriodo = (d, janela) => !janela || (d >= janela.inicio && d <= janela.fim);
export const pendenciasDoItem = (it) => (it.datas?.length ?? 0) + (it.chamada?.length ?? 0) + somaPlanos(it.planos);

/**
 * Painel recortado no período, com totais recalculados. Professores sem nada pendente passam para `semPendencia`.
 * Chamada e planos só existem para o bimestre atual (e para o ano, que inclui o atual): em bimestres passados ficam de fora.
 */
export function visaoPeriodo(painel, periodo = 'bimestre') {
  if (!painel) return painel;
  const janela = janelaDoPeriodo(painel, periodo);
  const efetivo = janela ? String(janela.numero) : 'ano';
  const atual = !janela || janela.numero === painel.bimestre?.numero;
  const professores = []; const semPendencia = [...(painel.semPendencia ?? [])];
  for (const p of painel.professores ?? []) {
    const itens = p.itens.map((it) => ({ ...it, datas: (it.datas ?? []).filter((d) => noPeriodo(d, janela)), chamada: atual ? it.chamada : null, planos: atual ? it.planos : null })).filter((it) => pendenciasDoItem(it) > 0);
    if (!itens.length) { if (p.id && !semPendencia.includes(p.nome)) semPendencia.push(p.nome); continue; }
    professores.push({ ...p, itens, dias: itens.reduce((s, i) => s + i.datas.length, 0), chamadas: itens.reduce((s, i) => s + (i.chamada?.length ?? 0), 0), planos: itens.reduce((s, i) => s + somaPlanos(i.planos), 0), total: itens.reduce((s, i) => s + pendenciasDoItem(i), 0) });
  }
  professores.sort((a, b) => (a.id === '') - (b.id === '') || b.total - a.total || a.nome.localeCompare(b.nome));
  return { ...painel, periodo: efetivo, janela, professores, semPendencia: semPendencia.sort((a, b) => a.localeCompare(b)) };
}

/** "18 aulas · 19/10 a 18/11". */
export function resumoDatas(datas) {
  const d = [...(datas ?? [])].sort();
  if (!d.length) return '';
  return `${d.length} ${d.length === 1 ? 'aula' : 'aulas'} · ${d.length > 1 ? `${fmt(d[0])} a ${fmt(d[d.length - 1])}` : fmt(d[0])}`;
}

/** Uma linha por (professor, turma, componente). */
export const achatar = (painel) => (painel?.professores ?? []).flatMap((p) => p.itens.map((it) => ({ ...it, professor: { id: p.id, nome: p.nome }, total: pendenciasDoItem(it) })));

function agrupar(itens, chave, rotulo) {
  const m = new Map();
  for (const it of itens) {
    const k = chave(it);
    if (!m.has(k)) m.set(k, { chave: k, rotulo: rotulo(it), itens: [] });
    m.get(k).itens.push(it);
  }
  return [...m.values()].map((g) => ({
    ...g, total: g.itens.reduce((s, i) => s + i.total, 0),
    professores: [...new Map(g.itens.map((i) => [i.professor.id || i.professor.nome, i.professor.nome])).values()],
    turmas: [...new Set(g.itens.map((i) => i.turma))], componentes: [...new Set(g.itens.map((i) => i.componente))],
  })).sort((a, b) => b.total - a.total || a.rotulo.localeCompare(b.rotulo));
}
export const porTurma = (painel) => agrupar(achatar(painel), (i) => i.turmaId, (i) => i.turma);
export const porComponente = (painel) => agrupar(achatar(painel), (i) => norm(i.componente), (i) => i.componente);
export const porProfessor = (painel) => (painel?.professores ?? []).map((p) => ({ chave: p.id || p.nome, rotulo: p.nome, total: p.total ?? p.dias, itens: achatar({ professores: [p] }), id: p.id,
  turmas: [...new Set(p.itens.map((i) => i.turma))], componentes: [...new Set(p.itens.map((i) => i.componente))], professores: [p.nome] }));

/** Filtro de busca (professor, turma ou componente) sobre um grupo. */
export const casaBusca = (g, q) => { const t = norm(q); return !t || norm(`${g.rotulo} ${g.turmas?.join(' ')} ${g.componentes?.join(' ')} ${g.professores?.join(' ')}`).includes(t); };

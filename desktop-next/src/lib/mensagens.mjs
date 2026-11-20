/** Mensagens de cobrança (para copiar e colar no WhatsApp). Só texto: o app não envia nada sozinho. */
const brData = (iso) => iso.slice(8, 10) + '/' + iso.slice(5, 7);
const primeiroNome = (nome) => {
  const p = String(nome ?? '').trim().split(/\s+/)[0] ?? '';
  return p ? p.charAt(0).toUpperCase() + p.slice(1).toLowerCase() : '';
};

/** Linhas de pendência de um item da coordenação (turma + componente). */
export function linhasDoItem(item) {
  const partes = [];
  if (item.datas?.length) partes.push(`Conteúdo: ${item.datas.map(brData).join(', ')}`);
  if (item.chamada?.length) partes.push(`Chamada: ${item.chamada.map(brData).join(', ')}`);
  const p = item.planos;
  if (p?.planoAula) partes.push(`${p.planoAula} ${p.planoAula === 1 ? 'plano de aula' : 'planos de aula'}`);
  if (p?.planoCurso) partes.push(`${p.planoCurso} ${p.planoCurso === 1 ? 'plano de curso' : 'planos de curso'}`);
  if (p?.pei) partes.push(`${p.pei} PEI`);
  return partes.length ? `• ${item.turma} (${item.componente}) – ${partes.join(' | ')}` : null;
}

export function mensagemProfessor(prof, { prazo = '', coordenador = '' } = {}) {
  const linhas = prof.itens.map(linhasDoItem).filter(Boolean);
  if (!linhas.length) return '';
  const nome = primeiroNome(prof.nome);
  return [
    `Olá${nome ? `, ${nome}` : ''}! Passando para lembrar das pendências do seu diário no SGE:`,
    '',
    ...linhas,
    '',
    prazo ? `Pode regularizar ${prazo}? Qualquer dúvida, me chame.` : 'Pode regularizar assim que possível? Qualquer dúvida, me chame.',
    coordenador ? `Obrigado! ${coordenador}` : 'Obrigado!',
  ].join('\n');
}

/** Todas as mensagens, uma por professor identificado e com pendência. */
export function mensagensEmLote(professores, opcoes) {
  return professores.filter((p) => p.id).map((p) => ({ professor: p.nome, texto: mensagemProfessor(p, opcoes) })).filter((m) => m.texto);
}

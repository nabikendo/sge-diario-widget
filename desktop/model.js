'use strict';
const NOMES = ['Conteúdos', 'Frequência', 'Plano de aula', 'Plano de curso', 'PEI'];

function lerBackup(texto) {
  if (new TextEncoder().encode(texto).length > 10 * 1024 * 1024) throw new Error('Backup maior que 10 MB.');
  const raiz = JSON.parse(texto);
  if (raiz.formato !== 'sge-diario-backup' || raiz.schema !== 1 || !raiz.dados || typeof raiz.dados !== 'object')
    throw new Error('Este arquivo não é um backup compatível do SGE Diário.');
  const obter = chave => {
    if (typeof raiz.dados[chave] !== 'string') return { turmas: [] };
    const p = JSON.parse(raiz.dados[chave]);
    if (!Array.isArray(p.turmas)) throw new Error('Painel inválido no backup.');
    return p;
  };
  const pessoal = obter('painel_v2');
  const coord = obter('painel_coord_v2');
  const normalizar = r => {
    if (!r || typeof r.id !== 'string' || !Array.isArray(r.modulos) || r.modulos.length !== 5)
      throw new Error('Resultado de turma incompleto no backup.');
    return {
      id: r.id, nome: String(r.nome || ''), escola: String(r.escola || ''),
      disciplina: String(r.disciplina || ''), atualizado: String(r.atualizado || ''),
      modulos: r.modulos.map(m => {
        if (!m || (m.quantidade !== null && (!Number.isInteger(m.quantidade) || m.quantidade < 0)))
          throw new Error('Contagem inválida no backup.');
        return { quantidade: m.quantidade, completa: m.completa === true,
          detalhe: String(m.detalhe || ''), aguardando: Number(m.aguardando || 0), aprovados: Number(m.aprovados || 0) };
      })
    };
  };
  return {
    criado: String(raiz.criado || ''), versao: String(raiz.versao || ''),
    pessoal: pessoal.turmas.map(normalizar),
    coordenacao: coord.turmas.flatMap(t => {
      if (!Array.isArray(t.disc)) throw new Error('Turma de coordenação inválida.');
      return t.disc.map(normalizar);
    })
  };
}

function valor(m) {
  if (!m || m.quantidade === null) return '—';
  return `${m.quantidade}${m.completa ? '' : '*'}`;
}

function estado(r) {
  if (r.modulos.some(m => m.quantidade !== null && m.quantidade > 0)) return 'pendente';
  if (r.modulos.some(m => m.quantidade === null || !m.completa)) return 'conferir';
  return 'em dia';
}

if (typeof module !== 'undefined') module.exports = { lerBackup, valor, estado, NOMES };
if (typeof window !== 'undefined') window.requireModel = { lerBackup, valor, estado, NOMES };

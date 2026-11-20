/** Painel fictício no mesmo formato que motor.js entrega (para ver o layout sem o SGE). */
const agora = new Date();
const iso = (diasAtras) => new Date(agora.getTime() - diasAtras * 86_400_000).toISOString().slice(0, 10);

const mod = (pend, freq = [], pa = 0, pc = 0, pei = null) => ({
  conteudo: { pendentes: pend, registradas: 14 }, frequencia: { datas: freq },
  planoAula: { pendentes: pa, aguardando: 1, aprovados: 6, ausentes: pa }, planoCurso: { pendentes: pc, aguardando: 0, aprovados: 1, ausentes: pc },
  pei: pei == null ? { indisponivel: true } : { pendentes: pei, aguardando: 0, aprovados: 2, ausentes: pei },
});
const cont = (m) => ({ conteudo: m.conteudo.pendentes.length, frequencia: m.frequencia.datas.length, planoAula: m.planoAula.pendentes, planoCurso: m.planoCurso.pendentes, pei: m.pei.indisponivel ? null : m.pei.pendentes });
const comModulos = (t, m) => { const contagens = cont(m); const totalPendencias = Object.values(contagens).reduce((s, v) => s + (v ?? 0), 0); return { ...t, modulos: m, contagens, totalPendencias, status: t.status === 'erro' ? 'erro' : totalPendencias ? 'pendente' : 'em-dia' }; };

export const exemploPainel = {
  versao: 1,
  atualizadoEm: new Date(agora.getTime() - 5 * 60_000).toISOString(),
  perfil: 'professor',
  bimestre: { numero: 4, inicio: iso(48), fim: iso(-22) },
  turmas: [
    comModulos({ id: '1', nome: '33.01', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 14, pendentes: [iso(9), iso(6), iso(2)], progresso: 14 / 17 }, mod([iso(9), iso(6), iso(2)], [iso(2)], 1)),
    comModulos({ id: '2', nome: '33.02', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 16, pendentes: [], progresso: 1 }, mod([])),
    comModulos({ id: '3', nome: '33.03', escola: 'CE José Luiz Siqueira', disciplina: 'Física', registradas: 11, pendentes: [iso(3)], progresso: 11 / 12 }, mod([iso(3)], [], 0, 0, 1)),
    comModulos({ id: '4', nome: '13.01', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 15, pendentes: [], progresso: 1 }, mod([], [iso(1)])),
    comModulos({ id: '5', nome: '13.02', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 9, pendentes: [iso(13), iso(10), iso(8), iso(5), iso(1)], progresso: 9 / 14 }, mod([iso(13), iso(10), iso(8), iso(5), iso(1)], [iso(5), iso(1)], 2, 1)),
    { id: '6', nome: '13.03', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 0, pendentes: [], progresso: 0, status: 'erro', erro: 'Componente não identificado: Química' },
  ],
};

/** Histórico fictício: uma sincronização por dia nos últimos 21 dias. */
export const exemploHistorico = Array.from({ length: 21 }, (_, i) => {
  const d = new Date(agora.getTime() - (20 - i) * 86_400_000);
  const dias = Math.max(0, Math.round(14 - i * 0.45 + 3 * Math.sin(i / 2)));
  return { em: d.toISOString(), dias, pendentes: Math.min(6, Math.ceil(dias / 3)), emDia: 6 - Math.min(6, Math.ceil(dias / 3)), erros: 0, total: 6, porTurma: {} };
});

export const exemploCoordenacao = {
  versao: 1, atualizadoEm: new Date(agora.getTime() - 12 * 60_000).toISOString(), totalProfessores: 18,
  turmas: [{ id: 'a', nome: '13.01/EM.MAT', dias: 6 }, { id: 'b', nome: '23.02/EM.VESP', dias: 3 }],
  professores: [
    { id: '10', nome: 'Ana Paula Ribeiro', dias: 7, itens: [
      { turmaId: 'a', turma: '13.01/EM.MAT', componente: 'Matemática', datas: [iso(12), iso(8), iso(5), iso(1)] },
      { turmaId: 'b', turma: '23.02/EM.VESP', componente: 'Matemática', datas: [iso(6), iso(4), iso(2)] }] },
    { id: '11', nome: 'Carlos Henrique Souza', dias: 2, itens: [{ turmaId: 'a', turma: '13.01/EM.MAT', componente: 'História', datas: [iso(3), iso(1)] }] },
    { id: '', nome: 'Professor não identificado', dias: 1, itens: [{ turmaId: 'b', turma: '23.02/EM.VESP', componente: 'Eletiva', datas: [iso(2)] }] },
  ],
  semPendencia: ['Beatriz Lima', 'Daniel Alves', 'Eduarda Nunes'],
};

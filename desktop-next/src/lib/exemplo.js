/** Painel fictício no mesmo formato que motor.js entrega (para ver o layout sem o SGE). */
const agora = new Date();
const iso = (diasAtras) => new Date(agora.getTime() - diasAtras * 86_400_000).toISOString().slice(0, 10);

export const exemploPainel = {
  versao: 1,
  atualizadoEm: new Date(agora.getTime() - 5 * 60_000).toISOString(),
  perfil: 'professor',
  bimestre: { numero: 4, inicio: iso(48), fim: iso(-22) },
  turmas: [
    { id: '1', nome: '33.01', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 14, pendentes: [iso(9), iso(6), iso(2)], progresso: 14 / 17, status: 'pendente' },
    { id: '2', nome: '33.02', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 16, pendentes: [], progresso: 1, status: 'em-dia' },
    { id: '3', nome: '33.03', escola: 'CE José Luiz Siqueira', disciplina: 'Física', registradas: 11, pendentes: [iso(3)], progresso: 11 / 12, status: 'pendente' },
    { id: '4', nome: '13.01', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 15, pendentes: [], progresso: 1, status: 'em-dia' },
    { id: '5', nome: '13.02', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 9, pendentes: [iso(13), iso(10), iso(8), iso(5), iso(1)], progresso: 9 / 14, status: 'pendente' },
    { id: '6', nome: '13.03', escola: 'CE José Luiz Siqueira', disciplina: 'Química', registradas: 0, pendentes: [], progresso: 0, status: 'erro', erro: 'Componente não identificado: Química' },
  ],
};

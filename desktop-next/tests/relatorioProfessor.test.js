'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const R = require('../electron/relatorioProfessor');

const painel = {
  atualizadoEm: '2026-11-20T14:00:00Z', totalProfessores: 3, janela: { numero: 4, inicio: '2026-10-19', fim: '2026-12-18' }, semPendencia: ['Beatriz Lima'],
  professores: [
    { id: '1', nome: 'EDMAIRA EDUARDO DA SILVA', itens: [
      { turma: '23.01/EM.MAT-ANL', componente: 'LÍNGUA PORTUGUESA', datas: ['2026-10-19', '2026-10-20', '2026-11-03'], chamada: ['2026-11-03'], planos: { planoAula: 2, planoCurso: 0, pei: null } },
      { turma: '13.01/EM.MAT-ANL', componente: 'ARTES', datas: [], chamada: [], planos: { planoAula: 0, planoCurso: 1, pei: 0 } },
    ] },
    { id: '2', nome: 'João Sem Chamada', itens: [{ turma: '33.01', componente: 'Química', datas: ['2026-11-10'], chamada: null, planos: null }] },
    { id: '', nome: 'Professor não identificado', itens: [{ turma: '9', componente: 'X', datas: ['2026-11-10'] }] },
  ],
};

test('uma página por professor, com os cinco itens e as datas por mês', () => {
  const h = R.htmlProfessores(painel);
  assert.match(h, /Pendências do diário por professor/);
  assert.equal((h.match(/<article/g) ?? []).length, 3); // capa + 2 professores (sem o "não identificado")
  for (const r of ['Conteúdo Aplicado', 'Chamada (frequência)', 'Plano de aula', 'Plano de curso', 'PEI']) assert.match(h, new RegExp(r.replace(/[()]/g, '\\$&')));
  assert.match(h, /3 aulas sem conteúdo registrado/);
  assert.match(h, /<b>outubro<\/b> 19, 20/);
  assert.match(h, /<b>novembro<\/b> 03/);
  assert.match(h, /2 planos pendentes/);
  assert.match(h, /Em dia/);
  assert.doesNotMatch(h, /Professor não identificado/);
});

test('item não lido aparece como "não verificado", nunca como em dia', () => {
  const h = R.htmlUmProfessor(painel, painel.professores[1]);
  assert.match(h, /Não verificado neste relatório/);
  assert.match(h, /1 aula sem conteúdo registrado/);
  const t = R.totais(painel.professores[1]);
  assert.equal(t.conteudo, 1); assert.equal(t.chamada, null); assert.equal(t.planoAula, null);
});

test('totais somam todas as turmas do professor; nomes de arquivo são seguros', () => {
  assert.deepEqual(R.totais(painel.professores[0]), { conteudo: 3, chamada: 1, planoAula: 2, planoCurso: 1, pei: 0 });
  assert.equal(R.nomeDeArquivo('ÂNGELA N. V. DE MENEZES / "X"'), 'ANGELA N. V. DE MENEZES X');
});

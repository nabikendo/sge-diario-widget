'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { novasPendencias, textoNovas, deveAvisarFimDia, textoFimDia, totalPainel } = require('../electron/avisos');
const { gerarBitmap } = require('../electron/iconeContador');

const turma = (id, pend, freq = [], plano = 0) => ({ id, nome: `33.0${id}`, disciplina: 'Química', status: 'pendente', pendentes: pend,
  modulos: { conteudo: { pendentes: pend }, frequencia: { datas: freq } }, contagens: { conteudo: pend.length, frequencia: freq.length, planoAula: plano, planoCurso: 0, pei: null }, totalPendencias: pend.length + freq.length + plano });

test('primeira leitura não gera aviso', () => assert.deepEqual(novasPendencias(null, { turmas: [turma(1, ['2026-11-18'])] }), []));
test('detecta conteúdo, chamada e plano novos', () => {
  const antes = { turmas: [turma(1, ['2026-11-17']), turma(2, [])] };
  const depois = { turmas: [turma(1, ['2026-11-17', '2026-11-19'], ['2026-11-19']), turma(2, [], [], 1)] };
  const n = novasPendencias(antes, depois);
  assert.equal(n.length, 3);
  assert.deepEqual(n.find((x) => x.modulo === 'conteudo').datas, ['2026-11-19']);
  assert.match(textoNovas(n).titulo, /3 novas/);
});
test('pendência que já existia não avisa de novo', () => {
  const p = { turmas: [turma(1, ['2026-11-17'])] };
  assert.deepEqual(novasPendencias(p, p), []);
});
test('aviso do fim do dia: só dia útil, depois da hora, uma vez', () => {
  const qui17 = new Date(2026, 10, 19, 17, 31); const qui16 = new Date(2026, 10, 19, 16, 0); const sab = new Date(2026, 10, 21, 18, 0);
  assert.equal(deveAvisarFimDia(qui17, '17:30', ''), true);
  assert.equal(deveAvisarFimDia(qui16, '17:30', ''), false);
  assert.equal(deveAvisarFimDia(qui17, '17:30', '2026-11-19'), false);
  assert.equal(deveAvisarFimDia(sab, '17:30', ''), false);
});
test('texto do fim do dia e total', () => {
  const p = { turmas: [turma(1, ['2026-11-17', '2026-11-18']), turma(2, [])] };
  assert.equal(totalPainel(p), 2);
  assert.match(textoFimDia(p).titulo, /2 pendências/);
  assert.match(textoFimDia({ turmas: [] }).titulo, /em dia/i);
});
test('ícone do contador tem o tamanho certo e pinta pixels', () => {
  for (const n of [0, 3, 12, 150]) { const b = gerarBitmap(n, 32); assert.equal(b.length, 32 * 32 * 4); assert.ok(b.some((v) => v === 255)); }
});

test('comparação semanal e mensagens', async () => {
  const { referenciaSemanal, diferencas } = await import('../src/lib/comparacao.mjs');
  const { mensagemProfessor, mensagensEmLote } = await import('../src/lib/mensagens.mjs');
  const agora = Date.parse('2026-11-20T12:00:00Z'); const DIA = 86_400_000;
  const lista = [{ em: new Date(agora - 9 * DIA).toISOString(), x: 1 }, { em: new Date(agora - 7 * DIA).toISOString(), x: 2 }, { em: new Date(agora - 1 * DIA).toISOString(), x: 3 }];
  assert.equal(referenciaSemanal(lista, agora).x, 2);
  assert.equal(referenciaSemanal([lista[2]], agora), null);
  const d = diferencas({ Ana: 2, Beto: 5 }, { Ana: 6, Beto: 1, Caio: 3 });
  assert.deepEqual(d.melhoraram.map((i) => i.chave), ['Ana', 'Caio']);
  assert.deepEqual(d.pioraram.map((i) => i.chave), ['Beto']);
  const prof = { id: '1', nome: 'ANA MARIA SOUZA', itens: [{ turma: '33.01', componente: 'Química', datas: ['2026-11-03', '2026-11-05'], chamada: ['2026-11-05'], planos: { planoAula: 1, planoCurso: 0, pei: 0 } }] };
  const msg = mensagemProfessor(prof, { prazo: 'até sexta' });
  assert.match(msg, /Olá, Ana!/); assert.match(msg, /Conteúdo: 03\/11, 05\/11 \| Chamada: 05\/11 \| 1 plano de aula/); assert.match(msg, /até sexta/);
  assert.equal(mensagensEmLote([{ id: '', nome: 'X', itens: [] }, prof]).length, 1);
});

test('visão da coordenação: período, agrupamentos e resumo', async () => {
  const E = await import('../electron/escolaVisao.mjs');
  const painel = { bimestre: { numero: 4, inicio: '2026-10-19', fim: '2026-12-18' }, semPendencia: ['Zé'], professores: [
    { id: '1', nome: 'Ana', itens: [
      { turmaId: 'a', turma: '13.01', componente: 'Química', datas: ['2026-09-10', '2026-10-20', '2026-11-03'], chamada: null, planos: null },
      { turmaId: 'b', turma: '13.02', componente: 'Química', datas: ['2026-09-11'], chamada: null, planos: null }] },
    { id: '2', nome: 'Beto', itens: [{ turmaId: 'a', turma: '13.01', componente: 'Física', datas: ['2026-11-04'], chamada: ['2026-11-04'], planos: { planoAula: 1 } }] },
  ] };
  const bim = E.visaoPeriodo(painel, 'bimestre'); const ano = E.visaoPeriodo(painel, 'ano');
  assert.equal(ano.professores.reduce((s, p) => s + p.total, 0), 7);
  assert.equal(bim.professores.reduce((s, p) => s + p.total, 0), 5);
  assert.deepEqual(bim.professores.map((p) => p.nome), ['Beto', 'Ana']);
  assert.equal(E.visaoPeriodo({ ...painel, bimestre: null }, 'bimestre').periodo, 'ano');
  assert.deepEqual(E.porTurma(bim).map((t) => [t.rotulo, t.total]), [['13.01', 5]]);
  assert.deepEqual(E.porComponente(ano).map((c) => [c.rotulo, c.total]), [['Química', 4], ['Física', 3]]);
  assert.equal(E.resumoDatas(['2026-11-03', '2026-10-20']), '2 aulas · 20/10 a 03/11');
  assert.ok(E.visaoPeriodo({ ...painel, professores: [{ id: '3', nome: 'Caio', itens: [{ turmaId: 'a', turma: 'x', componente: 'y', datas: ['2026-09-01'] }] }] }, 'bimestre').semPendencia.includes('Caio'));
});

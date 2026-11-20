'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const cal = require('../electron/calendario');
const avisos = require('../electron/avisos');

test('Páscoa e feriados móveis de 2026', () => {
  assert.equal(cal.pascoa(2026).toISOString().slice(0, 10), '2026-04-05');
  const f = cal.feriadosDoAno(2026);
  assert.equal(f.get('2026-04-03'), 'Sexta-feira Santa');
  assert.equal(f.get('2026-02-17'), 'Carnaval (terça)');
  assert.equal(f.get('2026-06-04'), 'Corpus Christi');
  assert.equal(f.get('2026-03-18'), 'Autonomia do Tocantins');
});

test('motivoSemAula: feriado, fim de semana, datas próprias e dia letivo extra', () => {
  assert.equal(cal.motivoSemAula('2026-11-20'), 'Consciência Negra');
  assert.equal(cal.motivoSemAula('2026-11-21'), 'Sábado');
  assert.equal(cal.motivoSemAula('2026-11-19'), null);
  const config = { diasSemAula: '2026-11-27 Recesso\n30/11/2026 Reunião pedagógica', diasLetivos: '2026-11-21\n2026-11-20' };
  assert.equal(cal.motivoSemAula('2026-11-27', config), 'Recesso');
  assert.equal(cal.motivoSemAula('2026-11-30', config), 'Reunião pedagógica');
  assert.equal(cal.motivoSemAula('2026-11-21', config), null); // sábado letivo
  assert.equal(cal.motivoSemAula('2026-11-20', config), null); // feriado trabalhado
  assert.deepEqual(cal.soLetivas(['2026-11-19', '2026-11-20', '2026-11-21']), ['2026-11-19']);
});

test('resumoDoDia: aulas dadas hoje e o que falta registrar', () => {
  const painel = { turmas: [
    { nome: '13.02', disciplina: 'Química', pendentes: ['2026-11-19'], modulos: { frequencia: { datas: ['2026-11-19'] } }, totalPendencias: 2 },
    { nome: '33.01', disciplina: 'Química', pendentes: [], modulos: { frequencia: { datas: [] } }, totalPendencias: 0 },
    { nome: '33.03', disciplina: 'Física', pendentes: ['2026-11-19'], modulos: { frequencia: { datas: [] } }, totalPendencias: 1 },
  ] };
  const aulas = [{ turma: '13.02', disciplina: 'Química' }, { turma: '13.02', disciplina: 'Química' }, { turma: '33.01', disciplina: 'Química' }, { turma: '33.03', disciplina: 'Física' }];
  const r = avisos.resumoDoDia(aulas, painel, '2026-11-19');
  assert.deepEqual(r, { aulas: 4, turmas: 3, semConteudo: 2, semChamada: 1 });
  const t = avisos.textoResumoDia(r, painel);
  assert.match(t.corpo, /Você deu 4 aulas hoje e falta registrar o conteúdo de 2 e a chamada de 1/);
  assert.equal(avisos.resumoDoDia([], painel, '2026-11-19'), null);
  assert.match(avisos.textoResumoDia({ aulas: 1, turmas: 1, semConteudo: 0, semChamada: 0 }, { turmas: [] }).titulo, /em dia/);
});

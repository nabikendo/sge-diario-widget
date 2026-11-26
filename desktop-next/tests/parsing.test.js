'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const P = require('../electron/sge/parsing');

const popup = (linhas) => linhas.map(([disc, editado]) =>
  `<tr><td>${disc}</td><td>07:00</td><td>${editado ? '<i class="fa fa-edit"></i>' : '<i class="fa fa-plus-circle"></i>'}</td></tr>`).join('');
const dia = (data, cls, linhas) =>
  `<td class="${cls}" data-original-title="${data}" data-content='<table>${popup(linhas)}</table>'></td>`;

test('conteudos: dia vermelho sem editar na disciplina conta como pendente', () => {
  const html = `<div class="calendario"><table><tr class="semana">
    ${dia('03/11/2026', 'vermelho', [['Química', false], ['Física', true]])}
    ${dia('04/11/2026', 'verde', [['Química', true]])}
    ${dia('05/11/2026', 'vermelho', [['Física', false]])}
    ${dia('30/11/2026', 'vermelho', [['Química', false]])}
  </tr></table></div>`;
  const r = P.conteudos(html, 'Química', '2026-11-19');
  assert.deepEqual(r.pendentes, ['2026-11-03']);      // 05/11 é de Física; 30/11 é futuro
  assert.deepEqual(r.registradas, ['2026-11-04']);
  assert.equal(r.quantidade, 1);
});

test('conteudos: respeita a janela do bimestre', () => {
  const html = `<div class="calendario"><table><tr class="semana">
    ${dia('10/09/2026', 'vermelho', [['Química', false]])}
    ${dia('03/11/2026', 'vermelho', [['Química', false]])}
  </tr></table></div>`;
  const r = P.conteudos(html, 'Química', '2026-11-19', { inicio: '2026-10-01', fim: '2026-12-20' });
  assert.deepEqual(r.pendentes, ['2026-11-03']);
});

test('conteudos: sem calendário é erro, não "tudo em dia"', () => {
  assert.throws(() => P.conteudos('<p>nada</p>', 'Química', '2026-11-19'), /Calendário ausente/);
});

test('turmasDoQuadro cruza o horário com as opções e ignora Diversificada por padrão', () => {
  const opcoes = P.opcoesTurma(`<select id="TurmaContextoId">
    <option value="">Selecione</option>
    <option value="15502_1" data-turmadescricao="33.02" data-escoladescricao="CE JLS" data-tipodaturmaenum="0">33.02</option>
    <option value="17000_1" data-turmadescricao="331.01" data-escoladescricao="CE JLS" data-tipodaturmaenum="1">331.01</option>
  </select>`);
  const quadro = `<table>
    <tr><td>07:00 às 07:50</td><td>Química</td><td>33.02</td></tr>
    <tr><td>07:50 às 08:40</td><td>Química</td><td>33.02</td></tr>
    <tr><td>08:40 às 09:30</td><td>Eletiva</td><td>331.01</td></tr>
  </table>`;
  const turmas = P.turmasDoQuadro(quadro, opcoes);
  assert.equal(turmas.length, 1);
  assert.deepEqual(turmas[0], { id: '15502', nome: '33.02', escola: 'CE JLS', disciplina: 'Química', tipo: '0' });
  assert.equal(P.turmasDoQuadro(quadro, opcoes, new Set(['DIVERSIFICADA'])).length, 2);
});

test('identidadeHorario aceita fragmento embrulhado em JSON', () => {
  const r = P.identidadeHorario(JSON.stringify({ html: '<input id="idProfessor" value="987"><select id="Tipo"><option value="1" selected>Semanal</option></select>' }));
  assert.equal(r.professor, '987');
  assert.equal(r.tipo, '1');
});

test('categoriaTurma', () => {
  assert.equal(P.categoriaTurma('33.02'), 'ESCOLARIZACAO');
  assert.equal(P.categoriaTurma('331.01'), 'DIVERSIFICADA');
  assert.equal(P.categoriaTurma('3301.01'), 'AEE');
});

test('aulasDoQuadro: o quadro é semanal; só entram as aulas do dia da semana pedido', () => {
  const tabela = (dia, linhas) => `<table><thead><tr><th colspan="3"><i class="fa fa-calendar"></i> ${dia} </th></tr>
    <tr><th>Horário</th><th>Componente Curricular</th><th>Turma</th></tr></thead><tbody>
    ${linhas.map(([h, c, t]) => `<tr><td>${h}</td><td>${c}</td><td>${t}</td></tr>`).join('')}</tbody></table>`;
  const html = tabela('Segunda', [['07:00 às 07:50', 'QUÍMICA', '13.01/EM']])
    + tabela('Terça', [['07:50 às 08:40', 'FÍSICA', '33.03/EM']])
    + tabela('Quinta', [['10:35 às 11:25', 'QUÍMICA', '23.02/EM'], ['13:00 às 13:50', 'QUÍMICA', '13.02/EM']]);
  assert.deepEqual(P.aulasDoQuadro(html, '2026-11-26').map((a) => a.turma), ['23.02/EM', '13.02/EM']); // quinta
  assert.deepEqual(P.aulasDoQuadro(html, '2026-11-24').map((a) => a.turma), ['33.03/EM']);             // terça (com acento)
  assert.deepEqual(P.aulasDoQuadro(html, '2026-11-25'), []);                                             // quarta: sem tabela
  assert.deepEqual(P.aulasDoQuadro(html, '2026-11-29'), []);                                             // domingo
  assert.equal(P.aulasDoQuadro(html).length, 4);                                                         // sem data: tudo
});

test('aulasDoQuadro: formato sem tabelas por dia continua lendo todas as linhas', () => {
  const html = '<table><tr><td>07:00 às 07:50</td><td>QUÍMICA</td><td>13.01/EM</td></tr></table>';
  assert.equal(P.aulasDoQuadro(html, '2026-11-26').length, 1);
});

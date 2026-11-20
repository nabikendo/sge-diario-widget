'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const P = require('../electron/sge/parsing');

test('frequencia: dias do cabeçalho dentro da janela', () => {
  const html = `<table id="gridFrequencia"><thead><tr>
    <th class="cabecalhoMes" title="Seg 03/11/2026"></th><th class="cabecalhoMes" title="Qua 05/11/2026"></th>
    <th class="cabecalhoMes" title="Qua 05/11/2026"></th><th class="cabecalhoMes" title="Seg 23/11/2026"></th></tr></thead></table>`;
  assert.deepEqual(P.frequencia(html, '2026-11-20', '2026-11-04'), ['2026-11-05']);
  assert.throws(() => P.frequencia('<p></p>', '2026-11-20'), /Grade de frequência ausente/);
});

test('planos: pendentes = Não Enviado + Reprovado; situação desconhecida é erro', () => {
  const linha = (s) => `<tr><td>x</td><td>${s}</td></tr>`;
  const html = (...ss) => `<table id="tabela_planejamentos"><thead><tr><th>Período</th><th>Situação</th></tr></thead><tbody>${ss.map(linha).join('')}</tbody></table>`;
  assert.deepEqual(P.planos(html('Não Enviado', 'Reprovado', 'Aprovado', 'Aguardando Avaliação')), { total: 4, pendentes: 2, aguardando: 1, aprovados: 1 });
  assert.throws(() => P.planos(html('Rascunho')), /Situação desconhecida/);
});

test('mesesComAula usa o resumo "X de Y registrados"', () => {
  const html = `<div class="calendario">
    <div class="mes"><div class="mes-titulo">Outubro</div><div class="mes-subtitulo">10 de 12 registrados</div>
      <table><tr class="semana"><td title="01/10/2026"></td></tr></table></div>
    <div class="mes"><div class="mes-titulo">Dezembro</div><div class="mes-subtitulo">Nenhum registro a ser feito</div></div>
  </div>`;
  assert.deepEqual([...P.mesesComAula(html)], ['2026-10']);
});

test('janelaOuNull reconhece mês, semana ISO, semestre e datas; nunca estima', () => {
  assert.deepEqual(P.janelaOuNull('Novembro', '202611', 2026), { inicio: '2026-11-01', fim: '2026-11-30' });
  assert.deepEqual(P.janelaOuNull('13ª Semana de 2026', '202613', 2026), { inicio: '2026-03-23', fim: '2026-03-29' });
  assert.deepEqual(P.janelaOuNull('2º Semestre', '2', 2026), { inicio: '2026-07-01', fim: '2026-12-31' });
  assert.deepEqual(P.janelaOuNull('01/08/2026 a 30/09/2026', '9', 2026), { inicio: '2026-08-01', fim: '2026-09-30' });
  assert.equal(P.janelaOuNull('Período especial', '77', 2026), null);
  assert.equal(P.numeroBimestre('3º Bimestre'), 3);
  assert.equal(P.numeroBimestre('Bimestre IV'), 4);
});

test('pendenciasCoordenacao: linhas sem lápis (ou com +), sem datas futuras', () => {
  const dia = (data, linhas) => `<td class="vermelho" data-original-title="${data}" data-content='<table>${linhas}</table>'></td>`;
  const html = `<div class="calendario"><table><tr class="semana">
    ${dia('03/11/2026', '<tr><td>Química</td><td>07:00</td><td><i class="fa fa-plus-circle"></i></td></tr><tr><td>Física</td><td>07:50</td><td><i class="fa fa-edit"></i></td></tr><tr><td>Língua Portuguesa</td><td>08:40</td></tr>')}
    ${dia('30/11/2026', '<tr><td>Química</td><td>07:00</td><td><i class="fa fa-plus"></i></td></tr>')}
  </tr></table></div>`;
  assert.deepEqual(P.pendenciasCoordenacao(html, '2026-11-20'), [{ data: '2026-11-03', disciplina: 'Química', hora: '07:00' }, { data: '2026-11-03', disciplina: 'Língua Portuguesa', hora: '08:40' }]);
});

test('coordenação: professores, perfil e vínculos pelo horário', () => {
  const horario = `<input id="ehPerfilEscola" value="True"><input id="ehPerfilProfessor" value="false">
    <select id="ProfessorId"><option value="">Selecione</option><option value="7">Zélia Souza</option><option value="3">Ana Lima</option></select>`;
  assert.equal(P.perfilAtivo(horario), 'escola');
  assert.deepEqual(P.professores(horario).map((p) => p.nome), ['Ana Lima', 'Zélia Souza']);
  const quadro = `<table><tr><td>07:00 às 07:50</td><td>Química</td><td>33.02/EM.MAT-ANL</td></tr>
    <tr><td>07:50 às 08:40</td><td>Química</td><td>99.99</td></tr></table>`;
  const r = P.analisarVinculos(quadro, { id: '3', nome: 'Ana Lima' }, [{ v: '15502', t: '33.02/EM.MAT-ANL' }]);
  assert.equal(r.vinculos.length, 1);
  assert.equal(r.vinculos[0].turmaId, '15502');
  assert.equal(r.diagnosticos[0].tipo, 'sem_correspondencia');
});

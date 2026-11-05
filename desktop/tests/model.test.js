'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { lerBackup, valor, estado } = require('../model');
const resultado = (modulos) => ({id:'27713',nome:'13.01',escola:'Escola',disciplina:'QUÍMICA',atualizado:'05/11',modulos});
const m = (quantidade, completa) => ({quantidade,completa,detalhe:'',aguardando:0,aprovados:0});
test('preserva desconhecidos e cobertura parcial em backup Android', () => {
  const r=resultado([m(0,true),m(null,false),m(2,false),m(0,true),m(0,true)]);
  const texto=JSON.stringify({formato:'sge-diario-backup',schema:1,criado:'2026-11-05',versao:'2.5.4',dados:{painel_v2:JSON.stringify({turmas:[r]}),painel_coord_v2:JSON.stringify({turmas:[{id:'27713',nome:'13.01',disc:[r]}]})}});
  const b=lerBackup(texto);
  assert.equal(b.pessoal.length,1);assert.equal(b.coordenacao.length,1);
  assert.equal(valor(b.pessoal[0].modulos[1]),'—');assert.equal(valor(b.pessoal[0].modulos[2]),'2*');
  assert.equal(estado(b.pessoal[0]),'pendente');
});
test('recusa contagem negativa e backup de outro formato', () => {
  assert.throws(()=>lerBackup('{}'),/backup compatível/);
  const r=resultado(Array(5).fill(m(-1,true)));
  assert.throws(()=>lerBackup(JSON.stringify({formato:'sge-diario-backup',schema:1,dados:{painel_v2:JSON.stringify({turmas:[r]})}})),/Contagem inválida/);
});

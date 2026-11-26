'use strict';
const test = require('node:test');
const assert = require('node:assert');
const Module = require('node:module');

// plataforma.js importa "electron"; nos testes basta um app falso.
const original = Module._load;
Module._load = function (pedido, ...resto) {
  if (pedido === 'electron') return { app: { isPackaged: false, setLoginItemSettings() {} } };
  return original.call(this, pedido, ...resto);
};
const { linhaExec } = require('../electron/plataforma');
Module._load = original;

test('atalho de início automático põe o caminho entre aspas e abre oculto', () => {
  assert.strictEqual(linhaExec('/opt/SGE Diário Desktop/sge-diario'), '"/opt/SGE Diário Desktop/sge-diario" --oculto');
});

test('atalho escapa aspas, crase, cifrão e barra invertida', () => {
  assert.strictEqual(linhaExec('/tmp/a"b`c$d\\e'), '"/tmp/a\\"b\\`c\\$d\\\\e" --oculto');
});

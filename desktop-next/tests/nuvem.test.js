'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const N = require('../electron/nuvemNucleo');

test('PKCE: o desafio é o SHA-256 do verificador', () => {
  const { verificador, desafio } = N.gerarPkce();
  assert.ok(verificador.length >= 43 && /^[A-Za-z0-9_-]+$/.test(verificador));
  assert.equal(desafio, crypto.createHash('sha256').update(verificador).digest('base64url'));
});

test('endereço de login: só o necessário e só no endereço local', () => {
  const u = new URL(N.urlDeLogin({ porta: 5555, estado: 'abc', desafio: 'xyz' }));
  assert.equal(u.origin, 'https://accounts.google.com');
  assert.equal(u.searchParams.get('redirect_uri'), 'http://127.0.0.1:5555');
  assert.equal(u.searchParams.get('code_challenge_method'), 'S256');
  assert.match(u.searchParams.get('scope'), /drive\.appdata/);
  assert.doesNotMatch(u.searchParams.get('scope'), /auth\/drive(\s|$)/); // nunca o Drive inteiro
});

test('fusão: históricos somados sem repetir; painel mais recente vence', () => {
  const local = { painel: { atualizadoEm: '2026-11-20T10:00:00Z', v: 'local' }, historico: [{ em: '2026-11-18T10:00:00Z', dias: 5 }, { em: '2026-11-20T10:00:00Z', dias: 3 }] };
  const remoto = { painel: { atualizadoEm: '2026-11-19T10:00:00Z', v: 'remoto' }, historico: [{ em: '2026-11-17T10:00:00Z', dias: 9 }, { em: '2026-11-18T10:00:00Z', dias: 5 }] };
  const f = N.fundir(local, remoto);
  assert.equal(f.painel.v, 'local');
  assert.deepEqual(f.historico.map((h) => h.em), ['2026-11-17T10:00:00Z', '2026-11-18T10:00:00Z', '2026-11-20T10:00:00Z']);
  assert.equal(N.fundir({}, { painel: { atualizadoEm: '2026-01-01', v: 'r' } }).painel.v, 'r');
});

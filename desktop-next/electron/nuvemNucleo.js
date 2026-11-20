'use strict';
/** Regras do backup no Google Drive (sem Electron nem rede): PKCE, endereço de login e fusão de cópias. */
const crypto = require('node:crypto');

const CLIENT_ID = '1084707556524-kb7hv01ho61aqqh5ur99sibr7ii7ulhe.apps.googleusercontent.com';
const ESCOPOS = ['https://www.googleapis.com/auth/drive.appdata', 'openid', 'email'];
const ARQUIVO = 'sge-diario-backup.json';

const b64url = (buf) => buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

/** Par verificador/desafio do PKCE (S256). */
function gerarPkce() {
  const verificador = b64url(crypto.randomBytes(48));
  return { verificador, desafio: b64url(crypto.createHash('sha256').update(verificador).digest()) };
}

function urlDeLogin({ porta, estado, desafio }) {
  const p = new URLSearchParams({
    client_id: CLIENT_ID, redirect_uri: `http://127.0.0.1:${porta}`, response_type: 'code', scope: ESCOPOS.join(' '),
    code_challenge: desafio, code_challenge_method: 'S256', state: estado, access_type: 'offline', prompt: 'consent',
  });
  return `https://accounts.google.com/o/oauth2/v2/auth?${p}`;
}

/** Une listas de registros com campo `em`, sem repetir, da mais antiga para a mais nova. */
function unirHistorico(a = [], b = []) {
  const porEm = new Map();
  for (const x of [...a, ...b]) if (x?.em) porEm.set(x.em, x);
  return [...porEm.values()].sort((x, y) => (x.em < y.em ? -1 : 1));
}

const maisNovo = (a, b) => (!a ? b : !b ? a : Date.parse(a.atualizadoEm ?? 0) >= Date.parse(b.atualizadoEm ?? 0) ? a : b);

/** Fusão: históricos somados; painéis ficam com o mais recente. Devolve o que gravar localmente. */
function fundir(local, remoto) {
  return {
    painel: maisNovo(local.painel, remoto.painel) ?? null,
    coordenacao: maisNovo(local.coordenacao, remoto.coordenacao) ?? null,
    historico: unirHistorico(local.historico, remoto.historico),
    historicoCoordenacao: unirHistorico(local.historicoCoordenacao, remoto.historicoCoordenacao),
  };
}

module.exports = { CLIENT_ID, ESCOPOS, ARQUIVO, gerarPkce, urlDeLogin, unirHistorico, fundir };

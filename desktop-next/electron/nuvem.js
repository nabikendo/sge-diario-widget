'use strict';
/**
 * Backup no Google Drive. Guarda uma cópia (painéis e históricos, nunca a sessão do SGE nem senhas) na pasta
 * privada do app no Drive ("appDataFolder"), que só este app enxerga. Login pelo navegador do sistema (OAuth + PKCE).
 */
const { app, shell, safeStorage } = require('electron');
const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs/promises');
const path = require('node:path');
const N = require('./nuvemNucleo');
const armazenamento = require('./armazenamento');

const arquivoToken = () => path.join(app.getPath('userData'), 'nuvem.json');

async function segredoDoCliente() {
  if (process.env.GOOGLE_CLIENT_SECRET) return process.env.GOOGLE_CLIENT_SECRET;
  try { return JSON.parse(await fs.readFile(path.join(__dirname, 'google.segredo.json'), 'utf8')).segredo || null; } catch { return null; }
}
const configurado = async () => Boolean(await segredoDoCliente());

/* ---------- Token guardado, criptografado pelo sistema (DPAPI no Windows, chaveiro no Linux) ---------- */
// No Linux sem chaveiro (GNOME Keyring, KWallet), o Electron cai para "basic_text", que não protege de verdade: aí recusa.
const armazenamentoSeguro = () => safeStorage.isEncryptionAvailable()
  && !(process.platform === 'linux' && safeStorage.getSelectedStorageBackend?.() === 'basic_text');
async function lerSessao() {
  try {
    const bruto = JSON.parse(await fs.readFile(arquivoToken(), 'utf8'));
    if (!armazenamentoSeguro()) return null;
    return { email: bruto.email, ultimoBackup: bruto.ultimoBackup ?? null, refresh: safeStorage.decryptString(Buffer.from(bruto.refresh, 'base64')) };
  } catch { return null; }
}
async function gravarSessao({ refresh, email, ultimoBackup }) {
  if (!armazenamentoSeguro()) throw new Error(process.platform === 'linux'
    ? 'O Linux não ofereceu um chaveiro para guardar o login. Instale e desbloqueie o GNOME Keyring ou o KWallet.'
    : 'O Windows não ofereceu armazenamento seguro para guardar o login.');
  await fs.writeFile(arquivoToken(), JSON.stringify({ email, ultimoBackup: ultimoBackup ?? null, refresh: safeStorage.encryptString(refresh).toString('base64') }));
}

async function trocar(parametros) {
  const segredo = await segredoDoCliente();
  const r = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: N.CLIENT_ID, client_secret: segredo, ...parametros }),
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error_description || j.error || `Google respondeu ${r.status}`);
  return j;
}

async function tokenDeAcesso() {
  const s = await lerSessao();
  if (!s) throw new Error('Entre com o Google primeiro.');
  return (await trocar({ grant_type: 'refresh_token', refresh_token: s.refresh })).access_token;
}

/* ---------- Login ---------- */
let emLogin = null;
function entrar() {
  if (emLogin) return emLogin;
  emLogin = (async () => {
    if (!(await configurado())) throw new Error('O backup no Google Drive não está configurado nesta versão do app.');
    const { verificador, desafio } = N.gerarPkce();
    const estado = crypto.randomBytes(16).toString('hex');
    const servidor = http.createServer();
    await new Promise((ok) => servidor.listen(0, '127.0.0.1', ok));
    const porta = servidor.address().port;
    const codigo = await new Promise((ok, falha) => {
      const tempo = setTimeout(() => falha(new Error('O login demorou demais. Tente de novo.')), 180_000);
      servidor.on('request', (req, res) => {
        const u = new URL(req.url, `http://127.0.0.1:${porta}`);
        if (u.pathname !== '/') { res.writeHead(404).end(); return; }
        const certo = u.searchParams.get('state') === estado && u.searchParams.get('code');
        res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
        res.end(`<meta charset="utf-8"><body style="font-family:Segoe UI,sans-serif;text-align:center;padding:4rem"><h2>${certo ? 'Pronto! Pode fechar esta aba e voltar ao SGE Diário.' : 'Não foi possível entrar. Volte ao SGE Diário e tente de novo.'}</h2>`);
        clearTimeout(tempo);
        certo ? ok(u.searchParams.get('code')) : falha(new Error(u.searchParams.get('error') || 'Login cancelado.'));
      });
      shell.openExternal(N.urlDeLogin({ porta, estado, desafio }));
    }).finally(() => servidor.close());
    const t = await trocar({ grant_type: 'authorization_code', code: codigo, code_verifier: verificador, redirect_uri: `http://127.0.0.1:${porta}` });
    if (!t.refresh_token) throw new Error('O Google não devolveu a permissão de uso contínuo. Tente entrar de novo.');
    const info = await fetch('https://openidconnect.googleapis.com/v1/userinfo', { headers: { authorization: `Bearer ${t.access_token}` } }).then((r) => r.json()).catch(() => ({}));
    await gravarSessao({ refresh: t.refresh_token, email: info.email ?? '' });
    return status();
  })().finally(() => { emLogin = null; });
  return emLogin;
}

async function sair() {
  const s = await lerSessao();
  if (s) await fetch(`https://oauth2.googleapis.com/revoke?token=${encodeURIComponent(s.refresh)}`, { method: 'POST' }).catch(() => {});
  await fs.rm(arquivoToken(), { force: true });
  return status();
}

async function status() {
  const s = await lerSessao();
  return { configurado: await configurado(), conectado: Boolean(s), email: s?.email ?? '', ultimoBackup: s?.ultimoBackup ?? null };
}

/* ---------- Drive (pasta privada do app) ---------- */
const api = async (token, url, opcoes = {}) => {
  const r = await fetch(url, { ...opcoes, headers: { authorization: `Bearer ${token}`, ...(opcoes.headers ?? {}) } });
  if (!r.ok) {
    const j = await r.json().catch(() => ({}));
    const msg = j.error?.message || `Drive respondeu ${r.status}`;
    throw new Error(/has not been used|disabled|accessNotConfigured/i.test(msg) ? 'A Google Drive API ainda não foi ativada no projeto do app.' : msg);
  }
  return r;
};

async function acharArquivo(token) {
  const q = encodeURIComponent(`name='${N.ARQUIVO}'`);
  const j = await (await api(token, `https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=${q}&fields=files(id,modifiedTime)`)).json();
  return j.files?.[0] ?? null;
}

async function copiaLocal() {
  const coord = await armazenamento.lerCoordenacao?.();
  return {
    painel: await armazenamento.lerPainel(), coordenacao: coord ?? null,
    historico: await armazenamento.lerHistorico(), historicoCoordenacao: await armazenamento.lerHistoricoCoordenacao(),
  };
}

async function enviar() {
  const token = await tokenDeAcesso();
  const corpo = JSON.stringify({ formato: 1, origem: 'desktop', enviadoEm: new Date().toISOString(), ...(await copiaLocal()) });
  const existente = await acharArquivo(token);
  if (existente) {
    await api(token, `https://www.googleapis.com/upload/drive/v3/files/${existente.id}?uploadType=media`, { method: 'PATCH', headers: { 'content-type': 'application/json' }, body: corpo });
  } else {
    const limite = 'sgediario' + crypto.randomBytes(6).toString('hex');
    const meta = JSON.stringify({ name: N.ARQUIVO, parents: ['appDataFolder'] });
    await api(token, 'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart', {
      method: 'POST', headers: { 'content-type': `multipart/related; boundary=${limite}` },
      body: `--${limite}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${meta}\r\n--${limite}\r\nContent-Type: application/json\r\n\r\n${corpo}\r\n--${limite}--`,
    });
  }
  const s = await lerSessao();
  await gravarSessao({ ...s, ultimoBackup: new Date().toISOString() });
  return status();
}

async function restaurar() {
  const token = await tokenDeAcesso();
  const arq = await acharArquivo(token);
  if (!arq) throw new Error('Ainda não há backup no seu Google Drive.');
  const remoto = await (await api(token, `https://www.googleapis.com/drive/v3/files/${arq.id}?alt=media`)).json();
  const f = N.fundir(await copiaLocal(), remoto);
  await armazenamento.restaurarCopia(f);
  return { ...(await status()), enviadoEm: remoto.enviadoEm ?? null, origem: remoto.origem ?? null };
}

module.exports = { status, entrar, sair, enviar, restaurar };

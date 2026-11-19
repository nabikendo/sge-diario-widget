'use strict';
/**
 * Cliente HTTP do SGE: porta de `SgeReadClient.request()` (Kotlin/Jsoup) para Electron.
 *
 * Usa `session.fetch()` da partição logada, ou seja, a pilha de rede do Chromium com os mesmos
 * cookies da janela do SGE. Não há cópia manual de cookies nem senha guardada.
 * Somente leitura: o único POST é a busca de frequência, igual ao Android.
 */
const { SGE, CAMINHO_LOGIN } = require('./constantes');

class SessaoExpirada extends Error {
  constructor() { super('Sessão expirada. Entre novamente no SGE.'); this.name = 'SessaoExpirada'; }
}

const ESPERAS_MS = [800, 2000, 4500];

function criarCliente(sessao, { registrar = () => {} } = {}) {
  async function tentar(fn) {
    let ultimoErro;
    for (let i = 0; i <= ESPERAS_MS.length; i++) {
      try { return await fn(); } catch (e) {
        // Sessão expirada e respostas inválidas não melhoram com nova tentativa: só falha de rede repete.
        if (e instanceof SessaoExpirada || e.naoRepetir) throw e;
        ultimoErro = e;
        if (i < ESPERAS_MS.length) await new Promise((r) => setTimeout(r, ESPERAS_MS[i]));
      }
    }
    throw ultimoErro;
  }

  /**
   * @param {string} caminho  ex.: '/Conteudo/CarregarDadosConteudoAplicado'
   * @param {Record<string,string>} params
   * @param {{post?: boolean, ajax?: boolean}} opcoes
   * @returns {Promise<string>} corpo da resposta (HTML ou JSON em texto)
   */
  async function requisicao(caminho, params = {}, { post = false, ajax = true } = {}) {
    const corpo = new URLSearchParams(params);
    const url = new URL(caminho, SGE);
    if (!post) url.search = corpo.toString();
    const inicio = Date.now();
    const resposta = await tentar(async () => {
      try {
        return await sessao.fetch(url.toString(), {
          method: post ? 'POST' : 'GET',
          credentials: 'include',
          headers: {
            ...(ajax ? { 'X-Requested-With': 'XMLHttpRequest' } : {}),
            ...(post ? { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' } : {}),
          },
          body: post ? corpo.toString() : undefined,
          signal: AbortSignal.timeout(30_000),
        });
      } catch (e) {
        throw new Error(`Falha de rede em ${caminho}: ${e.message}`);
      }
    });
    const texto = await resposta.text();
    registrar({ caminho, status: resposta.status, ms: Date.now() - inicio, bytes: texto.length });

    if (new URL(resposta.url).pathname.toLowerCase().includes(CAMINHO_LOGIN.toLowerCase())) throw new SessaoExpirada();
    if (texto.includes('id="login-form"') || texto.includes('id="btn-entrar"')) throw new SessaoExpirada();
    if (!resposta.ok) throw Object.assign(new Error(`SGE respondeu HTTP ${resposta.status} em ${caminho}`), { naoRepetir: true });
    if (!texto.trim()) throw Object.assign(new Error(`SGE retornou resposta vazia em ${caminho}`), { naoRepetir: true });
    return texto;
  }

  /** Atalho para rotas que devolvem JSON. */
  async function json(caminho, params, opcoes) {
    const texto = await requisicao(caminho, params, opcoes);
    try { return JSON.parse(texto); } catch {
      throw Object.assign(new Error(`Resposta não é JSON em ${caminho}`), { naoRepetir: true });
    }
  }

  return { requisicao, json };
}

module.exports = { criarCliente, SessaoExpirada };

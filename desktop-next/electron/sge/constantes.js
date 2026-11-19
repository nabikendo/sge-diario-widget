'use strict';

/** Endereço do SGE 3.0 (Seduc-TO). Mesmo usado pelo app Android. */
const SGE = 'https://sge.seduc.to.pontoid.com.br';
/** Partição persistente: os cookies do login ficam aqui (a mesma do desktop atual, então quem já entrou continua logado). */
const PARTICAO = 'persist:sge-oficial';
const CAMINHO_LOGIN = '/Acesso/Entrar';

module.exports = { SGE, PARTICAO, CAMINHO_LOGIN };

'use strict';
/**
 * Motor de sincronização: junta janela oculta + cliente HTTP + leitor e entrega à interface
 * um "painel" já limpo. A interface nunca vê HTML do SGE.
 */
const { app, session } = require('electron');
const fs = require('node:fs/promises');
const path = require('node:path');
const { PARTICAO } = require('./sge/constantes');
const { criarCliente, SessaoExpirada } = require('./sge/cliente');
const { criarLeitor } = require('./sge/leitor');
const janelaOculta = require('./sge/janelaOculta');

const arquivoCache = () => path.join(app.getPath('userData'), 'painel.json');

async function lerCache() {
  try { return JSON.parse(await fs.readFile(arquivoCache(), 'utf8')); } catch { return null; }
}
async function gravarCache(painel) {
  const destino = arquivoCache();
  await fs.mkdir(path.dirname(destino), { recursive: true });
  await fs.writeFile(destino + '.tmp', JSON.stringify(painel, null, 2));
  await fs.rename(destino + '.tmp', destino); // gravação atômica
}

/** Executa tarefas com no máximo `limite` ao mesmo tempo (para não sobrecarregar o SGE). */
async function emParalelo(itens, limite, fn) {
  const resultados = new Array(itens.length);
  let proximo = 0;
  await Promise.all(Array.from({ length: Math.min(limite, itens.length) }, async () => {
    while (proximo < itens.length) { const i = proximo++; resultados[i] = await fn(itens[i], i); }
  }));
  return resultados;
}

let emAndamento = null;

/**
 * @param {(p: {etapa: string, feitas: number, total: number|null}) => void} aoProgresso
 */
function sincronizar(aoProgresso = () => {}) {
  if (emAndamento) return emAndamento; // duas chamadas viram uma só
  emAndamento = (async () => {
    const sessao = session.fromPartition(PARTICAO);
    const cliente = criarCliente(sessao);
    const leitor = criarLeitor(cliente);

    aoProgresso({ etapa: 'Conferindo a sessão do SGE', feitas: 0, total: null });
    const inicial = await janelaOculta.lerPaginaInicial();
    if (!inicial.logado) throw new SessaoExpirada();

    aoProgresso({ etapa: 'Lendo suas turmas', feitas: 0, total: null });
    const { perfilEscola, turmas } = await leitor.turmas(inicial.opcoesTurma);
    const bimestre = await leitor.bimestreAtual(turmas[0].id);

    let feitas = 0;
    aoProgresso({ etapa: 'Conferindo o Conteúdo Aplicado', feitas, total: turmas.length });
    const resultado = await emParalelo(turmas, 2, async (t) => {
      let item;
      try {
        const c = await leitor.conteudos(t, bimestre);
        const total = c.pendentes.length + c.registradas.length;
        item = { ...t, pendentes: c.pendentes, registradas: c.registradas.length,
          progresso: total ? c.registradas.length / total : 1, status: c.pendentes.length ? 'pendente' : 'em-dia' };
      } catch (e) {
        if (e instanceof SessaoExpirada) throw e;
        item = { ...t, pendentes: [], registradas: 0, progresso: 0, status: 'erro', erro: e.message };
      }
      aoProgresso({ etapa: 'Conferindo o Conteúdo Aplicado', feitas: ++feitas, total: turmas.length });
      return item;
    });

    const painel = { versao: 1, atualizadoEm: new Date().toISOString(), perfil: perfilEscola ? 'coordenacao' : 'professor', bimestre, turmas: resultado };
    await gravarCache(painel);
    return painel;
  })().finally(() => { emAndamento = null; });
  return emAndamento;
}

module.exports = { sincronizar, lerCache, SessaoExpirada };

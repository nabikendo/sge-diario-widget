'use strict';
/** Ponte mínima e congelada entre a interface (sem Node) e o processo principal. */
const { contextBridge, ipcRenderer } = require('electron');

const ouvir = (canal) => (fn) => {
  const h = (_e, dados) => fn(dados);
  ipcRenderer.on(canal, h);
  return () => ipcRenderer.removeListener(canal, h); // para o useEffect desligar
};

contextBridge.exposeInMainWorld('sgeDesktop', Object.freeze({
  obterPainel: () => ipcRenderer.invoke('painel:obter'),
  sincronizar: () => ipcRenderer.invoke('sync:agora'),
  lerPeriodo: (n) => ipcRenderer.invoke('professor:periodo', n),
  entrar: () => ipcRenderer.invoke('sge:entrar'),
  abrirSge: (atalho) => ipcRenderer.invoke('sge:abrirWeb', atalho ?? null),
  sairDoSge: () => ipcRenderer.invoke('sge:sair'),
  obterCoordenacao: () => ipcRenderer.invoke('coord:obter'),
  sincronizarEscola: () => ipcRenderer.invoke('coord:sincronizar'),
  verificarAtualizacao: () => ipcRenderer.invoke('app:verificar'),
  obterAgenda: () => ipcRenderer.invoke('agenda:obter'),
  aoAgenda: ouvir('agenda:pronta'),
  obterHistoricoCoordenacao: () => ipcRenderer.invoke('coord:historico'),
  instalarAtualizacao: () => ipcRenderer.invoke('app:atualizar'),
  aoPerfil: ouvir('sge:perfil'),
  aoCoordenacao: ouvir('coord:concluida'),
  aoTurmasEscola: ouvir('coord:turmas'),
  aoFimSync: ouvir('sync:fim'),
  aoAtualizacao: ouvir('app:atualizacao'),
  obterHistorico: () => ipcRenderer.invoke('historico:obter'),
  obterConfig: () => ipcRenderer.invoke('config:obter'),
  salvarConfig: (parcial) => ipcRenderer.invoke('config:salvar', parcial),
  abrirPastaDados: () => ipcRenderer.invoke('dados:abrirPasta'),
  apagarDados: () => ipcRenderer.invoke('dados:apagar'),
  salvarRelatorio: (formato, opcoes) => ipcRenderer.invoke('relatorio:salvar', formato, opcoes),
  obterPerfil: () => ipcRenderer.invoke('perfil:obter'),
  aoProgresso: ouvir('sync:progresso'),
  aoConcluir: ouvir('sync:concluida'),
  aoSessao: ouvir('sge:sessao'),
  aoErro: ouvir('sge:erro'),
}));

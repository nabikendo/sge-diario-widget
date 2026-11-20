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
  entrar: () => ipcRenderer.invoke('sge:entrar'),
  abrirSgeWeb: () => ipcRenderer.invoke('sge:abrirWeb'),
  sairDoSge: () => ipcRenderer.invoke('sge:sair'),
  obterHistorico: () => ipcRenderer.invoke('historico:obter'),
  obterConfig: () => ipcRenderer.invoke('config:obter'),
  salvarConfig: (parcial) => ipcRenderer.invoke('config:salvar', parcial),
  abrirPastaDados: () => ipcRenderer.invoke('dados:abrirPasta'),
  apagarDados: () => ipcRenderer.invoke('dados:apagar'),
  salvarRelatorio: (formato) => ipcRenderer.invoke('relatorio:salvar', formato),
  aoProgresso: ouvir('sync:progresso'),
  aoConcluir: ouvir('sync:concluida'),
  aoSessao: ouvir('sge:sessao'),
  aoErro: ouvir('sge:erro'),
}));

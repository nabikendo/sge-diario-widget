/**
 * Acesso da interface ao motor. No Electron, usa a ponte do preload (window.sgeDesktop).
 * Aberto no navegador (npm run build:ui + vite preview, ou testes de layout), usa dados de exemplo.
 */
import { exemploPainel } from './exemplo';

const ponte = typeof window !== 'undefined' ? window.sgeDesktop : undefined;
const nada = () => () => {};

export const sge = ponte ?? {
  obterPainel: async () => exemploPainel,
  sincronizar: async () => exemploPainel,
  entrar: async () => {},
  abrirSgeWeb: async () => {},
  aoProgresso: nada, aoConcluir: nada, aoSessao: nada, aoErro: nada,
};
export const modoExemplo = !ponte;

/** "agora", "há 5 min", "há 2 h", "ontem"… */
export function tempoRelativo(iso, agora = Date.now()) {
  if (!iso) return 'nunca';
  const min = Math.round((agora - Date.parse(iso)) / 60000);
  if (min < 1) return 'agora';
  if (min < 60) return `há ${min} min`;
  const h = Math.round(min / 60);
  if (h < 24) return `há ${h} h`;
  return h < 48 ? 'ontem' : `há ${Math.round(h / 24)} dias`;
}

/** "2026-11-19" → "19/11". */
export const diaMes = (iso) => iso.slice(8, 10) + '/' + iso.slice(5, 7);

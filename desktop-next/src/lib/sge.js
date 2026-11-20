/**
 * Acesso da interface ao motor. No Electron, usa a ponte do preload (window.sgeDesktop).
 * Aberto no navegador (npm run build:ui + vite preview, ou testes de layout), usa dados de exemplo.
 */
import { exemploPainel, exemploHistorico, exemploCoordenacao, exemploHistoricoCoordenacao, exemploAgenda } from './exemplo';

const ponte = typeof window !== 'undefined' ? window.sgeDesktop : undefined;
const nada = () => () => {};

const CONFIG_EXEMPLO = { introVista: !location.search.includes('intro'), tema: 'sistema', fecharNaBandeja: true, notificarNovas: true, avisoFimDia: true, horaAviso: '17:30', coordChamada: true, coordPlanos: false };
export const sge = ponte ?? {
  obterPainel: async () => (location.search.includes('novo') ? null : exemploPainel),
  sincronizar: async () => exemploPainel,
  lerPeriodo: async (n) => ({ ...exemploPainel, periodo: n, bimestre: { ...exemploPainel.bimestre, numero: n } }),
  entrar: async () => {},
  abrirSge: async () => {},
  obterCoordenacao: async () => ({ painel: exemploCoordenacao, turmasEscola: 24 }),
  sincronizarEscola: async () => exemploCoordenacao,
  instalarAtualizacao: async () => {},
  verificarAtualizacao: async () => {},
  obterPerfil: async () => (location.search.includes('escola') ? 'escola' : null),
  obterAgenda: async () => exemploAgenda,
  aoAgenda: nada,
  obterHistoricoCoordenacao: async () => exemploHistoricoCoordenacao,
  aoPerfil: nada, aoCoordenacao: nada, aoTurmasEscola: nada, aoFimSync: nada, aoAtualizacao: nada,
  sairDoSge: async () => {},
  obterHistorico: async () => exemploHistorico,
  obterConfig: async () => ({ ...CONFIG_EXEMPLO, intervaloMin: 30, incluirDiversificada: false, incluirAee: false, abrirComWindows: false, abrirMaximizado: true, versao: 'exemplo', pastaDados: '' }),
  salvarConfig: async (c) => ({ ...CONFIG_EXEMPLO, intervaloMin: 30, incluirDiversificada: false, incluirAee: false, abrirComWindows: false, abrirMaximizado: true, versao: 'exemplo', ...c }),
  abrirPastaDados: async () => {},
  apagarDados: async () => {},
  salvarRelatorio: async () => ({ ok: false, mensagem: 'Disponível só no app instalado.' }),
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

/** Data por extenso curta: "2026-11-19" → "qui., 19/11". */
export const diaSemana = (iso) => new Date(iso + 'T12:00:00').toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' });

/** Módulos do diário, na ordem do SGE. `modulo` é o índice usado pelo atalho do SGE Web. */
export const MODULOS = [
  { chave: 'conteudo', rotulo: 'Conteúdo', modulo: 0, unidade: ['dia sem conteúdo', 'dias sem conteúdo'] },
  { chave: 'frequencia', rotulo: 'Chamada', modulo: 1, unidade: ['dia sem chamada', 'dias sem chamada'] },
  { chave: 'planoAula', rotulo: 'Plano de aula', modulo: 2, unidade: ['plano pendente', 'planos pendentes'] },
  { chave: 'planoCurso', rotulo: 'Plano de curso', modulo: 3, unidade: ['plano pendente', 'planos pendentes'] },
  { chave: 'pei', rotulo: 'PEI', modulo: 4, unidade: ['PEI pendente', 'PEIs pendentes'] },
];

/** Atalho para o SGE Web abrir direto na tela do módulo da turma. */
export const atalho = (t, modulo, datas = []) => ({ turmaId: t.id, turma: t.nome, componente: t.disciplina, modulo, datas });

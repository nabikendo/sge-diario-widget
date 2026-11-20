import { Home, Users, School, History, FileText, Settings, Search, RefreshCw, LogIn, BookOpenCheck } from 'lucide-react';
import { tempoRelativo } from '../lib/sge';

export const SECOES = [
  { id: 'home', rotulo: 'Home', icone: Home },
  { id: 'turmas', rotulo: 'Turmas', icone: Users },
  { id: 'coordenacao', rotulo: 'Coordenação', icone: School },
  { id: 'historico', rotulo: 'Histórico', icone: History },
  { id: 'relatorios', rotulo: 'Relatórios', icone: FileText },
];

/** Menu lateral: ícone + rótulo, item ativo em "pílula" de vidro. */
export function BarraLateral({ ativa, aoTrocar }) {
  const Item = ({ id, rotulo, icone: Icone }) => {
    const sel = ativa === id;
    return (
      <button
        type="button"
        onClick={() => aoTrocar(id)}
        aria-current={sel ? 'page' : undefined}
        className={`flex w-[76px] flex-col items-center gap-1 rounded-2xl px-2 py-2.5 text-[11.5px] font-medium transition duration-200 ease-(--ease-padrao)
          ${sel ? 'vidro-forte text-tinta shadow-[0_6px_18px_-8px_rgb(60_45_140/0.35)]' : 'text-tinta-suave hover:bg-white/40 hover:text-tinta'}`}
      >
        <Icone size={22} strokeWidth={sel ? 2.2 : 1.8} aria-hidden />
        {rotulo}
      </button>
    );
  };
  return (
    <nav aria-label="Seções" className="flex h-full flex-col items-center gap-1.5 pt-2 pb-4">
      {SECOES.map((s) => <Item key={s.id} {...s} />)}
      <div className="flex-1" />
      <Item id="configuracoes" rotulo="Configurações" icone={Settings} />
    </nav>
  );
}

/** Barra superior: título, busca global e botão de status da sincronização. */
export function BarraSuperior({ titulo, subtitulo, busca, aoBuscar, refBusca, painel, sincronizando, logado, aoSincronizar, aoEntrar, aoAbrirSge }) {
  return (
    <header className="grid grid-cols-[1fr_minmax(220px,400px)_1fr] items-center gap-6">
      <div className="min-w-0">
        <h1 className="truncate text-[28px] leading-tight font-semibold tracking-tight">{titulo}</h1>
        {subtitulo && <p className="truncate text-sm text-tinta-suave">{subtitulo}</p>}
      </div>

      <label className="vidro-forte flex h-12 items-center gap-3 rounded-full px-5 text-tinta-suave focus-within:ring-2 focus-within:ring-lavanda/40">
        <Search size={18} aria-hidden />
        <input
          ref={refBusca}
          value={busca}
          onChange={(e) => aoBuscar(e.target.value)}
          placeholder="Buscar turma, disciplina ou escola"
          className="w-full bg-transparent text-[15px] text-tinta outline-none placeholder:text-tinta-suave/70"
        />
        <kbd className="rounded-md bg-tinta/5 px-1.5 py-0.5 text-xs">/</kbd>
      </label>

      <div className="flex justify-end gap-2">
        <button type="button" onClick={aoAbrirSge} title="Abrir o SGE dentro do app para fazer o diário"
          className="flex h-12 items-center gap-2 rounded-full bg-lavanda px-5 text-sm font-semibold text-white shadow-[0_8px_20px_-8px_rgb(107_84_238/0.7)] transition hover:brightness-110 active:scale-[0.98]">
          <BookOpenCheck size={18} aria-hidden /> Fazer diário
        </button>
        {logado === false ? (
          <button type="button" onClick={aoEntrar}
            className="flex h-12 items-center gap-2 rounded-full bg-tinta px-5 text-sm font-semibold text-white shadow-lg transition hover:bg-black active:scale-[0.98]">
            <LogIn size={18} aria-hidden /> Entrar no SGE
          </button>
        ) : (
          <button type="button" onClick={aoSincronizar} disabled={sincronizando}
            className="vidro-forte flex h-12 items-center gap-2.5 rounded-full px-5 text-sm font-medium transition hover:bg-white active:scale-[0.98] disabled:cursor-default">
            <span className={`size-2 rounded-full ${sincronizando ? 'bg-lavanda animate-pulse' : painel ? 'bg-emdia' : 'bg-tinta-suave/40'}`} aria-hidden />
            {sincronizando ? 'Sincronizando…' : painel ? `Sincronizado ${tempoRelativo(painel.atualizadoEm)}` : 'Sincronizar'}
            <RefreshCw size={16} className={`text-tinta-suave ${sincronizando ? 'animate-spin' : ''}`} aria-hidden />
          </button>
        )}
      </div>
    </header>
  );
}

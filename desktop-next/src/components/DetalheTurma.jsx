import { useEffect } from 'react';
import { X, ExternalLink, CheckCircle2 } from 'lucide-react';
import { diaSemana } from '../lib/sge';

/** Painel lateral com os detalhes de uma turma (datas sem conteúdo). Fecha com Esc ou clicando fora. */
export default function DetalheTurma({ turma, aoFechar, aoAbrirSge }) {
  useEffect(() => {
    const tecla = (e) => e.key === 'Escape' && aoFechar();
    window.addEventListener('keydown', tecla);
    return () => window.removeEventListener('keydown', tecla);
  }, [aoFechar]);
  if (!turma) return null;
  const pct = Math.round((turma.progresso ?? 0) * 100);
  const pendentes = [...(turma.pendentes ?? [])].reverse(); // mais recentes primeiro

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-tinta/15 backdrop-blur-[2px]" onClick={aoFechar}>
      <aside
        role="dialog" aria-modal="true" aria-label={`Turma ${turma.nome}`}
        onClick={(e) => e.stopPropagation()}
        className="vidro-forte deslizar m-3 flex w-[min(440px,92vw)] flex-col rounded-[28px] p-6 shadow-2xl"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm text-tinta-suave">{turma.disciplina}</p>
            <h2 className="text-2xl font-semibold tracking-tight break-words">Turma {turma.nome}</h2>
            <p className="text-xs text-tinta-suave">{turma.escola}</p>
          </div>
          <button type="button" onClick={aoFechar} aria-label="Fechar" className="rounded-full p-2 text-tinta-suave hover:bg-tinta/5"><X size={20} /></button>
        </div>

        <div className="mt-5 grid grid-cols-3 gap-2 text-center">
          <Numero valor={turma.registradas ?? 0} rotulo="registradas" />
          <Numero valor={turma.pendentes?.length ?? 0} rotulo="sem conteúdo" cor={turma.pendentes?.length ? 'text-pendente' : undefined} />
          <Numero valor={turma.status === 'erro' ? '—' : `${pct}%`} rotulo="em dia" />
        </div>

        <h3 className="mt-6 mb-2 text-sm font-semibold">Dias sem conteúdo aplicado</h3>
        <div className="min-h-0 flex-1 overflow-y-auto rounded-2xl bg-white/60 p-2">
          {turma.status === 'erro' ? (
            <p className="p-3 text-sm text-alerta">{turma.erro ?? 'Não foi possível ler esta turma.'}</p>
          ) : pendentes.length === 0 ? (
            <p className="flex items-center gap-2 p-3 text-sm text-emdia"><CheckCircle2 size={18} /> Nenhuma pendência neste bimestre.</p>
          ) : (
            <ul>
              {pendentes.map((d) => (
                <li key={d} className="flex items-center justify-between rounded-xl px-3 py-2.5 text-sm capitalize odd:bg-white/70">
                  {diaSemana(d)}
                  <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs font-medium text-pendente normal-case">sem conteúdo</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <button type="button" onClick={aoAbrirSge}
          className="mt-4 flex h-11 items-center justify-center gap-2 rounded-full bg-tinta text-sm font-semibold text-white transition hover:bg-black active:scale-[0.98]">
          <ExternalLink size={16} /> Abrir o SGE para registrar
        </button>
      </aside>
    </div>
  );
}

function Numero({ valor, rotulo, cor = 'text-tinta' }) {
  return (
    <div className="rounded-2xl bg-white/70 px-2 py-3">
      <b className={`block text-2xl tabular-nums ${cor}`}>{valor}</b>
      <span className="text-xs text-tinta-suave">{rotulo}</span>
    </div>
  );
}

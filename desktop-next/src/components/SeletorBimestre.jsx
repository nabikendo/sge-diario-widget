import { Loader2 } from 'lucide-react';

/** 1º a 4º bimestre para o professor. O atual vem marcado; os outros são lidos do SGE só para consulta. */
export default function SeletorBimestre({ atual, escolhido, lendo, aoEscolher }) {
  return (
    <div className="mb-5">
      <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Bimestre">
        {[1, 2, 3, 4].map((n) => {
          const sel = escolhido === n;
          return (
            <button key={n} type="button" disabled={lendo} onClick={() => aoEscolher(n)} aria-pressed={sel}
              className={`rounded-full px-4 py-2 text-sm font-medium transition disabled:opacity-60 ${sel ? 'bg-tinta text-white' : 'bg-white/70 text-tinta-suave ring-1 ring-white hover:bg-white'}`}>
              {n}º bimestre{n === atual && <span className="ml-1.5 inline-block size-1.5 rounded-full bg-emdia align-middle" aria-label="bimestre atual" />}
            </button>
          );
        })}
        {lendo && <span className="flex items-center gap-2 pl-2 text-sm text-tinta-suave" role="status"><Loader2 size={16} className="animate-spin" aria-hidden /> Lendo no SGE…</span>}
      </div>
      {escolhido !== atual && !lendo && <p className="mt-2 text-xs text-tinta-suave">Consulta do {escolhido}º bimestre: Conteúdo e Chamada. Os planos aparecem só no bimestre atual, e os avisos e o histórico seguem o atual.</p>}
    </div>
  );
}

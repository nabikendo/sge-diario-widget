import { AlertTriangle, CheckCircle2, CircleDashed, ChevronRight } from 'lucide-react';
import { diaMes, MODULOS } from '../lib/sge';

/** Cores por estado: o verde sempre significa "em dia" (igual ao app Android). */
const ESTADOS = {
  'em-dia':   { rotulo: 'Em dia',   icone: CheckCircle2,  chip: 'bg-emerald-50/90 text-emdia ring-emerald-200/70',  barra: 'from-emerald-400 to-teal-400' },
  'pendente': { rotulo: 'Pendente', icone: CircleDashed,  chip: 'bg-rose-50/90 text-pendente ring-rose-200/70',     barra: 'from-violet-500 to-fuchsia-400' },
  'erro':     { rotulo: 'Não lido', icone: AlertTriangle, chip: 'bg-amber-50/90 text-alerta ring-amber-200/70',     barra: 'from-amber-300 to-amber-400' },
};

/**
 * Card de Turma com efeito vidro.
 * @param {{ turma: {id:string,nome:string,escola:string,disciplina:string,registradas:number,pendentes:string[],progresso:number,status:'em-dia'|'pendente'|'erro',erro?:string},
 *           indice?: number, aoAbrir?: (turma) => void }} props
 */
export default function CartaoTurma({ turma, indice = 0, aoAbrir }) {
  const estado = ESTADOS[turma.status] ?? ESTADOS.erro;
  const Icone = estado.icone;
  const pct = Math.round((turma.progresso ?? 0) * 100);
  const qtd = turma.pendentes?.length ?? 0;

  return (
    <button
      type="button"
      onClick={() => aoAbrir?.(turma)}
      // Cascata curta: no máximo 8 cartões escalonados, 40 ms entre eles.
      style={{ '--atraso': `${Math.min(indice, 8) * 40}ms` }}
      className="surgir vidro group w-full rounded-3xl p-5 text-left transition duration-200 ease-(--ease-padrao)
                 hover:-translate-y-0.5 hover:bg-white/75 hover:shadow-[0_18px_40px_-16px_rgb(60_45_140/0.28)]
                 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-lavanda active:scale-[0.99]"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[13px] font-medium text-tinta-suave">{turma.disciplina}</p>
          <h3 className="mt-0.5 truncate text-2xl font-semibold tracking-tight">Turma {turma.nome}</h3>
          <p className="mt-0.5 truncate text-xs text-tinta-suave/80">{turma.escola}</p>
        </div>
        <span className={`inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${estado.chip}`}>
          <Icone size={14} strokeWidth={2.4} aria-hidden />
          {estado.rotulo}
        </span>
      </div>

      {/* Barra de progresso do bimestre: aulas com conteúdo registrado ÷ aulas dadas. */}
      <div className="mt-5">
        <div className="mb-1.5 flex items-baseline justify-between text-xs">
          <span className="text-tinta-suave">Conteúdo registrado no bimestre</span>
          <span className="font-semibold tabular-nums">{turma.status === 'erro' ? '—' : `${pct}%`}</span>
        </div>
        <div className="h-2.5 overflow-hidden rounded-full bg-tinta/[0.07]">
          <div
            className={`h-full rounded-full bg-gradient-to-r ${estado.barra} transition-[width] duration-500 ease-(--ease-entrada)`}
            style={{ width: `${turma.status === 'erro' ? 100 : Math.max(pct, 3)}%` }}
          />
        </div>
      </div>

      {/* Módulos do diário: número de pendências de cada um (— = não lido, oculto se o SGE não tem o tipo). */}
      {turma.contagens && turma.status !== 'erro' && (
        <div className="mt-4 flex flex-wrap gap-1.5">
          {MODULOS.filter((m) => !turma.modulos?.[m.chave]?.indisponivel).map((m) => {
            const n = turma.contagens[m.chave];
            const cor = n == null ? 'bg-amber-50 text-alerta' : n > 0 ? 'bg-rose-50 text-pendente' : 'bg-emerald-50/80 text-emdia';
            return (
              <span key={m.chave} className={`rounded-full px-2.5 py-1 text-xs font-medium ${cor}`} title={turma.modulos?.[m.chave]?.erro}>
                {m.rotulo} <b className="tabular-nums">{n == null ? '—' : n}</b>
              </span>
            );
          })}
        </div>
      )}

      <div className="mt-3 flex items-center justify-between gap-3 text-sm">
        {turma.status === 'erro' ? (
          <p className="line-clamp-2 text-xs text-alerta">{turma.erro ?? 'Não foi possível ler esta turma.'}</p>
        ) : qtd === 0 ? (
          <p className="text-tinta-suave"><span className="font-semibold text-tinta tabular-nums">{turma.registradas}</span> aulas com conteúdo</p>
        ) : (
          <p className="truncate text-tinta-suave">
            <span className="font-semibold text-pendente tabular-nums">{qtd} {qtd === 1 ? 'dia' : 'dias'}</span>
            {' '}sem conteúdo · {turma.pendentes.slice(0, 3).map(diaMes).join(', ')}{qtd > 3 ? '…' : ''}
          </p>
        )}
        <ChevronRight size={18} className="shrink-0 text-tinta-suave/60 transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden />
      </div>
    </button>
  );
}

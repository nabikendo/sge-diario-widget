import { Sparkles, CalendarDays, RefreshCcwDot } from 'lucide-react';
import BarraSegmentada from './BarraSegmentada';
import { tempoRelativo, diaMes } from '../lib/sge';

/** Card principal (topo esquerdo): resumo das pendências do professor, no estilo "Happening now". */
export function CardResumo({ painel }) {
  const turmas = painel?.turmas ?? [];
  const dias = turmas.reduce((s, t) => s + (t.pendentes?.length ?? 0), 0);
  const comPendencia = turmas.filter((t) => t.status === 'pendente').length;
  const emDia = turmas.filter((t) => t.status === 'em-dia').length;
  const maisAntiga = turmas.flatMap((t) => t.pendentes ?? []).sort()[0];

  return (
    <section className="surgir relative overflow-hidden rounded-3xl p-7 shadow-[0_12px_32px_-12px_rgb(60_45_140/0.25)] ring-1 ring-white/70"
      style={{ background: 'linear-gradient(135deg, rgb(238 233 255 / .95) 0%, rgb(232 215 252 / .9) 45%, rgb(214 226 255 / .9) 100%)' }}>
      <p className="text-xs font-bold tracking-[0.12em] text-lavanda uppercase">Pendências gerais</p>
      <div className="mt-2 flex items-end gap-3">
        <span className="text-6xl leading-none font-semibold tracking-tight tabular-nums">{dias}</span>
        <span className="pb-1.5 text-lg text-tinta-suave">{dias === 1 ? 'dia sem conteúdo' : 'dias sem conteúdo'}</span>
      </div>
      <p className="mt-2 text-[15px] text-tinta-suave">
        {dias === 0 ? 'Tudo em dia. Bom trabalho!' : `Em ${comPendencia} ${comPendencia === 1 ? 'turma' : 'turmas'}${maisAntiga ? ` · a mais antiga é de ${diaMes(maisAntiga)}` : ''}.`}
      </p>
      <div className="mt-6 flex flex-wrap gap-2">
        <Indicador valor={comPendencia} rotulo="com pendência" cor="text-pendente" />
        <Indicador valor={emDia} rotulo="em dia" cor="text-emdia" />
        <Indicador valor={turmas.length} rotulo="turmas" />
      </div>
      <Sparkles className="pointer-events-none absolute -right-3 -bottom-3 text-lavanda/15" size={140} strokeWidth={1.2} aria-hidden />
    </section>
  );
}

function Indicador({ valor, rotulo, cor = 'text-tinta' }) {
  return (
    <span className="rounded-full bg-white/70 px-3.5 py-1.5 text-sm ring-1 ring-white">
      <b className={`tabular-nums ${cor}`}>{valor}</b> <span className="text-tinta-suave">{rotulo}</span>
    </span>
  );
}

/** Card secundário (topo direito): última sincronização + progresso do bimestre. */
export function CardSincronizacao({ painel, progresso, sincronizando }) {
  const b = painel?.bimestre;
  const hoje = Date.now();
  const pctBimestre = b ? Math.min(1, Math.max(0, (hoje - Date.parse(b.inicio)) / (Date.parse(b.fim) - Date.parse(b.inicio)))) : null;
  const pctSync = progresso?.total ? progresso.feitas / progresso.total : null;

  return (
    <section className="surgir vidro rounded-3xl p-6" style={{ '--atraso': '40ms' }}>
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-[15px] font-semibold"><RefreshCcwDot size={18} className="text-lavanda" aria-hidden /> Sincronização com o SGE</h2>
        <span className="text-xs text-tinta-suave">{painel ? tempoRelativo(painel.atualizadoEm) : 'nunca'}</span>
      </div>

      <div className="mt-4">
        {sincronizando ? (
          <>
            <p className="mb-2 text-sm text-tinta-suave">{progresso?.etapa ?? 'Sincronizando…'}{progresso?.total ? ` · ${progresso.feitas} de ${progresso.total}` : ''}</p>
            <BarraSegmentada progresso={pctSync} rotulo="Progresso da sincronização" />
          </>
        ) : (
          <p className="text-sm text-tinta-suave">
            {painel ? <>Última leitura em <b className="text-tinta">{new Date(painel.atualizadoEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}</b>. Próxima automática em até 30 min.</>
              : 'Ainda sem dados. Entre no SGE para a primeira leitura.'}
          </p>
        )}
      </div>

      <div className="mt-6 border-t border-tinta/5 pt-5">
        <div className="mb-2 flex items-center justify-between text-sm">
          <span className="flex items-center gap-2 font-semibold"><CalendarDays size={16} className="text-tinta-suave" aria-hidden />{b ? `${b.numero}º bimestre` : 'Bimestre'}</span>
          <span className="text-xs text-tinta-suave">{b ? `${diaMes(b.inicio)} – ${diaMes(b.fim)}` : 'datas não lidas'}</span>
        </div>
        <BarraSegmentada progresso={pctBimestre ?? 0} segmentos={22} altura={30} cor="#d58ad6" trilho="rgb(85 45 48 / 0.12)" rotulo="Progresso do bimestre" />
        <div className="mt-1.5 flex justify-between text-[11px] text-tinta-suave">
          <span>início</span>
          {pctBimestre != null && <b className="text-sm text-[#b75fb8]">{Math.round(pctBimestre * 100)}%</b>}
          <span>fim</span>
        </div>
      </div>
    </section>
  );
}

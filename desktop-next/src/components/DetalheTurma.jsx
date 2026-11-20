import { useEffect } from 'react';
import { X, ArrowUpRight, CheckCircle2, AlertTriangle } from 'lucide-react';
import { diaSemana, diaMes, MODULOS, atalho } from '../lib/sge';

/**
 * Painel lateral da turma: cada módulo do diário com o que falta e um botão que abre
 * o SGE dentro do app, já na tela certa. Fecha com Esc ou clicando fora.
 */
export default function DetalheTurma({ turma, aoFechar, aoAbrirSge }) {
  useEffect(() => {
    const tecla = (e) => e.key === 'Escape' && aoFechar();
    window.addEventListener('keydown', tecla);
    return () => window.removeEventListener('keydown', tecla);
  }, [aoFechar]);
  if (!turma) return null;
  const pct = Math.round((turma.progresso ?? 0) * 100);
  const modulos = turma.modulos ?? { conteudo: { pendentes: turma.pendentes ?? [], registradas: turma.registradas, erro: turma.status === 'erro' ? turma.erro : undefined } };

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-tinta/15 backdrop-blur-[2px]" onClick={aoFechar}>
      <aside
        role="dialog" aria-modal="true" aria-label={`Turma ${turma.nome}`}
        onClick={(e) => e.stopPropagation()}
        className="vidro-forte deslizar m-3 flex w-[min(480px,94vw)] flex-col rounded-[28px] p-6 shadow-2xl"
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
          <Numero valor={turma.registradas ?? 0} rotulo="com conteúdo" />
          <Numero valor={turma.totalPendencias ?? turma.pendentes?.length ?? 0} rotulo="pendências" cor={(turma.totalPendencias ?? turma.pendentes?.length) ? 'text-pendente' : undefined} />
          <Numero valor={turma.status === 'erro' ? '—' : `${pct}%`} rotulo="conteúdo em dia" />
        </div>

        <div className="mt-5 min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
          {MODULOS.map((m) => {
            const dado = modulos[m.chave];
            if (!dado || dado.indisponivel) return null;
            return <Modulo key={m.chave} def={m} dado={dado} aoAbrir={(datas) => aoAbrirSge(atalho(turma, m.modulo, datas))} />;
          })}
        </div>

        <button type="button" onClick={() => aoAbrirSge(atalho(turma, 0, turma.pendentes ?? []))}
          className="mt-4 flex h-11 items-center justify-center gap-2 rounded-full bg-tinta text-sm font-semibold text-white transition hover:opacity-90 active:scale-[0.98]">
          <ArrowUpRight size={18} /> Fazer o diário desta turma no SGE
        </button>
      </aside>
    </div>
  );
}

function Modulo({ def, dado, aoAbrir }) {
  const datas = def.chave === 'conteudo' ? dado.pendentes ?? [] : def.chave === 'frequencia' ? dado.datas ?? [] : null;
  const n = dado.erro ? null : datas ? datas.length : dado.pendentes ?? 0;
  return (
    <section className="rounded-2xl bg-white/70 p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 font-semibold">
          {dado.erro ? <AlertTriangle size={17} className="text-alerta" /> : n > 0 ? <span className="size-2 rounded-full bg-pendente" /> : <CheckCircle2 size={17} className="text-emdia" />}
          {def.rotulo}
        </h3>
        <button type="button" onClick={() => aoAbrir(datas ?? [])}
          className="flex items-center gap-1 rounded-full bg-lavanda-clara px-3 py-1.5 text-xs font-semibold text-lavanda transition hover:bg-lavanda hover:text-[#fff]">
          Abrir no SGE <ArrowUpRight size={14} />
        </button>
      </div>
      <div className="mt-2 text-sm text-tinta-suave">
        {dado.erro ? <p className="text-alerta">{dado.erro}</p>
          : datas ? (n === 0 ? <p>Nada pendente no bimestre.</p> : (
            <div className="flex flex-wrap gap-1.5">
              {[...datas].reverse().slice(0, 12).map((d) => (
                <button key={d} type="button" onClick={() => aoAbrir([d])} title={`${diaSemana(d)} · abrir este dia no SGE`}
                  className="rounded-full bg-rose-50 px-2 py-0.5 text-xs font-medium text-pendente capitalize transition hover:bg-pendente hover:text-[#fff]">{diaMes(d)}</button>
              ))}
              {datas.length > 12 && <span className="text-xs">+{datas.length - 12}</span>}
            </div>
          ))
          : (
            <p>
              <b className={n ? 'text-pendente' : 'text-tinta'}>{n}</b> {n === 1 ? def.unidade[0] : def.unidade[1]}
              {dado.ausentes ? ` (${dado.ausentes} a criar)` : ''} · {dado.aguardando ?? 0} aguardando avaliação · {dado.aprovados ?? 0} aprovados
              {dado.parcial && <span className="block text-xs text-alerta">Alguns períodos não tinham datas reconhecíveis e não foram contados.</span>}
              {dado.coberturaErro && <span className="block text-xs text-alerta">Cobertura não verificada: {dado.coberturaErro}</span>}
            </p>
          )}
      </div>
    </section>
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

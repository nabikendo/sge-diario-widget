import { useEffect, useState } from 'react';
import { ArrowUpRight, CalendarOff, CheckCircle2 } from 'lucide-react';
import { sge, atalho } from '../lib/sge';

const rotuloDia = (iso, i) => {
  const d = new Date(iso + 'T12:00:00');
  const extenso = d.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  return `${i === 0 ? 'Hoje' : 'Amanhã'} · ${extenso.charAt(0).toUpperCase() + extenso.slice(1)}`;
};

/** Aulas de hoje e de amanhã (horário do SGE) com o que falta registrar de cada turma. */
export function Semana({ painel, aoAbrir }) {
  const [agenda, setAgenda] = useState(undefined);
  useEffect(() => {
    sge.obterAgenda().then(setAgenda);
    return sge.aoAgenda(setAgenda);
  }, []);
  if (agenda === undefined) return <p className="text-tinta-suave">Lendo o horário no SGE…</p>;
  if (!agenda) return <p className="rounded-2xl bg-white/60 p-6 text-center text-tinta-suave">Não consegui ler o horário agora. Entre no SGE (perfil Servidor) e sincronize.</p>;
  const turmas = painel?.turmas ?? [];

  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
      {agenda.dias.map((dia, i) => (
        <section key={dia.data} className="surgir" style={{ '--atraso': `${i * 60}ms` }}>
          <h2 className="mb-3 text-lg font-semibold">{rotuloDia(dia.data, i)}</h2>
          {dia.semAula && <p className="mb-2.5 flex items-center gap-2 rounded-2xl bg-lavanda-clara px-4 py-2.5 text-sm font-medium text-lavanda"><CalendarOff size={16} aria-hidden /> {dia.semAula}: dia sem aula, sem cobrança de registro.</p>}
          {dia.aulas.length === 0 ? (
            <p className="flex items-center gap-3 rounded-2xl bg-white/60 p-5 text-tinta-suave"><CalendarOff size={20} aria-hidden /> {dia.semAula ? 'Sem aulas neste dia.' : 'Sem aulas neste dia no horário do SGE.'}</p>
          ) : (
            <ul className="space-y-2.5">
              {dia.aulas.map((a) => <Aula key={`${a.inicio}|${a.turma}`} aula={a} dia={dia.data} hoje={i === 0} turma={turmas.find((t) => t.nome === a.turma && t.disciplina === a.disciplina)} aoAbrir={aoAbrir} />)}
            </ul>
          )}
        </section>
      ))}
    </div>
  );
}

function Aula({ aula, dia, hoje, turma, aoAbrir }) {
  const falta = [];
  if (turma && turma.status !== 'erro') {
    const c = turma.contagens ?? {};
    if (hoje && turma.pendentes?.includes(dia)) falta.push({ texto: 'Conteúdo de hoje', forte: true });
    if (hoje && turma.modulos?.frequencia?.datas?.includes(dia)) falta.push({ texto: 'Chamada de hoje', forte: true });
    if (c.conteudo) falta.push({ texto: `${c.conteudo} ${c.conteudo === 1 ? 'dia' : 'dias'} sem conteúdo` });
    if (c.frequencia) falta.push({ texto: `${c.frequencia} ${c.frequencia === 1 ? 'chamada' : 'chamadas'}` });
    if (c.planoAula) falta.push({ texto: `${c.planoAula} plano de aula` });
    if (c.planoCurso) falta.push({ texto: `${c.planoCurso} plano de curso` });
    if (c.pei) falta.push({ texto: `${c.pei} PEI` });
  }
  const modulo = turma?.modulos?.frequencia?.datas?.includes(dia) && hoje ? 1 : 0;
  return (
    <li className="vidro flex items-center gap-4 rounded-2xl p-4">
      <div className="w-24 shrink-0 text-center">
        <b className="block text-lg tabular-nums">{aula.inicio}</b>
        <span className="text-xs text-tinta-suave tabular-nums">até {aula.fim}</span>
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold">Turma {aula.turma} <span className="font-normal text-tinta-suave">· {aula.disciplina}</span></p>
        {!turma ? <p className="text-sm text-tinta-suave">Turma não lida na última sincronização.</p>
          : turma.status === 'erro' ? <p className="text-sm text-alerta">Não foi possível ler esta turma.</p>
          : falta.length === 0 ? <p className="flex items-center gap-1.5 text-sm text-emdia"><CheckCircle2 size={15} aria-hidden /> Nada pendente</p>
          : <div className="mt-1 flex flex-wrap gap-1.5">{falta.map((f) => <span key={f.texto} className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${f.forte ? 'bg-pendente text-[#fff]' : 'bg-rose-50 text-pendente'}`}>{f.texto}</span>)}</div>}
      </div>
      {turma && (
        <button type="button" onClick={() => aoAbrir(atalho(turma, modulo, modulo === 0 ? turma.pendentes ?? [] : []))}
          className="flex shrink-0 items-center gap-1 rounded-full bg-lavanda-clara px-3 py-1.5 text-xs font-semibold text-lavanda transition hover:bg-lavanda hover:text-[#fff]">
          Abrir no SGE <ArrowUpRight size={14} aria-hidden />
        </button>
      )}
    </li>
  );
}

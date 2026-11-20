import { useEffect, useMemo, useState } from 'react';
import { School, MessageSquareText, X, Copy, Check, TrendingDown, TrendingUp } from 'lucide-react';
import { sge } from '../lib/sge';
import { referenciaSemanal, diferencas } from '../lib/comparacao.mjs';
import { mensagensEmLote, linhasDoItem } from '../lib/mensagens.mjs';

const brData = (iso) => iso.slice(8, 10) + '/' + iso.slice(5, 7);
const totalDe = (p) => p.total ?? p.dias;

/** Coordenação (perfil Unidade escolar): só consulta. Pendências da escola por professor, comparação com a semana passada e mensagens de cobrança. */
export function Coordenacao({ perfilEscola, sincronizando }) {
  const [dados, setDados] = useState(null);
  const [historico, setHistorico] = useState([]);
  const [aberto, setAberto] = useState(null);
  const [busca, setBusca] = useState('');
  const [cobranca, setCobranca] = useState(false);
  useEffect(() => {
    const carregar = () => { sge.obterCoordenacao().then(setDados); sge.obterHistoricoCoordenacao().then(setHistorico); };
    carregar();
    const desligar = [sge.aoCoordenacao(carregar), sge.aoTurmasEscola(carregar)];
    return () => desligar.forEach((f) => f());
  }, []);
  const painel = dados?.painel;
  const semana = useMemo(() => {
    if (!painel) return null;
    const ref = referenciaSemanal(historico.filter((h) => h.em !== painel.atualizadoEm));
    if (!ref) return null;
    const atual = Object.fromEntries(painel.professores.filter((p) => p.id).map((p) => [p.nome, totalDe(p)]));
    return { ref, ...diferencas(atual, ref.porProfessor), delta: new Map(Object.keys({ ...atual, ...ref.porProfessor }).map((n) => [n, (atual[n] ?? 0) - (ref.porProfessor[n] ?? 0)])) };
  }, [painel, historico]);
  if (!dados) return null;
  const { turmasEscola } = dados;

  if (!painel) {
    return (
      <div className="mx-auto max-w-2xl py-8">
        <div className="text-center">
          <School size={48} className="mx-auto text-lavanda" strokeWidth={1.5} />
          <h2 className="mt-4 text-2xl font-semibold">Coordenação</h2>
          <p className="mt-2 text-tinta-suave">Pendências da escola inteira, por professor, atribuídas pelo horário de cada um. O app só consulta: o coordenador corrige no SGE.</p>
        </div>
        <ol className="mt-8 space-y-3">
          <Passo n={1} feito={perfilEscola}>Entre no SGE com o perfil <b>Unidade escolar</b>.</Passo>
          <Passo n={2} feito={turmasEscola > 0}>
            No SGE, abra <b>Escola → Conteúdo Aplicado</b>. O app lê a lista de turmas sozinho{turmasEscola > 0 ? ` (${turmasEscola} turmas lidas).` : '.'}
          </Passo>
          <Passo n={3}>Clique em <b>Ler a escola</b>. Nada é gravado no SGE.</Passo>
        </ol>
        <div className="mt-8 flex justify-center gap-3">
          <button type="button" onClick={() => sge.abrirSge()} className="rounded-full bg-white px-5 py-2.5 text-sm font-semibold ring-1 ring-tinta/10 hover:bg-white/70">Abrir o SGE para consulta</button>
          <button type="button" onClick={() => sge.sincronizarEscola()} disabled={!turmasEscola || sincronizando}
            className="rounded-full bg-tinta px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-40">Ler a escola</button>
        </div>
      </div>
    );
  }

  const q = busca.trim().toLowerCase();
  const professores = painel.professores.filter((p) => !q || `${p.nome} ${p.itens.map((i) => `${i.turma} ${i.componente}`).join(' ')}`.toLowerCase().includes(q));
  const totalGeral = painel.professores.reduce((s, p) => s + totalDe(p), 0);
  const comPendencia = painel.professores.filter((p) => p.id).length;
  const max = Math.max(1, ...painel.professores.map(totalDe));
  const mais = painel.detalhes?.chamada || painel.detalhes?.planos;

  return (
    <>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <Indicador valor={totalGeral} rotulo={mais ? 'pendências na escola' : 'aulas sem conteúdo na escola'} cor="text-pendente" />
        <Indicador valor={`${comPendencia}/${painel.totalProfessores}`} rotulo="professores com pendência" />
        <Indicador valor={painel.turmas.length} rotulo="turmas lidas" />
      </div>

      {semana && (
        <section className="surgir vidro mt-5 rounded-3xl p-5">
          <h2 className="text-[15px] font-semibold">Desde a semana passada <span className="font-normal text-tinta-suave">· comparado com {new Date(semana.ref.em).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}</span></h2>
          <p className="mt-1 text-sm text-tinta-suave">
            Total de {semana.totalAntes} para <b className="text-tinta">{semana.totalAgora}</b> pendências.
          </p>
          <div className="mt-3 grid gap-4 md:grid-cols-2">
            <Lista icone={TrendingDown} cor="text-emdia" titulo="Melhoraram" vazio="Ninguém reduziu as pendências." itens={semana.melhoraram} />
            <Lista icone={TrendingUp} cor="text-pendente" titulo="Pioraram" vazio="Ninguém piorou." itens={semana.pioraram} />
          </div>
        </section>
      )}

      <div className="mt-8 mb-3 flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-semibold">Por professor</h2>
        <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Filtrar professor, turma ou componente"
          className="ml-auto w-64 rounded-full bg-white/80 px-4 py-2 text-sm ring-1 ring-white outline-none focus:ring-lavanda/40" />
        <button type="button" onClick={() => setCobranca(true)} className="flex items-center gap-1.5 rounded-full bg-lavanda px-4 py-2 text-sm font-semibold text-[#fff] hover:brightness-110">
          <MessageSquareText size={16} aria-hidden /> Mensagens de cobrança
        </button>
        <button type="button" onClick={() => sge.salvarRelatorio('coord-pdf')} className="rounded-full bg-white px-4 py-2 text-sm font-semibold ring-1 ring-tinta/10 hover:bg-white/70">PDF</button>
        <button type="button" onClick={() => sge.salvarRelatorio('coord-csv')} className="rounded-full bg-white px-4 py-2 text-sm font-semibold ring-1 ring-tinta/10 hover:bg-white/70">Planilha</button>
        <button type="button" onClick={() => sge.sincronizarEscola()} disabled={sincronizando}
          className="rounded-full bg-tinta px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">{sincronizando ? 'Lendo…' : 'Ler de novo'}</button>
      </div>
      <p className="mb-3 text-xs text-tinta-suave">
        Lido em {new Date(painel.atualizadoEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })} · {turmasEscola} turmas da escola conhecidas
        {!mais && ' · Chamada e planos podem ser incluídos em Configurações'}
      </p>

      <ul className="space-y-2">
        {professores.map((p, i) => {
          const chave = p.id || p.nome;
          const abertoAqui = aberto === chave;
          const delta = semana?.delta.get(p.nome);
          return (
            <li key={chave} className="surgir overflow-hidden rounded-2xl bg-white/70" style={{ '--atraso': `${Math.min(i, 8) * 30}ms` }}>
              <button type="button" onClick={() => setAberto(abertoAqui ? null : chave)} aria-expanded={abertoAqui}
                className="flex w-full items-center gap-4 px-5 py-3.5 text-left hover:bg-white/60">
                <span className="w-64 shrink-0 truncate font-medium">{p.nome}</span>
                <span className="h-2.5 flex-1 overflow-hidden rounded-full bg-tinta/[0.07]">
                  <span className="block h-full rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-400" style={{ width: `${(totalDe(p) / max) * 100}%` }} />
                </span>
                <span className="w-16 shrink-0 text-right text-xs font-semibold tabular-nums">
                  {delta ? <span className={delta < 0 ? 'text-emdia' : 'text-pendente'}>{delta < 0 ? '▼' : '▲'} {Math.abs(delta)}</span> : ''}
                </span>
                <span className="w-28 shrink-0 text-right text-sm"><b className="tabular-nums text-pendente">{totalDe(p)}</b> {totalDe(p) === 1 ? 'pendência' : 'pendências'}</span>
              </button>
              {abertoAqui && (
                <div className="border-t border-white px-5 py-3">
                  {p.itens.map((it) => (
                    <div key={`${it.turmaId}|${it.componente}`} className="flex flex-wrap items-start gap-x-3 gap-y-1 py-1.5 text-sm">
                      <span className="w-56 truncate font-medium">{it.turma}</span>
                      <span className="w-40 truncate text-tinta-suave">{it.componente}</span>
                      <span className="flex flex-1 flex-wrap gap-1">
                        {it.datas.map((d) => <Etiqueta key={`c${d}`}>Conteúdo {brData(d)}</Etiqueta>)}
                        {it.chamada?.map((d) => <Etiqueta key={`f${d}`}>Chamada {brData(d)}</Etiqueta>)}
                        {it.planos?.planoAula > 0 && <Etiqueta>{it.planos.planoAula} plano de aula</Etiqueta>}
                        {it.planos?.planoCurso > 0 && <Etiqueta>{it.planos.planoCurso} plano de curso</Etiqueta>}
                        {it.planos?.pei > 0 && <Etiqueta>{it.planos.pei} PEI</Etiqueta>}
                        {it.erroChamada && <span className="text-xs text-alerta" title={it.erroChamada}>Chamada não lida</span>}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </li>
          );
        })}
      </ul>
      {painel.semPendencia?.length > 0 && (
        <p className="mt-6 text-sm text-tinta-suave"><b className="text-emdia">Em dia:</b> {painel.semPendencia.join(', ')}.</p>
      )}
      {cobranca && <Cobranca professores={painel.professores} aoFechar={() => setCobranca(false)} />}
    </>
  );
}

const Etiqueta = ({ children }) => <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs text-pendente">{children}</span>;

function Lista({ icone: Icone, cor, titulo, itens, vazio }) {
  return (
    <div>
      <h3 className={`flex items-center gap-1.5 text-sm font-semibold ${cor}`}><Icone size={16} aria-hidden /> {titulo}</h3>
      {itens.length === 0 ? <p className="mt-1 text-sm text-tinta-suave">{vazio}</p> : (
        <ul className="mt-1 space-y-0.5 text-sm">
          {itens.slice(0, 6).map((i) => <li key={i.chave} className="flex justify-between gap-3"><span className="truncate">{i.chave}</span><span className="tabular-nums text-tinta-suave">{i.antes} → <b className={cor}>{i.agora}</b></span></li>)}
          {itens.length > 6 && <li className="text-xs text-tinta-suave">e mais {itens.length - 6}</li>}
        </ul>
      )}
    </div>
  );
}

/** Mensagens prontas para copiar e colar no WhatsApp (uma por professor). O app não envia nada sozinho. */
function Cobranca({ professores, aoFechar }) {
  const [prazo, setPrazo] = useState('até sexta-feira');
  const [coordenador, setCoordenador] = useState('');
  const [copiado, setCopiado] = useState(null);
  useEffect(() => {
    const tecla = (e) => e.key === 'Escape' && aoFechar();
    window.addEventListener('keydown', tecla);
    return () => window.removeEventListener('keydown', tecla);
  }, [aoFechar]);
  const mensagens = useMemo(() => mensagensEmLote(professores, { prazo, coordenador }), [professores, prazo, coordenador]);
  const copiar = async (chave, texto) => { await navigator.clipboard.writeText(texto); setCopiado(chave); setTimeout(() => setCopiado((c) => (c === chave ? null : c)), 1800); };
  const naoIdentificados = professores.find((p) => !p.id);

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-tinta/20 p-6 backdrop-blur-[2px]" onClick={aoFechar}>
      <div role="dialog" aria-modal="true" aria-label="Mensagens de cobrança" onClick={(e) => e.stopPropagation()}
        className="vidro-forte surgir flex max-h-full w-[min(760px,100%)] flex-col rounded-[28px] p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-xl font-semibold">Mensagens de cobrança</h2>
            <p className="text-sm text-tinta-suave">Uma por professor, pronta para colar no WhatsApp. O app não envia nada.</p>
          </div>
          <button type="button" onClick={aoFechar} aria-label="Fechar" className="rounded-full p-2 text-tinta-suave hover:bg-tinta/5"><X size={20} /></button>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <label className="text-sm text-tinta-suave">Prazo
            <input value={prazo} onChange={(e) => setPrazo(e.target.value)} placeholder="até sexta-feira" className="mt-1 w-full rounded-xl bg-white/80 px-3 py-2 text-tinta ring-1 ring-white outline-none focus:ring-lavanda/40" />
          </label>
          <label className="text-sm text-tinta-suave">Assinatura (opcional)
            <input value={coordenador} onChange={(e) => setCoordenador(e.target.value)} placeholder="Coordenação pedagógica" className="mt-1 w-full rounded-xl bg-white/80 px-3 py-2 text-tinta ring-1 ring-white outline-none focus:ring-lavanda/40" />
          </label>
        </div>
        <div className="mt-4 min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
          {mensagens.length === 0 && <p className="rounded-2xl bg-white/60 p-5 text-center text-tinta-suave">Nenhum professor identificado com pendência.</p>}
          {mensagens.map((m) => (
            <article key={m.professor} className="rounded-2xl bg-white/70 p-4">
              <div className="mb-2 flex items-center justify-between gap-3">
                <b>{m.professor}</b>
                <button type="button" onClick={() => copiar(m.professor, m.texto)}
                  className="flex items-center gap-1.5 rounded-full bg-lavanda-clara px-3 py-1.5 text-xs font-semibold text-lavanda hover:bg-lavanda hover:text-[#fff]">
                  {copiado === m.professor ? <><Check size={14} aria-hidden /> Copiado</> : <><Copy size={14} aria-hidden /> Copiar</>}
                </button>
              </div>
              <pre className="font-sans text-sm whitespace-pre-wrap text-tinta">{m.texto}</pre>
            </article>
          ))}
          {naoIdentificados && <p className="text-xs text-tinta-suave">{naoIdentificados.itens.map(linhasDoItem).filter(Boolean).length} item(ns) sem professor identificado não geram mensagem.</p>}
        </div>
        {mensagens.length > 1 && (
          <button type="button" onClick={() => copiar('todas', mensagens.map((m) => `*${m.professor}*\n${m.texto}`).join('\n\n———\n\n'))}
            className="mt-4 flex h-11 items-center justify-center gap-2 rounded-full bg-tinta text-sm font-semibold text-white hover:opacity-90">
            {copiado === 'todas' ? <><Check size={18} aria-hidden /> Copiado</> : <><Copy size={18} aria-hidden /> Copiar todas</>}
          </button>
        )}
      </div>
    </div>
  );
}

function Passo({ n, feito, children }) {
  return (
    <li className="flex items-start gap-3 rounded-2xl bg-white/70 px-4 py-3">
      <span className={`flex size-7 shrink-0 items-center justify-center rounded-full text-sm font-bold ${feito ? 'bg-emdia text-[#fff]' : 'bg-lavanda-clara text-lavanda'}`}>{feito ? '✓' : n}</span>
      <span className="pt-0.5">{children}</span>
    </li>
  );
}
function Indicador({ valor, rotulo, cor = 'text-tinta' }) {
  return (
    <div className="surgir vidro rounded-3xl p-5">
      <b className={`block text-4xl font-semibold tabular-nums ${cor}`}>{valor}</b>
      <span className="text-sm text-tinta-suave">{rotulo}</span>
    </div>
  );
}

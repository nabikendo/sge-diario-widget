import { useEffect, useMemo, useState } from 'react';
import { School, Printer, MessageSquareText, X, Copy, Check, TrendingDown, TrendingUp, ChevronRight, ChevronDown, FileText, FileSpreadsheet } from 'lucide-react';
import { sge } from '../lib/sge';
import { referenciaSemanal, diferencas } from '../lib/comparacao.mjs';
import { mensagensEmLote } from '../lib/mensagens.mjs';
import { visaoPeriodo, porProfessor, porTurma, porComponente, casaBusca, resumoDatas } from '../../electron/escolaVisao.mjs';

const brData = (iso) => iso.slice(8, 10) + '/' + iso.slice(5, 7);
const plural = (n, um, varios) => `${n} ${n === 1 ? um : varios}`;

/* ---------- Dados e período ---------- */
function usarEscola() {
  const [dados, setDados] = useState(null);
  const [historico, setHistorico] = useState([]);
  const [periodo, setPeriodoEstado] = useState(() => { try { const v = localStorage.getItem('periodoEscola'); return /^(ano|[1-4])$/.test(v) ? v : 'bimestre'; } catch { return 'bimestre'; } });
  const setPeriodo = (p) => { setPeriodoEstado(p); try { localStorage.setItem('periodoEscola', p); } catch { /* sem armazenamento: só nesta sessão */ } };
  useEffect(() => {
    const carregar = () => { sge.obterCoordenacao().then(setDados); sge.obterHistoricoCoordenacao().then(setHistorico); };
    carregar();
    const desligar = [sge.aoCoordenacao(carregar), sge.aoTurmasEscola(carregar)];
    return () => desligar.forEach((f) => f());
  }, []);
  const painel = dados?.painel ?? null;
  const visao = useMemo(() => (painel ? visaoPeriodo(painel, periodo) : null), [painel, periodo]);
  return { dados, historico, periodo: visao?.periodo ?? periodo, escolhido: periodo, setPeriodo, painel, visao };
}

/** Coordenação (perfil Unidade escolar): só consulta. Uma seção por vez; todas partem dos mesmos dados lidos do SGE. */
export function Escola({ secao, busca, perfilEscola, sincronizando, aoAbrirSge, foco, aoFocar }) {
  const e = usarEscola();
  const [detalhe, setDetalhe] = useState(null); // { tipo: 'professor'|'turma'|'componente', chave }
  const [cobranca, setCobranca] = useState(false);
  useEffect(() => setDetalhe(foco ?? null), [secao]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (foco) { setDetalhe(foco); aoFocar?.(null); } }, [foco]); // eslint-disable-line react-hooks/exhaustive-deps
  if (!e.dados) return null;
  if (!e.painel) return <PrimeiraLeitura turmasEscola={e.dados.turmasEscola} perfilEscola={perfilEscola} sincronizando={sincronizando} aoAbrirSge={aoAbrirSge} />;
  const { visao, painel, periodo } = e;

  const Secao = { visao: VisaoGeral, professores: Professores, 'turmas-escola': Turmas, componentes: Componentes, historico: HistoricoEscola, relatorios: RelatoriosEscola, direcao: Direcao }[secao] ?? VisaoGeral;
  return (
    <>
      <Barra e={e} sincronizando={sincronizando} aoCobranca={() => setCobranca(true)} mostrarAcoes={secao !== 'relatorios' && secao !== 'historico' && secao !== 'direcao'} />
      <Secao e={e} busca={busca} aoAbrir={(tipo, chave) => setDetalhe({ tipo, chave })} aoIr={(tipo, chave) => setDetalhe({ tipo, chave })} />
      {detalhe && <Detalhe visao={visao} {...detalhe} periodo={periodo} aoFechar={() => setDetalhe(null)} />}
      {cobranca && <Cobranca professores={visao.professores} aoFechar={() => setCobranca(false)} />}
    </>
  );
}

/* ---------- Barra: período e ações ---------- */
function Barra({ e, sincronizando, aoCobranca, mostrarAcoes }) {
  const { painel, periodo, setPeriodo, visao } = e;
  const lista = painel.bimestres?.length ? painel.bimestres : painel.bimestre ? [painel.bimestre] : [];
  const Opcao = ({ id, atual, children, titulo }) => (
    <button type="button" onClick={() => setPeriodo(id)} aria-pressed={periodo === (id === 'bimestre' ? String(painel.bimestre?.numero) : id)} title={titulo}
      className={`rounded-full px-4 py-2 text-sm font-medium transition ${periodo === (id === 'bimestre' ? String(painel.bimestre?.numero) : id) ? 'bg-tinta text-white' : 'bg-white/70 text-tinta-suave ring-1 ring-white hover:bg-white'}`}>
      {children}{atual && <span className="ml-1.5 inline-block size-1.5 rounded-full bg-emdia align-middle" aria-label="bimestre atual" />}
    </button>
  );
  const j = visao.janela;
  return (
    <div className="mb-5" data-nao-imprimir>
      <div className="flex flex-wrap items-center gap-2">
        {lista.map((b) => <Opcao key={b.numero} id={String(b.numero)} atual={b.numero === painel.bimestre?.numero} titulo={`${brData(b.inicio)} a ${brData(b.fim)}`}>{b.numero}º bimestre</Opcao>)}
        <Opcao id="ano">Ano letivo</Opcao>
        {mostrarAcoes && (
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <button type="button" onClick={aoCobranca} className="flex items-center gap-1.5 rounded-full bg-lavanda px-4 py-2 text-sm font-semibold text-[#fff] hover:brightness-110">
              <MessageSquareText size={16} aria-hidden /> Mensagens de cobrança
            </button>
            <button type="button" onClick={() => sge.sincronizarEscola()} disabled={sincronizando}
              className="rounded-full bg-tinta px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">{sincronizando ? 'Lendo…' : 'Ler de novo'}</button>
          </div>
        )}
      </div>
      <p className="mt-2 text-xs text-tinta-suave">
        {j ? `${j.numero}º bimestre, de ${brData(j.inicio)} a ${brData(j.fim)}` : 'Ano letivo inteiro'} · lido em {new Date(painel.atualizadoEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })} · {e.dados.turmasEscola} turmas
        {j && j.numero !== painel.bimestre?.numero && ' · Chamada e planos só aparecem no bimestre atual e no ano letivo'}
        {!(painel.detalhes?.chamada || painel.detalhes?.planos) && ' · só Conteúdo Aplicado (Chamada e planos: Configurações)'}
      </p>
    </div>
  );
}

/* ---------- Visão geral ---------- */
function VisaoGeral({ e, aoIr }) {
  const { visao, historico, periodo } = e;
  const total = visao.professores.reduce((s, p) => s + p.total, 0);
  const prof = porProfessor(visao); const turmas = porTurma(visao); const comps = porComponente(visao);
  const semana = useMemo(() => {
    if (e.escolhido !== 'bimestre' && periodo !== String(visao.bimestre?.numero)) return null;
    const ref = referenciaSemanal(historico.filter((h) => h.em !== visao.atualizadoEm));
    if (!ref) return null;
    const atual = Object.fromEntries(visao.professores.filter((p) => p.id).map((p) => [p.nome, p.total]));
    return { ref, ...diferencas(atual, ref.porProfessor) };
  }, [visao, historico, periodo, e.escolhido]);

  return (
    <>
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Indicador valor={total} rotulo={periodo === 'ano' ? 'pendências no ano letivo' : `pendências no ${periodo}º bimestre`} cor="text-pendente" />
        <Indicador valor={`${prof.filter((p) => p.id).length}/${visao.totalProfessores}`} rotulo="professores com pendência" />
        <Indicador valor={`${turmas.length}/${visao.turmas.length}`} rotulo="turmas com pendência" />
        <Indicador valor={comps.length} rotulo="componentes com pendência" />
      </div>

      {semana && (
        <section className="surgir vidro mt-5 rounded-3xl p-5">
          <h2 className="text-[15px] font-semibold">Desde a semana passada <span className="font-normal text-tinta-suave">· comparado com {new Date(semana.ref.em).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}</span></h2>
          <p className="mt-1 text-sm text-tinta-suave">Total de {semana.totalAntes} para <b className="text-tinta">{semana.totalAgora}</b> pendências.</p>
          <div className="mt-3 grid gap-4 md:grid-cols-2">
            <Variacao icone={TrendingDown} cor="text-emdia" titulo="Melhoraram" vazio="Ninguém reduziu as pendências." itens={semana.melhoraram} />
            <Variacao icone={TrendingUp} cor="text-pendente" titulo="Pioraram" vazio="Ninguém piorou." itens={semana.pioraram} />
          </div>
        </section>
      )}

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        <Ranking titulo="Professores com mais pendências" itens={prof.filter((p) => p.id).slice(0, 6)} aoAbrir={(g) => aoIr('professor', g.chave)} detalhe={(g) => plural(g.turmas.length, 'turma', 'turmas')} />
        <Ranking titulo="Turmas com mais pendências" itens={turmas.slice(0, 6)} aoAbrir={(g) => aoIr('turma', g.chave)} detalhe={(g) => plural(g.componentes.length, 'componente', 'componentes')} />
      </div>
      {visao.semPendencia?.length > 0 && <p className="mt-6 text-sm text-tinta-suave"><b className="text-emdia">Em dia:</b> {visao.semPendencia.join(', ')}.</p>}
    </>
  );
}

function Ranking({ titulo, itens, aoAbrir, detalhe }) {
  const max = Math.max(1, ...itens.map((i) => i.total));
  return (
    <section className="surgir vidro rounded-3xl p-5">
      <h2 className="mb-3 text-[15px] font-semibold">{titulo}</h2>
      {itens.length === 0 ? <p className="text-sm text-tinta-suave">Nenhuma pendência.</p> : (
        <ul className="space-y-1">
          {itens.map((g) => (
            <li key={g.chave}>
              <button type="button" onClick={() => aoAbrir(g)} className="flex w-full items-center gap-3 rounded-xl px-2 py-2 text-left hover:bg-white/60">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{g.rotulo}</span>
                  <span className="mt-1 block h-1.5 overflow-hidden rounded-full bg-tinta/[0.07]"><span className="block h-full rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-400" style={{ width: `${(g.total / max) * 100}%` }} /></span>
                </span>
                <span className="w-28 shrink-0 text-right text-xs text-tinta-suave"><b className="text-sm tabular-nums text-pendente">{g.total}</b> · {detalhe(g)}</span>
                <ChevronRight size={16} className="shrink-0 text-tinta-suave/60" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/* ---------- Professores / Turmas / Componentes: cartões como os do professor ---------- */
function Professores({ e, busca, aoAbrir }) {
  const { visao } = e;
  const grupos = porProfessor(visao).filter((g) => casaBusca(g, busca));
  const max = Math.max(1, ...grupos.map((g) => g.total));
  return (
    <>
      <Grade vazio="Nenhum professor com pendência neste período.">
        {grupos.map((g, i) => <Cartao key={g.chave} indice={i} sobre={g.id ? 'Professor' : 'Sem professor no horário'} titulo={g.rotulo} total={g.total} max={max}
          chips={[plural(g.turmas.length, 'turma', 'turmas'), plural(g.componentes.length, 'componente', 'componentes')]} resumo={resumoPorTipo(g.itens)} aoAbrir={() => aoAbrir('professor', g.chave)} />)}
      </Grade>
      {visao.semPendencia?.length > 0 && !busca && <EmDia nomes={visao.semPendencia} rotulo="Professores em dia" />}
    </>
  );
}

function Turmas({ e, busca, aoAbrir }) {
  const { visao } = e;
  const comPend = porTurma(visao);
  const ids = new Set(comPend.map((g) => g.chave));
  const emDia = visao.turmas.filter((t) => !ids.has(t.id)).map((t) => t.nome).sort();
  const grupos = comPend.filter((g) => casaBusca(g, busca));
  const max = Math.max(1, ...grupos.map((g) => g.total));
  return (
    <>
      <Grade vazio="Nenhuma turma com pendência neste período.">
        {grupos.map((g, i) => <Cartao key={g.chave} indice={i} sobre="Turma" titulo={g.rotulo} total={g.total} max={max}
          chips={[plural(g.componentes.length, 'componente', 'componentes'), plural(g.professores.length, 'professor', 'professores')]} resumo={resumoPorTipo(g.itens)} aoAbrir={() => aoAbrir('turma', g.chave)} />)}
      </Grade>
      {emDia.length > 0 && !busca && <EmDia nomes={emDia} rotulo="Turmas em dia" />}
    </>
  );
}

function Componentes({ e, busca, aoAbrir }) {
  const grupos = porComponente(e.visao).filter((g) => casaBusca(g, busca));
  const max = Math.max(1, ...grupos.map((g) => g.total));
  return (
    <Grade vazio="Nenhum componente com pendência neste período.">
      {grupos.map((g, i) => <Cartao key={g.chave} indice={i} sobre="Componente" titulo={g.rotulo} total={g.total} max={max}
        chips={[plural(g.turmas.length, 'turma', 'turmas'), plural(g.professores.length, 'professor', 'professores')]} resumo={resumoPorTipo(g.itens)} aoAbrir={() => aoAbrir('componente', g.chave)} />)}
    </Grade>
  );
}

/** "Conteúdo 18 · Chamada 3 · Planos 2" somando os itens. */
function resumoPorTipo(itens) {
  const c = itens.reduce((s, i) => s + i.datas.length, 0); const f = itens.reduce((s, i) => s + (i.chamada?.length ?? 0), 0);
  const p = itens.reduce((s, i) => s + Object.values(i.planos ?? {}).reduce((a, v) => a + (v ?? 0), 0), 0);
  return [c && `Conteúdo ${c}`, f && `Chamada ${f}`, p && `Planos ${p}`].filter(Boolean).join(' · ');
}

function Grade({ children, vazio }) {
  const lista = Array.isArray(children) ? children : [children];
  if (!lista.length) return <p className="rounded-2xl bg-white/60 p-6 text-center text-tinta-suave">{vazio}</p>;
  return <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">{children}</div>;
}

function EmDia({ nomes, rotulo }) {
  return <p className="mt-6 text-sm text-tinta-suave"><b className="text-emdia">{rotulo}:</b> {nomes.join(', ')}.</p>;
}

/** Cartão no mesmo estilo dos cartões de turma do professor. */
function Cartao({ sobre, titulo, total, max, chips, resumo, indice, aoAbrir }) {
  return (
    <button type="button" onClick={aoAbrir} style={{ '--atraso': `${Math.min(indice, 8) * 40}ms` }}
      className="surgir vidro group w-full rounded-3xl p-5 text-left transition duration-200 ease-(--ease-padrao) hover:-translate-y-0.5 hover:bg-white/75 hover:shadow-[0_18px_40px_-16px_rgb(60_45_140/0.28)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-lavanda active:scale-[0.99]">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[13px] font-medium text-tinta-suave">{sobre}</p>
          <h3 className="mt-0.5 text-xl leading-snug font-semibold tracking-tight break-words">{titulo}</h3>
        </div>
        <span className="shrink-0 rounded-full bg-rose-50/90 px-3 py-1 text-sm font-semibold text-pendente ring-1 ring-rose-200/70"><b className="tabular-nums">{total}</b> {total === 1 ? 'pendência' : 'pendências'}</span>
      </div>
      <div className="mt-4 h-2.5 overflow-hidden rounded-full bg-tinta/[0.07]">
        <div className="h-full rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-400 transition-[width] duration-500 ease-(--ease-entrada)" style={{ width: `${Math.max(4, (total / max) * 100)}%` }} />
      </div>
      <div className="mt-3 flex flex-wrap gap-1.5">{chips.map((c) => <span key={c} className="rounded-full bg-lavanda-clara px-2.5 py-1 text-xs font-medium text-lavanda">{c}</span>)}</div>
      <div className="mt-3 flex items-center justify-between gap-3 text-sm text-tinta-suave">
        <p className="truncate">{resumo}</p>
        <ChevronRight size={18} className="shrink-0 text-tinta-suave/60 transition-transform duration-200 group-hover:translate-x-0.5" aria-hidden />
      </div>
    </button>
  );
}

/* ---------- Painel lateral de detalhe ---------- */
function Detalhe({ visao, tipo, chave, periodo, aoFechar }) {
  useEffect(() => {
    const tecla = (e) => e.key === 'Escape' && aoFechar();
    window.addEventListener('keydown', tecla);
    return () => window.removeEventListener('keydown', tecla);
  }, [aoFechar]);
  const grupo = { professor: porProfessor, turma: porTurma, componente: porComponente }[tipo](visao).find((g) => g.chave === chave);
  if (!grupo) return null;
  // Como organizar por dentro: professor → turmas; turma → componentes; componente → turmas.
  const secaoDe = { professor: (i) => i.turma, turma: (i) => i.componente, componente: (i) => i.turma }[tipo];
  const linhaDe = { professor: (i) => i.componente, turma: (i) => i.professor.nome, componente: (i) => i.professor.nome }[tipo];
  const secoes = new Map();
  grupo.itens.forEach((i) => { const k = secaoDe(i); if (!secoes.has(k)) secoes.set(k, []); secoes.get(k).push(i); });
  const sobre = { professor: 'Professor', turma: 'Turma', componente: 'Componente' }[tipo];

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-tinta/15 backdrop-blur-[2px]" onClick={aoFechar}>
      <aside role="dialog" aria-modal="true" aria-label={`${sobre} ${grupo.rotulo}`} onClick={(ev) => ev.stopPropagation()}
        className="vidro-forte deslizar m-3 flex w-[min(560px,94vw)] flex-col rounded-[28px] p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-sm text-tinta-suave">{sobre} · {periodo === 'ano' ? 'ano letivo' : `${periodo}º bimestre`}</p>
            <h2 className="text-2xl font-semibold tracking-tight break-words">{grupo.rotulo}</h2>
          </div>
          <button type="button" onClick={aoFechar} aria-label="Fechar" className="rounded-full p-2 text-tinta-suave hover:bg-tinta/5"><X size={20} /></button>
        </div>
        <div className="mt-4 grid grid-cols-3 gap-2 text-center">
          <Numero valor={grupo.total} rotulo="pendências" cor="text-pendente" />
          <Numero valor={grupo.turmas.length} rotulo={grupo.turmas.length === 1 ? 'turma' : 'turmas'} />
          <Numero valor={tipo === 'professor' ? grupo.componentes.length : grupo.professores.length} rotulo={tipo === 'professor' ? 'componentes' : 'professores'} />
        </div>
        <div className="mt-5 min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
          {[...secoes.entries()].map(([titulo, itens]) => (
            <section key={titulo} className="rounded-2xl bg-white/70 p-4">
              <h3 className="mb-1 font-semibold">{titulo}</h3>
              <div className="divide-y divide-tinta/5">{itens.map((i) => <LinhaItem key={`${i.turmaId}|${i.componente}|${i.professor.id}`} item={i} rotulo={linhaDe(i)} />)}</div>
            </section>
          ))}
        </div>
      </aside>
    </div>
  );
}

function LinhaItem({ item, rotulo }) {
  const [aberto, setAberto] = useState(false);
  const p = item.planos ?? {};
  const partes = [
    item.datas.length > 0 && resumoDatas(item.datas),
    item.chamada?.length > 0 && `Chamada: ${plural(item.chamada.length, 'dia', 'dias')}`,
    p.planoAula > 0 && `${plural(p.planoAula, 'plano de aula', 'planos de aula')}`, p.planoCurso > 0 && `${plural(p.planoCurso, 'plano de curso', 'planos de curso')}`, p.pei > 0 && `${p.pei} PEI`,
  ].filter(Boolean);
  return (
    <div className="py-2.5">
      <button type="button" onClick={() => setAberto(!aberto)} aria-expanded={aberto} className="flex w-full items-start gap-3 text-left">
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-medium break-words">{rotulo}</span>
          <span className="block text-xs text-tinta-suave">{partes.join(' · ')}</span>
        </span>
        <span className="shrink-0 rounded-full bg-rose-50 px-2.5 py-0.5 text-xs font-semibold text-pendente tabular-nums">{item.total}</span>
        {aberto ? <ChevronDown size={16} className="mt-0.5 shrink-0 text-tinta-suave" aria-hidden /> : <ChevronRight size={16} className="mt-0.5 shrink-0 text-tinta-suave" aria-hidden />}
      </button>
      {aberto && (
        <div className="mt-2 flex flex-wrap gap-1">
          {item.datas.map((d) => <Etiqueta key={`c${d}`}>Conteúdo {brData(d)}</Etiqueta>)}
          {item.chamada?.map((d) => <Etiqueta key={`f${d}`}>Chamada {brData(d)}</Etiqueta>)}
          {item.erroChamada && <span className="text-xs text-alerta" title={item.erroChamada}>Chamada não lida</span>}
        </div>
      )}
    </div>
  );
}
const Etiqueta = ({ children }) => <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs text-pendente">{children}</span>;

/* ---------- Histórico e relatórios da escola ---------- */
function HistoricoEscola({ e }) {
  const lista = e.historico;
  const porDia = useMemo(() => { const m = new Map(); for (const h of lista) m.set(h.em.slice(0, 10), h); return [...m.entries()].slice(-30); }, [lista]);
  if (!lista.length) return <p className="rounded-2xl bg-white/60 p-6 text-center text-tinta-suave">O histórico da escola começa a ser registrado a cada leitura. Volte depois de algumas leituras.</p>;
  const max = Math.max(1, ...porDia.map(([, h]) => h.total));
  return (
    <>
      <section className="surgir vidro rounded-3xl p-6">
        <div className="flex items-baseline justify-between"><h2 className="text-[15px] font-semibold">Pendências da escola no bimestre</h2><span className="text-xs text-tinta-suave">última leitura de cada dia</span></div>
        <div className="mt-6 flex h-48 items-end gap-1.5" role="img" aria-label="Gráfico de pendências da escola por dia">
          {porDia.map(([dia, h], i) => (
            <div key={dia} className="group flex flex-1 flex-col items-center justify-end gap-1">
              <span className="text-[11px] tabular-nums text-tinta-suave opacity-0 transition group-hover:opacity-100">{h.total}</span>
              <div className={`w-full rounded-xl transition-[height] duration-500 ease-(--ease-entrada) ${i === porDia.length - 1 ? 'bg-lavanda' : 'bg-lavanda/35'}`} style={{ height: `${Math.max(6, (h.total / max) * 150)}px` }} title={`${brData(dia)}: ${h.total}`} />
              <span className="text-[10px] text-tinta-suave">{brData(dia)}</span>
            </div>
          ))}
        </div>
      </section>
      <div className="mt-5 overflow-hidden rounded-3xl bg-white/60">
        <table className="w-full text-sm">
          <thead className="text-left text-xs tracking-wide text-tinta-suave uppercase"><tr><th className="px-5 py-3">Quando</th><th className="px-3 py-3 text-right">Pendências</th><th className="px-5 py-3 text-right">Professores com pendência</th></tr></thead>
          <tbody>{[...lista].reverse().slice(0, 40).map((h) => (
            <tr key={h.em} className="border-t border-white"><td className="px-5 py-2.5">{new Date(h.em).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}</td>
              <td className="px-3 py-2.5 text-right font-semibold tabular-nums">{h.total}</td><td className="px-5 py-2.5 text-right tabular-nums">{Object.keys(h.porProfessor ?? {}).length}</td></tr>))}</tbody>
        </table>
      </div>
    </>
  );
}

function RelatoriosEscola({ e }) {
  const [aviso, setAviso] = useState(null);
  const salvar = async (formato) => {
    setAviso(null);
    const r = await sge.salvarRelatorio(formato, { periodo: e.periodo });
    if (r?.ok) setAviso({ ok: true, texto: `Relatório salvo em ${r.caminho}` }); else if (r?.mensagem) setAviso({ ok: false, texto: r.mensagem });
  };
  const Opcao = ({ icone: Icone, titulo, texto, formato }) => (
    <button type="button" onClick={() => salvar(formato)} className="surgir vidro flex items-start gap-4 rounded-3xl p-6 text-left transition hover:-translate-y-0.5 hover:bg-white/75">
      <span className="rounded-2xl bg-lavanda-clara p-3 text-lavanda"><Icone size={26} /></span>
      <span><b className="block text-lg">{titulo}</b><span className="text-sm text-tinta-suave">{texto}</span></span>
    </button>
  );
  return (
    <>
      <p className="mb-5 text-tinta-suave">Pendências da escola por professor, turma e componente, no período escolhido acima ({e.periodo === 'ano' ? 'ano letivo' : `${e.periodo}º bimestre`}).</p>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Opcao icone={FileText} titulo="Salvar em PDF" texto="Pronto para imprimir ou levar à reunião pedagógica." formato="coord-pdf" />
        <Opcao icone={FileSpreadsheet} titulo="Exportar planilha" texto="Arquivo CSV que abre no Excel ou no Google Planilhas." formato="coord-csv" />
      </div>
      {aviso && <p role="status" className={`mt-4 rounded-2xl px-4 py-3 text-sm ${aviso.ok ? 'bg-emerald-50 text-emerald-900' : 'bg-rose-50 text-rose-900'}`}>{aviso.texto}</p>}
    </>
  );
}

/* ---------- Painel da direção (uma página, para imprimir) ---------- */
function Direcao({ e }) {
  const { visao, historico, periodo } = e;
  const prof = porProfessor(visao).filter((p) => p.id); const turmas = porTurma(visao); const comps = porComponente(visao);
  const total = visao.professores.reduce((s, p) => s + p.total, 0);
  const ref = referenciaSemanal(historico.filter((h) => h.em !== visao.atualizadoEm));
  const antes = ref ? Object.values(ref.porProfessor ?? {}).reduce((s, v) => s + v, 0) : null;
  const nome = periodo === 'ano' ? 'Ano letivo' : `${periodo}º bimestre`;
  const Lista = ({ titulo, itens, detalhe }) => (
    <section className="break-inside-avoid rounded-2xl border border-tinta/10 p-4">
      <h2 className="mb-2 text-sm font-semibold">{titulo}</h2>
      {itens.length === 0 ? <p className="text-sm text-tinta-suave">Nenhuma pendência.</p> : (
        <ol className="space-y-1 text-sm">
          {itens.slice(0, 8).map((g, i) => <li key={g.chave} className="flex gap-2"><span className="w-5 text-tinta-suave tabular-nums">{i + 1}.</span><span className="min-w-0 flex-1 truncate">{g.rotulo} <span className="text-tinta-suave">· {detalhe(g)}</span></span><b className="tabular-nums text-pendente">{g.total}</b></li>)}
        </ol>
      )}
    </section>
  );
  return (
    <div className="mx-auto max-w-4xl">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold">Pendências do diário · {nome}</h2>
          <p className="text-sm text-tinta-suave">{visao.janela ? `${brData(visao.janela.inicio)} a ${brData(visao.janela.fim)} · ` : ''}dados lidos do SGE em {new Date(visao.atualizadoEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}</p>
        </div>
        <button type="button" onClick={() => window.print()} data-nao-imprimir className="flex shrink-0 items-center gap-2 rounded-full bg-lavanda px-5 py-2.5 text-sm font-semibold text-[#fff] hover:brightness-110">
          <Printer size={16} aria-hidden /> Imprimir ou salvar em PDF
        </button>
      </div>
      <p className="mb-4 text-xs text-tinta-suave" data-nao-imprimir>Escolha o período nos botões acima da tela antes de imprimir. Na janela de impressão, “Salvar como PDF” gera o arquivo.</p>
      <div className="grid grid-cols-4 gap-3">
        <Indicador valor={total} rotulo="pendências no período" cor="text-pendente" />
        <Indicador valor={`${prof.length}/${visao.totalProfessores}`} rotulo="professores com pendência" />
        <Indicador valor={`${turmas.length}/${visao.turmas.length}`} rotulo="turmas com pendência" />
        <Indicador valor={comps.length} rotulo="componentes com pendência" />
      </div>
      {antes != null && <p className="mt-4 text-sm">Desde {new Date(ref.em).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}: de <b>{antes}</b> para <b className={total <= antes ? 'text-emdia' : 'text-pendente'}>{total}</b> pendências ({total <= antes ? 'queda' : 'alta'} de {Math.abs(total - antes)}).</p>}
      <div className="mt-5 grid grid-cols-2 gap-4">
        <Lista titulo="Professores com mais pendências" itens={prof} detalhe={(g) => plural(g.turmas.length, 'turma', 'turmas')} />
        <Lista titulo="Turmas com mais pendências" itens={turmas} detalhe={(g) => plural(g.componentes.length, 'componente', 'componentes')} />
        <Lista titulo="Componentes com mais pendências" itens={comps} detalhe={(g) => plural(g.turmas.length, 'turma', 'turmas')} />
        <section className="break-inside-avoid rounded-2xl border border-tinta/10 p-4">
          <h2 className="mb-2 text-sm font-semibold">Professores em dia</h2>
          <p className="text-sm">{visao.semPendencia?.length ? visao.semPendencia.join(', ') : 'Nenhum neste período.'}</p>
          {visao.semHorario?.length > 0 && <p className="mt-3 text-xs text-tinta-suave">Fora das contagens (sem horário atual): {visao.semHorario.length}.</p>}
        </section>
      </div>
    </div>
  );
}

/* ---------- Primeira leitura ---------- */
function PrimeiraLeitura({ turmasEscola, perfilEscola, sincronizando, aoAbrirSge }) {
  return (
    <div className="mx-auto max-w-2xl py-8">
      <div className="text-center">
        <School size={48} className="mx-auto text-lavanda" strokeWidth={1.5} />
        <h2 className="mt-4 text-2xl font-semibold">Coordenação</h2>
        <p className="mt-2 text-tinta-suave">Pendências da escola inteira, por professor, turma e componente. O app só consulta: o coordenador corrige no SGE.</p>
      </div>
      <ol className="mt-8 space-y-3">
        <Passo n={1} feito={perfilEscola}>Entre no SGE com o perfil <b>Unidade escolar</b>.</Passo>
        <Passo n={2} feito={turmasEscola > 0}>No SGE, abra <b>Escola → Conteúdo Aplicado</b>. O app lê a lista de turmas sozinho{turmasEscola > 0 ? ` (${turmasEscola} turmas lidas).` : '.'}</Passo>
        <Passo n={3}>Clique em <b>Ler a escola</b>. Nada é gravado no SGE.</Passo>
      </ol>
      <div className="mt-8 flex justify-center gap-3">
        <button type="button" onClick={() => aoAbrirSge()} className="rounded-full bg-white px-5 py-2.5 text-sm font-semibold ring-1 ring-tinta/10 hover:bg-white/70">Abrir o SGE para consulta</button>
        <button type="button" onClick={() => sge.sincronizarEscola()} disabled={!turmasEscola || sincronizando} className="rounded-full bg-tinta px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-40">Ler a escola</button>
      </div>
    </div>
  );
}

/* ---------- Mensagens de cobrança ---------- */
function Cobranca({ professores, aoFechar }) {
  const [prazo, setPrazo] = useState('até sexta-feira');
  const [coordenador, setCoordenador] = useState('');
  const [copiado, setCopiado] = useState(null);
  useEffect(() => {
    const tecla = (ev) => ev.key === 'Escape' && aoFechar();
    window.addEventListener('keydown', tecla);
    return () => window.removeEventListener('keydown', tecla);
  }, [aoFechar]);
  const mensagens = useMemo(() => mensagensEmLote(professores, { prazo, coordenador }), [professores, prazo, coordenador]);
  const copiar = async (chave, texto) => { await navigator.clipboard.writeText(texto); setCopiado(chave); setTimeout(() => setCopiado((c) => (c === chave ? null : c)), 1800); };
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-tinta/20 p-6 backdrop-blur-[2px]" onClick={aoFechar}>
      <div role="dialog" aria-modal="true" aria-label="Mensagens de cobrança" onClick={(ev) => ev.stopPropagation()} className="vidro-forte surgir flex max-h-full w-[min(760px,100%)] flex-col rounded-[28px] p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-3">
          <div><h2 className="text-xl font-semibold">Mensagens de cobrança</h2><p className="text-sm text-tinta-suave">Uma por professor, no período escolhido, pronta para colar no WhatsApp. O app não envia nada.</p></div>
          <button type="button" onClick={aoFechar} aria-label="Fechar" className="rounded-full p-2 text-tinta-suave hover:bg-tinta/5"><X size={20} /></button>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <label className="text-sm text-tinta-suave">Prazo<input value={prazo} onChange={(ev) => setPrazo(ev.target.value)} placeholder="até sexta-feira" className="mt-1 w-full rounded-xl bg-white/80 px-3 py-2 text-tinta ring-1 ring-white outline-none focus:ring-lavanda/40" /></label>
          <label className="text-sm text-tinta-suave">Assinatura (opcional)<input value={coordenador} onChange={(ev) => setCoordenador(ev.target.value)} placeholder="Coordenação pedagógica" className="mt-1 w-full rounded-xl bg-white/80 px-3 py-2 text-tinta ring-1 ring-white outline-none focus:ring-lavanda/40" /></label>
        </div>
        <div className="mt-4 min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
          {mensagens.length === 0 && <p className="rounded-2xl bg-white/60 p-5 text-center text-tinta-suave">Nenhum professor identificado com pendência.</p>}
          {mensagens.map((m) => (
            <article key={m.professor} className="rounded-2xl bg-white/70 p-4">
              <div className="mb-2 flex items-center justify-between gap-3"><b>{m.professor}</b>
                <button type="button" onClick={() => copiar(m.professor, m.texto)} className="flex items-center gap-1.5 rounded-full bg-lavanda-clara px-3 py-1.5 text-xs font-semibold text-lavanda hover:bg-lavanda hover:text-[#fff]">
                  {copiado === m.professor ? <><Check size={14} aria-hidden /> Copiado</> : <><Copy size={14} aria-hidden /> Copiar</>}</button></div>
              <pre className="font-sans text-sm whitespace-pre-wrap text-tinta">{m.texto}</pre>
            </article>
          ))}
        </div>
        {mensagens.length > 1 && (
          <button type="button" onClick={() => copiar('todas', mensagens.map((m) => `*${m.professor}*\n${m.texto}`).join('\n\n———\n\n'))} className="mt-4 flex h-11 items-center justify-center gap-2 rounded-full bg-tinta text-sm font-semibold text-white hover:opacity-90">
            {copiado === 'todas' ? <><Check size={18} aria-hidden /> Copiado</> : <><Copy size={18} aria-hidden /> Copiar todas</>}</button>
        )}
      </div>
    </div>
  );
}

/* ---------- Peças comuns ---------- */
function Variacao({ icone: Icone, cor, titulo, itens, vazio }) {
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
function Passo({ n, feito, children }) {
  return (
    <li className="flex items-start gap-3 rounded-2xl bg-white/70 px-4 py-3">
      <span className={`flex size-7 shrink-0 items-center justify-center rounded-full text-sm font-bold ${feito ? 'bg-emdia text-[#fff]' : 'bg-lavanda-clara text-lavanda'}`}>{feito ? '✓' : n}</span>
      <span className="pt-0.5">{children}</span>
    </li>
  );
}
function Indicador({ valor, rotulo, cor = 'text-tinta' }) {
  return (<div className="surgir vidro rounded-3xl p-5"><b className={`block text-4xl font-semibold tabular-nums ${cor}`}>{valor}</b><span className="text-sm text-tinta-suave">{rotulo}</span></div>);
}
function Numero({ valor, rotulo, cor = 'text-tinta' }) {
  return (<div className="rounded-2xl bg-white/70 px-2 py-3"><b className={`block text-2xl tabular-nums ${cor}`}>{valor}</b><span className="text-xs text-tinta-suave">{rotulo}</span></div>);
}

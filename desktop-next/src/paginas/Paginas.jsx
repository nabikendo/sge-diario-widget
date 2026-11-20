import { useEffect, useMemo, useState } from 'react';
import { FileSpreadsheet, FileText, FolderOpen, LogOut, Trash2, School, Info } from 'lucide-react';
import CartaoTurma from '../components/CartaoTurma';
import { CardResumo, CardSincronizacao } from '../components/CardsTopo';
import { sge } from '../lib/sge';

const PESO = { pendente: 0, erro: 1, 'em-dia': 2 };
const ordenar = (lista) => [...lista].sort((a, b) => PESO[a.status] - PESO[b.status]
  || (b.pendentes?.length ?? 0) - (a.pendentes?.length ?? 0) || a.nome.localeCompare(b.nome));

function GradeTurmas({ turmas, aoAbrir, vazio = 'Nenhuma turma encontrada.' }) {
  if (!turmas.length) return <p className="rounded-2xl bg-white/60 p-6 text-center text-tinta-suave">{vazio}</p>;
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
      {turmas.map((t, i) => <CartaoTurma key={`${t.id}/${t.disciplina}`} turma={t} indice={i} aoAbrir={aoAbrir} />)}
    </div>
  );
}

function Titulo({ children, direita }) {
  return (
    <div className="mt-8 mb-3 flex items-baseline justify-between first:mt-0">
      <h2 className="text-lg font-semibold">{children}</h2>
      {direita && <span className="text-sm text-tinta-suave">{direita}</span>}
    </div>
  );
}

/* ---------- Home ---------- */
export function Inicio({ painel, turmas, progresso, sincronizando, aoAbrir }) {
  const urgentes = turmas.filter((t) => t.status !== 'em-dia');
  return (
    <>
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.25fr_1fr]">
        <CardResumo painel={painel} />
        <CardSincronizacao painel={painel} progresso={progresso} sincronizando={sincronizando} />
      </div>
      <Titulo direita={`${urgentes.length} de ${turmas.length}`}>Precisam de atenção</Titulo>
      <GradeTurmas turmas={urgentes} aoAbrir={aoAbrir} vazio="Todas as turmas estão em dia." />
    </>
  );
}

/* ---------- Turmas ---------- */
const FILTROS = [
  { id: 'todas', rotulo: 'Todas', f: () => true },
  { id: 'pendente', rotulo: 'Pendentes', f: (t) => t.status === 'pendente' },
  { id: 'em-dia', rotulo: 'Em dia', f: (t) => t.status === 'em-dia' },
  { id: 'erro', rotulo: 'Não lidas', f: (t) => t.status === 'erro' },
];

export function Turmas({ turmas, aoAbrir }) {
  const [filtro, setFiltro] = useState('todas');
  const [porEscola, setPorEscola] = useState(true);
  const lista = turmas.filter(FILTROS.find((x) => x.id === filtro).f);
  const grupos = porEscola ? Object.entries(Object.groupBy?.(lista, (t) => t.escola || 'Sem escola') ?? { Todas: lista }) : [['', lista]];
  return (
    <>
      <div className="mb-5 flex flex-wrap items-center gap-2">
        {FILTROS.map((x) => {
          const n = turmas.filter(x.f).length;
          const sel = filtro === x.id;
          return (
            <button key={x.id} type="button" onClick={() => setFiltro(x.id)} aria-pressed={sel}
              className={`rounded-full px-4 py-2 text-sm font-medium transition ${sel ? 'bg-tinta text-white' : 'bg-white/70 text-tinta-suave ring-1 ring-white hover:bg-white'}`}>
              {x.rotulo} <span className={`ml-1 tabular-nums ${sel ? 'text-white/70' : ''}`}>{n}</span>
            </button>
          );
        })}
        <label className="ml-auto flex items-center gap-2 text-sm text-tinta-suave">
          <input type="checkbox" checked={porEscola} onChange={(e) => setPorEscola(e.target.checked)} className="size-4 accent-lavanda" />
          Agrupar por escola
        </label>
      </div>
      {grupos.map(([escola, ts]) => (
        <section key={escola} className="mb-6">
          {escola && <Titulo direita={`${ts.length} ${ts.length === 1 ? 'turma' : 'turmas'}`}>{escola}</Titulo>}
          <GradeTurmas turmas={ordenar(ts)} aoAbrir={aoAbrir} />
        </section>
      ))}
    </>
  );
}

/* ---------- Coordenação ---------- */
export function Coordenacao({ perfilEscola, sincronizando }) {
  const [dados, setDados] = useState(null);
  const [aberto, setAberto] = useState(null);
  const [busca, setBusca] = useState('');
  useEffect(() => {
    const carregar = () => sge.obterCoordenacao().then(setDados);
    carregar();
    const desligar = [sge.aoCoordenacao(carregar), sge.aoTurmasEscola(carregar)];
    return () => desligar.forEach((f) => f());
  }, []);
  if (!dados) return null;
  const { painel, turmasEscola } = dados;

  if (!painel) {
    return (
      <div className="mx-auto max-w-2xl py-8">
        <div className="text-center">
          <School size={48} className="mx-auto text-lavanda" strokeWidth={1.5} />
          <h2 className="mt-4 text-2xl font-semibold">Coordenação</h2>
          <p className="mt-2 text-tinta-suave">Pendências de Conteúdo Aplicado de todos os professores da escola, atribuídas pelo horário de cada um.</p>
        </div>
        <ol className="mt-8 space-y-3">
          <Passo n={1} feito={perfilEscola}>Entre no SGE com o perfil <b>Unidade escolar</b>.</Passo>
          <Passo n={2} feito={turmasEscola > 0}>
            No SGE, abra <b>Escola → Conteúdo Aplicado</b>. O app lê a lista de turmas sozinho{turmasEscola > 0 ? ` (${turmasEscola} turmas lidas).` : '.'}
          </Passo>
          <Passo n={3}>Clique em <b>Ler a escola</b>. O app só consulta: nada é gravado no SGE.</Passo>
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
  const dias = painel.professores.reduce((s, p) => s + p.dias, 0);
  const comPendencia = painel.professores.filter((p) => p.id).length;
  const max = Math.max(1, ...painel.professores.map((p) => p.dias));

  return (
    <>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <Indicador valor={dias} rotulo="aulas sem conteúdo na escola" cor="text-pendente" />
        <Indicador valor={`${comPendencia}/${painel.totalProfessores}`} rotulo="professores com pendência" />
        <Indicador valor={painel.turmas.length} rotulo="turmas lidas" />
      </div>

      <div className="mt-8 mb-3 flex flex-wrap items-center gap-3">
        <h2 className="text-lg font-semibold">Por professor</h2>
        <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Filtrar professor, turma ou componente"
          className="ml-auto w-72 rounded-full bg-white/80 px-4 py-2 text-sm ring-1 ring-white outline-none focus:ring-lavanda/40" />
        <button type="button" onClick={() => sge.salvarRelatorio('coord-pdf')} className="rounded-full bg-white px-4 py-2 text-sm font-semibold ring-1 ring-tinta/10 hover:bg-white/70">PDF</button>
        <button type="button" onClick={() => sge.salvarRelatorio('coord-csv')} className="rounded-full bg-white px-4 py-2 text-sm font-semibold ring-1 ring-tinta/10 hover:bg-white/70">Planilha</button>
        <button type="button" onClick={() => sge.sincronizarEscola()} disabled={sincronizando}
          className="rounded-full bg-tinta px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">{sincronizando ? 'Lendo…' : 'Ler de novo'}</button>
      </div>
      <p className="mb-3 text-xs text-tinta-suave">Lido em {new Date(painel.atualizadoEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })} · {turmasEscola} turmas da escola conhecidas</p>

      <ul className="space-y-2">
        {professores.map((p, i) => {
          const abertoAqui = aberto === (p.id || p.nome);
          return (
            <li key={p.id || p.nome} className="surgir overflow-hidden rounded-2xl bg-white/70" style={{ '--atraso': `${Math.min(i, 8) * 30}ms` }}>
              <button type="button" onClick={() => setAberto(abertoAqui ? null : (p.id || p.nome))} aria-expanded={abertoAqui}
                className="flex w-full items-center gap-4 px-5 py-3.5 text-left hover:bg-white/60">
                <span className="w-64 shrink-0 truncate font-medium">{p.nome}</span>
                <span className="h-2.5 flex-1 overflow-hidden rounded-full bg-tinta/[0.07]">
                  <span className="block h-full rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-400" style={{ width: `${(p.dias / max) * 100}%` }} />
                </span>
                <span className="w-24 shrink-0 text-right text-sm"><b className="tabular-nums text-pendente">{p.dias}</b> {p.dias === 1 ? 'aula' : 'aulas'}</span>
              </button>
              {abertoAqui && (
                <div className="border-t border-white px-5 py-3">
                  {p.itens.map((it) => (
                    <div key={`${it.turmaId}|${it.componente}`} className="flex flex-wrap items-center gap-2 py-1.5 text-sm">
                      <span className="w-56 truncate font-medium">{it.turma}</span>
                      <span className="w-40 truncate text-tinta-suave">{it.componente}</span>
                      <span className="flex flex-1 flex-wrap gap-1">{it.datas.map((d) => <span key={d} className="rounded-full bg-rose-50 px-2 py-0.5 text-xs text-pendente">{d.slice(8, 10)}/{d.slice(5, 7)}</span>)}</span>
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
    </>
  );
}

function Passo({ n, feito, children }) {
  return (
    <li className="flex items-start gap-3 rounded-2xl bg-white/70 px-4 py-3">
      <span className={`flex size-7 shrink-0 items-center justify-center rounded-full text-sm font-bold ${feito ? 'bg-emdia text-white' : 'bg-lavanda-clara text-lavanda'}`}>{feito ? '✓' : n}</span>
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

/* ---------- Histórico ---------- */
export function Historico() {
  const [lista, setLista] = useState(null);
  useEffect(() => { sge.obterHistorico().then(setLista); }, []);
  // Uma barra por dia: o último registro de cada dia.
  const porDia = useMemo(() => {
    const m = new Map();
    for (const h of lista ?? []) m.set(h.em.slice(0, 10), h);
    return [...m.entries()].slice(-30);
  }, [lista]);
  if (!lista) return null;
  if (!lista.length) return <p className="rounded-2xl bg-white/60 p-6 text-center text-tinta-suave">O histórico começa a ser registrado a partir da próxima sincronização.</p>;
  const max = Math.max(1, ...porDia.map(([, h]) => h.dias));
  return (
    <>
      <section className="surgir vidro rounded-3xl p-6">
        <div className="flex items-baseline justify-between">
          <h2 className="text-[15px] font-semibold">Dias sem conteúdo ao longo do tempo</h2>
          <span className="text-xs text-tinta-suave">último registro de cada dia</span>
        </div>
        <div className="mt-6 flex h-48 items-end gap-1.5" role="img" aria-label="Gráfico de dias sem conteúdo por dia">
          {porDia.map(([dia, h], i) => (
            <div key={dia} className="group flex flex-1 flex-col items-center justify-end gap-1">
              <span className="text-[11px] tabular-nums text-tinta-suave opacity-0 transition group-hover:opacity-100">{h.dias}</span>
              <div className={`w-full rounded-xl transition-[height] duration-500 ease-(--ease-entrada) ${i === porDia.length - 1 ? 'bg-lavanda' : 'bg-lavanda/35'}`}
                style={{ height: `${Math.max(6, (h.dias / max) * 150)}px` }} title={`${dia.split('-').reverse().join('/')}: ${h.dias} dias`} />
              <span className="text-[10px] text-tinta-suave">{dia.slice(8, 10)}/{dia.slice(5, 7)}</span>
            </div>
          ))}
        </div>
      </section>
      <Titulo direita={`${lista.length} sincronizações`}>Sincronizações</Titulo>
      <div className="overflow-hidden rounded-3xl bg-white/60">
        <table className="w-full text-sm">
          <thead className="text-left text-xs tracking-wide text-tinta-suave uppercase">
            <tr><th className="px-5 py-3">Quando</th><th className="px-3 py-3 text-right">Dias sem conteúdo</th><th className="px-3 py-3 text-right">Pendentes</th><th className="px-3 py-3 text-right">Em dia</th><th className="px-5 py-3 text-right">Não lidas</th></tr>
          </thead>
          <tbody>
            {[...lista].reverse().slice(0, 50).map((h) => (
              <tr key={h.em} className="border-t border-white">
                <td className="px-5 py-2.5">{new Date(h.em).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}</td>
                <td className="px-3 py-2.5 text-right font-semibold tabular-nums">{h.dias}</td>
                <td className="px-3 py-2.5 text-right tabular-nums text-pendente">{h.pendentes}</td>
                <td className="px-3 py-2.5 text-right tabular-nums text-emdia">{h.emDia}</td>
                <td className="px-5 py-2.5 text-right tabular-nums text-alerta">{h.erros || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

/* ---------- Relatórios ---------- */
export function Relatorios({ painel }) {
  const [aviso, setAviso] = useState(null);
  const salvar = async (formato) => {
    setAviso(null);
    const r = await sge.salvarRelatorio(formato);
    if (r?.ok) setAviso({ ok: true, texto: `Relatório salvo em ${r.caminho}` });
    else if (r?.mensagem) setAviso({ ok: false, texto: r.mensagem });
  };
  const Opcao = ({ icone: Icone, titulo, texto, formato }) => (
    <button type="button" onClick={() => salvar(formato)} disabled={!painel}
      className="surgir vidro group flex items-start gap-4 rounded-3xl p-6 text-left transition hover:-translate-y-0.5 hover:bg-white/75 disabled:opacity-50">
      <span className="rounded-2xl bg-lavanda-clara p-3 text-lavanda"><Icone size={26} /></span>
      <span><b className="block text-lg">{titulo}</b><span className="text-sm text-tinta-suave">{texto}</span></span>
    </button>
  );
  return (
    <>
      <p className="mb-5 text-tinta-suave">Relatório de Conteúdo Aplicado do bimestre, com as turmas, a situação e as datas sem conteúdo.{!painel && ' Sincronize primeiro.'}</p>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Opcao icone={FileText} titulo="Salvar em PDF" texto="Pronto para imprimir ou enviar à coordenação." formato="pdf" />
        <Opcao icone={FileSpreadsheet} titulo="Exportar planilha" texto="Arquivo CSV que abre no Excel ou no Google Planilhas." formato="csv" />
      </div>
      {aviso && <p role="status" className={`mt-4 rounded-2xl px-4 py-3 text-sm ${aviso.ok ? 'bg-emerald-50 text-emerald-900' : 'bg-rose-50 text-rose-900'}`}>{aviso.texto}</p>}
    </>
  );
}

/* ---------- Configurações ---------- */
export function Configuracoes({ logado, aoSair }) {
  const [c, setC] = useState(null);
  useEffect(() => { sge.obterConfig().then(setC); }, []);
  if (!c) return null;
  const mudar = async (parcial) => setC(await sge.salvarConfig(parcial));

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <Grupo titulo="Sincronização">
        <Linha titulo="Sincronizar automaticamente" texto="Além de ao abrir o app e ao voltar da suspensão.">
          <select value={c.intervaloMin} onChange={(e) => mudar({ intervaloMin: Number(e.target.value) })}
            className="rounded-full bg-white px-4 py-2 text-sm ring-1 ring-tinta/10 outline-none focus:ring-lavanda">
            <option value={15}>A cada 15 min</option><option value={30}>A cada 30 min</option>
            <option value={60}>A cada 1 hora</option><option value={120}>A cada 2 horas</option><option value={0}>Só manualmente</option>
          </select>
        </Linha>
        <Linha titulo="Incluir turmas da Parte Diversificada" texto="Turmas com 3 dígitos (ex.: 331.01). Vale a partir da próxima sincronização.">
          <Chave ligada={c.incluirDiversificada} aoMudar={(v) => mudar({ incluirDiversificada: v })} />
        </Linha>
        <Linha titulo="Incluir turmas de AEE" texto="Turmas com 4 dígitos.">
          <Chave ligada={c.incluirAee} aoMudar={(v) => mudar({ incluirAee: v })} />
        </Linha>
      </Grupo>

      <Grupo titulo="Janela">
        <Linha titulo="Abrir maximizado"><Chave ligada={c.abrirMaximizado} aoMudar={(v) => mudar({ abrirMaximizado: v })} /></Linha>
        <Linha titulo="Abrir junto com o Windows" texto="O app abre quando você entra no Windows e já sincroniza se a sessão do SGE estiver ativa."><Chave ligada={c.abrirComWindows} aoMudar={(v) => mudar({ abrirComWindows: v })} /></Linha>
      </Grupo>

      <Grupo titulo="Conta e dados">
        <Linha titulo={logado === false ? 'Você não está conectado ao SGE' : 'Sair do SGE neste computador'} texto="Apaga a sessão do SGE guardada no app. A senha nunca é guardada.">
          <Botao icone={LogOut} onClick={aoSair} disabled={logado === false}>Sair</Botao>
        </Linha>
        <Linha titulo="Pasta de dados" texto="Configurações, último painel e histórico ficam neste computador.">
          <Botao icone={FolderOpen} onClick={() => sge.abrirPastaDados()}>Abrir</Botao>
        </Linha>
        <Linha titulo="Apagar histórico e painel salvo" texto="Não afeta nada no SGE.">
          <Botao icone={Trash2} perigo onClick={async () => { if (confirm('Apagar o histórico e o painel salvo neste computador?')) await sge.apagarDados(); }}>Apagar</Botao>
        </Linha>
      </Grupo>

      <p className="flex items-center justify-center gap-2 pb-4 text-xs text-tinta-suave"><Info size={14} /> SGE Diário Desktop {c.versao} · somente leitura: o app nunca grava nada no SGE.</p>
    </div>
  );
}

function Grupo({ titulo, children }) {
  return (
    <section className="surgir vidro rounded-3xl p-2">
      <h2 className="px-4 pt-3 pb-1 text-xs font-bold tracking-[0.1em] text-tinta-suave uppercase">{titulo}</h2>
      <div className="divide-y divide-tinta/5">{children}</div>
    </section>
  );
}
function Linha({ titulo, texto, children }) {
  return (
    <div className="flex items-center justify-between gap-6 px-4 py-3.5">
      <div><p className="font-medium">{titulo}</p>{texto && <p className="text-sm text-tinta-suave">{texto}</p>}</div>
      <div className="shrink-0">{children}</div>
    </div>
  );
}
function Chave({ ligada, aoMudar }) {
  return (
    <button type="button" role="switch" aria-checked={ligada} onClick={() => aoMudar(!ligada)}
      className={`relative h-7 w-12 rounded-full transition-colors duration-200 ${ligada ? 'bg-lavanda' : 'bg-tinta/15'}`}>
      <span className={`absolute top-0.5 left-0.5 size-6 rounded-full bg-white shadow transition-transform duration-200 ease-(--ease-padrao) ${ligada ? 'translate-x-5' : ''}`} />
    </button>
  );
}
function Botao({ icone: Icone, children, perigo, ...props }) {
  return (
    <button type="button" {...props}
      className={`flex items-center gap-2 rounded-full px-4 py-2 text-sm font-medium ring-1 transition disabled:opacity-40 ${perigo ? 'text-pendente ring-rose-200 hover:bg-rose-50' : 'bg-white ring-tinta/10 hover:bg-white/70'}`}>
      <Icone size={16} /> {children}
    </button>
  );
}

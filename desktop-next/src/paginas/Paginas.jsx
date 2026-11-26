import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { FileSpreadsheet, FileText, FolderOpen, LogOut, Trash2, Info, TrendingDown, TrendingUp, RefreshCw, Cloud, CloudUpload, CloudDownload } from 'lucide-react';
import { referenciaSemanal, diferencas } from '../lib/comparacao.mjs';
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
  const ultimo = lista[lista.length - 1];
  const ref = referenciaSemanal(lista.slice(0, -1));
  const semana = ref && ref.porTurma && ultimo.porTurma ? diferencas(ultimo.porTurma, ref.porTurma) : null;
  return (
    <>
      {semana && (
        <section className="surgir vidro mb-5 rounded-3xl p-6">
          <h2 className="text-[15px] font-semibold">Desde a semana passada <span className="font-normal text-tinta-suave">· comparado com {new Date(ref.em).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}</span></h2>
          <p className="mt-1 text-sm text-tinta-suave">Dias sem conteúdo: de {semana.totalAntes} para <b className="text-tinta">{semana.totalAgora}</b>.</p>
          <div className="mt-3 grid gap-4 md:grid-cols-2">
            <Variacao icone={TrendingDown} cor="text-emdia" titulo="Melhoraram" vazio="Nenhuma turma reduziu." itens={semana.melhoraram} />
            <Variacao icone={TrendingUp} cor="text-pendente" titulo="Pioraram" vazio="Nenhuma turma piorou." itens={semana.pioraram} />
          </div>
        </section>
      )}
      <section className="surgir vidro rounded-3xl p-6">
        <div className="flex items-baseline justify-between">
          <h2 className="text-[15px] font-semibold">Dias sem conteúdo ao longo do tempo</h2>
          <span className="text-xs text-tinta-suave">último registro de cada dia</span>
        </div>
        <div className="mt-6 flex h-48 items-end gap-1.5" role="img" aria-label="Gráfico de dias sem conteúdo por dia">
          {porDia.map(([dia, h], i) => (
            <div key={dia} className="group flex flex-1 flex-col items-center justify-end gap-1">
              <span className="text-[11px] tabular-nums text-tinta-suave opacity-0 transition group-hover:opacity-100">{h.dias}</span>
              <div className={`w-full rounded-xl transition-[height] duration-500 ease-(--ease-entrada) ${i === porDia.length - 1 ? 'bg-lavanda-solido' : 'bg-lavanda-solido/35'}`}
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

function Variacao({ icone: Icone, cor, titulo, itens, vazio }) {
  return (
    <div>
      <h3 className={`flex items-center gap-1.5 text-sm font-semibold ${cor}`}><Icone size={16} aria-hidden /> {titulo}</h3>
      {itens.length === 0 ? <p className="mt-1 text-sm text-tinta-suave">{vazio}</p> : (
        <ul className="mt-1 space-y-0.5 text-sm">
          {itens.slice(0, 6).map((i) => <li key={i.chave} className="flex justify-between gap-3"><span className="truncate">{i.chave}</span><span className="tabular-nums text-tinta-suave">{i.antes} → <b className={cor}>{i.agora}</b></span></li>)}
        </ul>
      )}
    </div>
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
      <span className="rounded-2xl bg-lavanda-clara p-3 text-lavanda-texto"><Icone size={26} /></span>
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
export function Configuracoes({ logado, aoSair, atualizacao, aoVerTutorial }) {
  const [c, setC] = useState(null);
  useEffect(() => { sge.obterConfig().then(setC); }, []);
  if (!c) return null;
  const sistema = c.sistema ?? 'Windows'; // nome do sistema nos textos (Windows ou Linux)
  const mudar = async (parcial) => setC(await sge.salvarConfig(parcial));

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <Grupo titulo="Sincronização">
        <Linha titulo="Sincronizar automaticamente" texto="Além de ao abrir o app e ao voltar da suspensão.">
          <select aria-label="Intervalo de sincronização" value={c.intervaloMin} onChange={(e) => mudar({ intervaloMin: Number(e.target.value) })}
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

      <Grupo titulo="Aparência">
        <Linha titulo="Tema" texto={`Claro, escuro ou o mesmo do ${sistema}.`}>
          <select aria-label="Tema" value={c.tema} onChange={(e) => mudar({ tema: e.target.value })}
            className="rounded-full bg-white px-4 py-2 text-sm ring-1 ring-tinta/10 outline-none focus:ring-lavanda">
            <option value="sistema">Do {sistema}</option><option value="claro">Claro</option><option value="escuro">Escuro</option>
          </select>
        </Linha>
      </Grupo>

      <Grupo titulo="Avisos">
        <Linha titulo="Notificar pendência nova" texto={`Notificação do ${sistema} quando a sincronização achar uma pendência que não existia (se o app não estiver à sua frente).`}>
          <Chave ligada={c.notificarNovas} aoMudar={(v) => mudar({ notificarNovas: v })} />
        </Linha>
        <Linha titulo="Resumo no fim da tarde" texto="Uma notificação por dia letivo: quantas aulas você deu hoje e o que falta registrar delas.">
          <Chave ligada={c.avisoFimDia} aoMudar={(v) => mudar({ avisoFimDia: v })} />
        </Linha>
        <Linha titulo="Horário do resumo">
          <input type="time" aria-label="Horário do resumo" value={c.horaAviso} disabled={!c.avisoFimDia} onChange={(e) => e.target.value && mudar({ horaAviso: e.target.value })}
            className="rounded-full bg-white px-4 py-2 text-sm ring-1 ring-tinta/10 outline-none focus:ring-lavanda disabled:opacity-40" />
        </Linha>
        <Linha titulo="Manter na bandeja ao fechar" texto="Fechar a janela deixa o app ao lado do relógio, com o número de pendências no ícone. Para sair de vez: clique com o botão direito no ícone → Sair.">
          <Chave ligada={c.fecharNaBandeja} aoMudar={(v) => mudar({ fecharNaBandeja: v })} />
        </Linha>
      </Grupo>

      <Grupo titulo="Coordenação (perfil Unidade escolar)">
        <Linha titulo="Ler também a Chamada" texto="Inclui as chamadas não concluídas de cada professor (bimestre atual). Faz mais consultas ao SGE; a leitura da escola fica mais lenta.">
          <Chave ligada={c.coordChamada} aoMudar={(v) => mudar({ coordChamada: v })} />
        </Linha>
        <Linha titulo="Ler também Plano de aula, Plano de curso e PEI" texto="Muitas consultas por professor e componente: a primeira leitura pode levar vários minutos.">
          <Chave ligada={c.coordPlanos} aoMudar={(v) => mudar({ coordPlanos: v })} />
        </Linha>
      </Grupo>

      <BackupNuvem c={c} mudar={mudar} />

      <Grupo titulo="Calendário escolar">
        <div className="px-4 py-3.5">
          <p className="font-medium">Dias sem aula</p>
          <p className="text-sm text-tinta-suave">Feriados nacionais, de Tocantins, Carnaval, Sexta-feira Santa e Corpus Christi já são considerados: não entram nas pendências nem geram aviso. Acrescente aqui recessos e reuniões, uma data por linha (ex.: 27/11/2026 Recesso).</p>
          <textarea aria-label="Dias sem aula" key={`s${c.versao}`} rows={3} defaultValue={c.diasSemAula} onBlur={(e) => e.target.value !== c.diasSemAula && mudar({ diasSemAula: e.target.value })} placeholder="27/11/2026 Recesso"
            className="mt-2 w-full rounded-2xl bg-white px-4 py-2.5 text-sm ring-1 ring-tinta/10 outline-none focus:ring-lavanda" />
        </div>
        <div className="px-4 py-3.5">
          <p className="font-medium">Sábados letivos e feriados trabalhados</p>
          <p className="text-sm text-tinta-suave">Estas datas voltam a ser dias de aula (ex.: 21/11/2026).</p>
          <textarea aria-label="Sábados letivos e feriados trabalhados" key={`l${c.versao}`} rows={2} defaultValue={c.diasLetivos} onBlur={(e) => e.target.value !== c.diasLetivos && mudar({ diasLetivos: e.target.value })} placeholder="21/11/2026"
            className="mt-2 w-full rounded-2xl bg-white px-4 py-2.5 text-sm ring-1 ring-tinta/10 outline-none focus:ring-lavanda" />
        </div>
      </Grupo>

      <Grupo titulo="Ajuda">
        <Linha titulo="Tutorial de introdução" texto="Revê o passo a passo de boas-vindas."><Botao icone={Info} onClick={aoVerTutorial}>Ver de novo</Botao></Linha>
      </Grupo>

      <Grupo titulo="Atualizações">
        <Linha titulo={`Versão ${c.versao}`} texto={textoAtualizacao(atualizacao)}>
          {atualizacao?.estado === 'pronta'
            ? <Botao icone={RefreshCw} onClick={() => sge.instalarAtualizacao()}>Reiniciar e instalar</Botao>
            : <Botao icone={RefreshCw} onClick={() => sge.verificarAtualizacao()} disabled={atualizacao?.estado === 'procurando' || atualizacao?.estado === 'baixando'}>Verificar agora</Botao>}
        </Linha>
      </Grupo>

      <Grupo titulo="Janela">
        <Linha titulo="Abrir maximizado"><Chave ligada={c.abrirMaximizado} aoMudar={(v) => mudar({ abrirMaximizado: v })} /></Linha>
        <Linha titulo={`Abrir junto com o ${sistema}`} texto={`O app abre quando você entra no ${sistema} e já sincroniza se a sessão do SGE estiver ativa.`}><Chave ligada={c.abrirComWindows} aoMudar={(v) => mudar({ abrirComWindows: v })} /></Linha>
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

function textoAtualizacao(a) {
  if (!a) return 'O app procura versões novas sozinho e instala ao fechar.';
  return { procurando: 'Procurando versão nova…', baixando: `Baixando a versão ${a.versao}…`, pronta: `A versão ${a.versao} está pronta para instalar.`,
    atual: 'Você está na versão mais recente.', erro: a.mensagem, indisponivel: a.mensagem }[a.estado] ?? '';
}

function Grupo({ titulo, children }) {
  return (
    <section className="surgir vidro rounded-3xl p-2">
      <h2 className="px-4 pt-3 pb-1 text-xs font-bold tracking-[0.1em] text-tinta-suave uppercase">{titulo}</h2>
      <div className="divide-y divide-tinta/5">{children}</div>
    </section>
  );
}
const RotuloCtx = createContext('');
function Linha({ titulo, texto, children }) {
  return (
    <RotuloCtx.Provider value={titulo}>
    <div className="flex items-center justify-between gap-6 px-4 py-3.5">
      <div><p className="font-medium">{titulo}</p>{texto && <p className="text-sm text-tinta-suave">{texto}</p>}</div>
      <div className="shrink-0">{children}</div>
    </div>
    </RotuloCtx.Provider>
  );
}
function Chave({ ligada, aoMudar }) {
  const rotulo = useContext(RotuloCtx);
  return (
    <button type="button" role="switch" aria-checked={ligada} aria-label={rotulo || undefined} onClick={() => aoMudar(!ligada)}
      className={`relative h-7 w-12 rounded-full transition-colors duration-200 ${ligada ? 'bg-lavanda-solido' : 'bg-tinta/15'}`}>
      <span className={`absolute top-0.5 left-0.5 size-6 rounded-full bg-white shadow transition-transform duration-200 ease-(--ease-padrao) ${ligada ? 'translate-x-5' : ''}`} />
    </button>
  );
}
function Botao({ icone: Icone, children, perigo, ...props }) {
  return (
    <button type="button" {...props}
      className={`flex items-center gap-2 rounded-full px-4 py-2 text-sm font-medium ring-1 transition disabled:opacity-40 ${perigo ? 'text-pendente-forte ring-rose-200 hover:bg-rose-50' : 'bg-white ring-tinta/10 hover:bg-white/70'}`}>
      <Icone size={16} /> {children}
    </button>
  );
}


/* ---------- Backup no Google Drive ---------- */
function BackupNuvem({ c, mudar }) {
  const [n, setN] = useState(null);
  const [ocupado, setOcupado] = useState(false);
  const [aviso, setAviso] = useState(null);
  useEffect(() => { sge.nuvemStatus().then(setN); }, []);
  const rodar = async (fn, ok) => {
    setOcupado(true); setAviso(null);
    const r = await fn();
    if (r?.ok === false) setAviso({ ok: false, texto: r.mensagem });
    else { setAviso(ok ? { ok: true, texto: ok(r) } : null); }
    setN(await sge.nuvemStatus());
    setOcupado(false);
  };
  if (!n) return null;
  const quando = n.ultimoBackup ? new Date(n.ultimoBackup).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : 'ainda não enviado';
  return (
    <Grupo titulo="Backup no Google Drive">
      {!n.configurado ? (
        <Linha titulo="Indisponível nesta versão" texto="Esta instalação não traz a configuração do Google. Atualize o app." />
      ) : !n.conectado ? (
        <Linha titulo="Guarde uma cópia dos seus dados no seu Google Drive" texto="Painéis e históricos ficam numa pasta privada do app, que só ele enxerga. Sua senha do SGE nunca vai para lá. O login é feito no navegador.">
          <Botao icone={Cloud} disabled={ocupado} onClick={() => rodar(() => sge.nuvemEntrar(), () => 'Conectado ao Google Drive.')}>{ocupado ? 'Aguardando o navegador…' : 'Entrar com o Google'}</Botao>
        </Linha>
      ) : (
        <>
          <Linha titulo={`Conectado${n.email ? `: ${n.email}` : ''}`} texto={`Último backup: ${quando}.`}>
            <div className="flex flex-wrap justify-end gap-2">
              <Botao icone={CloudUpload} disabled={ocupado} onClick={() => rodar(() => sge.nuvemEnviar(), () => 'Backup enviado.')}>Enviar agora</Botao>
              <Botao icone={CloudDownload} disabled={ocupado} onClick={() => rodar(() => sge.nuvemRestaurar(), () => 'Dados restaurados e unidos aos deste computador.')}>Restaurar</Botao>
              <Botao icone={LogOut} perigo disabled={ocupado} onClick={() => rodar(() => sge.nuvemSair(), () => 'Desconectado do Google Drive.')}>Sair</Botao>
            </div>
          </Linha>
          <Linha titulo="Enviar automaticamente" texto="Depois de cada leitura do SGE (no máximo a cada 5 minutos).">
            <Chave ligada={c.nuvemAuto} aoMudar={(v) => mudar({ nuvemAuto: v })} />
          </Linha>
        </>
      )}
      {aviso && <p role="status" className={`mx-4 mb-3 rounded-2xl px-4 py-3 text-sm ${aviso.ok ? 'bg-emerald-50 text-emerald-900' : 'bg-rose-50 text-rose-900'}`}>{aviso.texto}</p>}
    </Grupo>
  );
}

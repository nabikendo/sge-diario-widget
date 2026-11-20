import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BarraLateral, BarraSuperior, SECOES } from './components/Layout';
import DetalheTurma from './components/DetalheTurma';
import { Inicio, Turmas, Coordenacao, Historico, Relatorios, Configuracoes } from './paginas/Paginas';
import { sge, modoExemplo } from './lib/sge';

const saudacao = () => { const h = new Date().getHours(); return h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite'; };
const SUBTITULOS = {
  turmas: 'Todas as suas turmas e componentes',
  coordenacao: 'Pendências da escola inteira',
  historico: 'Como as pendências evoluíram',
  relatorios: 'Exporte o Conteúdo Aplicado do bimestre',
  configuracoes: 'Sincronização, janela e dados',
};

export default function App() {
  const [painel, setPainel] = useState(null);
  const [progresso, setProgresso] = useState(null);
  const [sincronizando, setSincronizando] = useState(false);
  const [logado, setLogado] = useState(null);
  const [erro, setErro] = useState(null);
  const [busca, setBusca] = useState('');
  const [secao, setSecao] = useState(() => location.hash.slice(1) || 'home'); // #secao abre direto numa tela
  const [abertaChave, setAbertaChave] = useState(null);
  const [perfilEscola, setPerfilEscola] = useState(false);
  const [atualizacao, setAtualizacao] = useState(null);
  const [, tique] = useState(0);
  const campoBusca = useRef(null);
  const conteudo = useRef(null);

  // Assinaturas do motor (progresso, fim, sessão, erros) + cache salvo.
  useEffect(() => {
    sge.obterPainel().then((p) => p && setPainel(p));
    const desligar = [
      sge.aoProgresso((p) => { setSincronizando(true); setProgresso(p); setErro(null); }),
      sge.aoConcluir((p) => { setPainel(p); setSincronizando(false); setProgresso(null); setLogado(true); }),
      sge.aoSessao(({ logado }) => { setLogado(logado); if (!logado) setSincronizando(false); }),
      sge.aoErro(({ mensagem }) => { setErro(mensagem); setSincronizando(false); }),
      sge.aoPerfil(({ escola }) => setPerfilEscola(escola)),
      sge.aoFimSync(() => { setSincronizando(false); setProgresso(null); setLogado(true); }),
      sge.aoAtualizacao(setAtualizacao),
    ];
    const relogio = setInterval(() => tique((n) => n + 1), 30_000); // atualiza "há X min"
    const atalho = (e) => { if (e.key === '/' && !['INPUT', 'SELECT', 'TEXTAREA'].includes(document.activeElement?.tagName)) { e.preventDefault(); campoBusca.current?.focus(); } };
    window.addEventListener('keydown', atalho);
    return () => { desligar.forEach((f) => f()); clearInterval(relogio); window.removeEventListener('keydown', atalho); };
  }, []);

  // Coordenação não tem diário próprio: ao detectar o perfil Unidade escolar, a tela inicial é a da escola.
  useEffect(() => { if (perfilEscola && secao === 'home') setSecao('coordenacao'); }, [perfilEscola, secao]);

  // Ao trocar de seção, volta ao topo.
  useEffect(() => { conteudo.current?.scrollTo({ top: 0 }); }, [secao]);

  const turmas = useMemo(() => {
    const q = busca.trim().toLowerCase();
    const lista = painel?.turmas ?? [];
    const filtradas = q ? lista.filter((t) => `${t.nome} ${t.disciplina} ${t.escola}`.toLowerCase().includes(q)) : lista;
    const peso = { pendente: 0, erro: 1, 'em-dia': 2 };
    return [...filtradas].sort((a, b) => peso[a.status] - peso[b.status] || (b.pendentes?.length ?? 0) - (a.pendentes?.length ?? 0) || a.nome.localeCompare(b.nome));
  }, [painel, busca]);

  // Buscar leva para Turmas (onde o resultado aparece completo).
  const buscar = (texto) => { setBusca(texto); if (texto && !perfilEscola && !['home', 'turmas'].includes(secao)) setSecao('turmas'); };
  const sincronizar = async () => { setSincronizando(true); setErro(null); const p = await sge.sincronizar(); if (p) setPainel(p); setSincronizando(false); };
  const sair = async () => { if (confirm('Sair do SGE neste computador? Você precisará entrar de novo para sincronizar.')) { await sge.sairDoSge(); setLogado(false); } };
  const fecharDetalhe = useCallback(() => setAbertaChave(null), []);
  const setAberta = (t) => setAbertaChave(t ? `${t.id}/${t.disciplina}` : null);
  // O painel aberto acompanha a sincronização: sempre mostra a leitura mais recente da turma.
  const aberta = abertaChave ? (painel?.turmas ?? []).find((t) => `${t.id}/${t.disciplina}` === abertaChave) ?? null : null;

  const titulo = secao === 'home' && !perfilEscola ? saudacao() : SECOES.find((s) => s.id === secao)?.rotulo ?? 'Configurações';
  const hoje = new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  const subtitulo = secao === 'home' && !perfilEscola ? hoje.charAt(0).toUpperCase() + hoje.slice(1) : SUBTITULOS[secao];

  return (
    <div className="grid h-full grid-cols-[96px_1fr] grid-rows-[auto_1fr] gap-x-2 gap-y-5 p-5 pl-2">
      <div className="row-span-2"><BarraLateral ativa={secao} aoTrocar={setSecao} /></div>

      <BarraSuperior
        titulo={titulo} subtitulo={subtitulo}
        busca={busca} aoBuscar={buscar} refBusca={campoBusca} painel={painel} sincronizando={sincronizando} logado={logado}
        modoEscola={perfilEscola} aoSincronizar={sincronizar} aoEntrar={() => sge.entrar()} aoAbrirSge={() => sge.abrirSge()}
      />

      <main ref={conteudo} className="vidro min-h-0 overflow-y-auto rounded-[28px] p-6">
        {modoExemplo && <p className="mb-4 rounded-2xl bg-amber-100/70 px-4 py-2 text-sm text-amber-900">Modo de exemplo: dados fictícios (aberto fora do Electron).</p>}
        {erro && <p role="alert" className="mb-4 rounded-2xl bg-rose-100/70 px-4 py-2 text-sm text-rose-900">{erro}</p>}
        {atualizacao && (
          <p className="mb-4 flex items-center justify-between gap-4 rounded-2xl bg-lavanda-clara px-4 py-3 text-sm text-tinta">
            {atualizacao.estado === 'pronta' ? `A versão ${atualizacao.versao} está pronta. Ela é instalada ao fechar o app.` : `Baixando a versão ${atualizacao.versao}…`}
            {atualizacao.estado === 'pronta' && <button type="button" onClick={() => sge.instalarAtualizacao()} className="shrink-0 rounded-full bg-lavanda px-4 py-1.5 font-semibold text-white">Reiniciar agora</button>}
          </p>
        )}
        {perfilEscola && secao !== 'coordenacao' && (
          <p className="mb-4 flex items-center justify-between gap-4 rounded-2xl bg-white/70 px-4 py-3 text-sm">
            O perfil ativo no SGE é Unidade escolar (coordenação): o app mostra as pendências da escola e só consulta. Para atualizar o seu diário de professor, troque para o perfil Servidor no SGE.
            <button type="button" onClick={() => setSecao('coordenacao')} className="shrink-0 rounded-full bg-tinta px-4 py-1.5 font-semibold text-white">Ver Coordenação</button>
          </p>
        )}
        {logado === false && (
          <p className="mb-4 flex items-center justify-between gap-4 rounded-2xl bg-white/70 px-4 py-3 text-sm">
            Entre no SGE para atualizar os dados. O app guarda só a sessão do SGE neste computador, nunca a sua senha.
            <button type="button" onClick={() => sge.entrar()} className="shrink-0 rounded-full bg-tinta px-4 py-1.5 font-semibold text-white">Entrar</button>
          </p>
        )}

        <div key={secao} className="surgir">
          {secao === 'home' && <Inicio painel={painel} turmas={turmas} progresso={progresso} sincronizando={sincronizando} aoAbrir={setAberta} />}
          {secao === 'turmas' && <Turmas turmas={turmas} aoAbrir={setAberta} />}
          {secao === 'coordenacao' && <Coordenacao perfilEscola={perfilEscola} sincronizando={sincronizando} />}
          {secao === 'historico' && <Historico />}
          {secao === 'relatorios' && <Relatorios painel={painel} />}
          {secao === 'configuracoes' && <Configuracoes logado={logado} aoSair={sair} />}
        </div>
      </main>

      {aberta && <DetalheTurma turma={aberta} aoFechar={fecharDetalhe} aoAbrirSge={(a) => sge.abrirSge(a)} />}
    </div>
  );
}

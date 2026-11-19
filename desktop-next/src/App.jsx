import { useEffect, useMemo, useRef, useState } from 'react';
import { BarraLateral, BarraSuperior, SECOES } from './components/Layout';
import { CardResumo, CardSincronizacao } from './components/CardsTopo';
import CartaoTurma from './components/CartaoTurma';
import { sge, modoExemplo } from './lib/sge';

const saudacao = () => { const h = new Date().getHours(); return h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite'; };

export default function App() {
  const [painel, setPainel] = useState(null);
  const [progresso, setProgresso] = useState(null);
  const [sincronizando, setSincronizando] = useState(false);
  const [logado, setLogado] = useState(null);
  const [erro, setErro] = useState(null);
  const [busca, setBusca] = useState('');
  const [secao, setSecao] = useState('home');
  const [, tique] = useState(0);
  const campoBusca = useRef(null);

  // Assinaturas do motor (progresso, fim, sessão, erros) + cache salvo.
  useEffect(() => {
    sge.obterPainel().then((p) => p && setPainel(p));
    const desligar = [
      sge.aoProgresso((p) => { setSincronizando(true); setProgresso(p); setErro(null); }),
      sge.aoConcluir((p) => { setPainel(p); setSincronizando(false); setProgresso(null); setLogado(true); }),
      sge.aoSessao(({ logado }) => { setLogado(logado); if (!logado) setSincronizando(false); }),
      sge.aoErro(({ mensagem }) => { setErro(mensagem); setSincronizando(false); }),
    ];
    const relogio = setInterval(() => tique((n) => n + 1), 30_000); // atualiza "há X min"
    const atalho = (e) => { if (e.key === '/' && document.activeElement?.tagName !== 'INPUT') { e.preventDefault(); campoBusca.current?.focus(); } };
    window.addEventListener('keydown', atalho);
    return () => { desligar.forEach((f) => f()); clearInterval(relogio); window.removeEventListener('keydown', atalho); };
  }, []);

  const turmas = useMemo(() => {
    const q = busca.trim().toLowerCase();
    const lista = painel?.turmas ?? [];
    const filtradas = q ? lista.filter((t) => `${t.nome} ${t.disciplina} ${t.escola}`.toLowerCase().includes(q)) : lista;
    // Pendentes primeiro (mais dias antes), depois erros, depois em dia.
    const peso = { pendente: 0, erro: 1, 'em-dia': 2 };
    return [...filtradas].sort((a, b) => peso[a.status] - peso[b.status] || (b.pendentes?.length ?? 0) - (a.pendentes?.length ?? 0) || a.nome.localeCompare(b.nome));
  }, [painel, busca]);

  const sincronizar = async () => { setSincronizando(true); setErro(null); const p = await sge.sincronizar(); if (p) setPainel(p); setSincronizando(false); };
  const titulo = secao === 'home' ? saudacao() : SECOES.find((s) => s.id === secao)?.rotulo ?? 'Configurações';
  const hoje = new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });

  return (
    <div className="grid h-full grid-cols-[96px_1fr] grid-rows-[auto_1fr] gap-x-2 gap-y-5 p-5 pl-2">
      <div className="row-span-2"><BarraLateral ativa={secao} aoTrocar={setSecao} /></div>

      <BarraSuperior
        titulo={titulo} subtitulo={secao === 'home' ? hoje.charAt(0).toUpperCase() + hoje.slice(1) : undefined}
        busca={busca} aoBuscar={setBusca} refBusca={campoBusca} painel={painel} sincronizando={sincronizando} logado={logado}
        aoSincronizar={sincronizar} aoEntrar={() => sge.entrar()}
      />

      <main className="vidro min-h-0 overflow-y-auto rounded-[28px] p-6">
        {modoExemplo && <p className="mb-4 rounded-2xl bg-amber-100/70 px-4 py-2 text-sm text-amber-900">Modo de exemplo: dados fictícios (aberto fora do Electron).</p>}
        {erro && <p role="alert" className="mb-4 rounded-2xl bg-rose-100/70 px-4 py-2 text-sm text-rose-900">{erro}</p>}
        {logado === false && !painel && (
          <p className="mb-4 rounded-2xl bg-white/70 px-4 py-3 text-sm">Entre no SGE uma vez. O app guarda só a sessão do SGE neste computador, nunca a sua senha.</p>
        )}

        <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.25fr_1fr]">
          <CardResumo painel={painel} />
          <CardSincronizacao painel={painel} progresso={progresso} sincronizando={sincronizando} />
        </div>

        <div className="mt-8 mb-3 flex items-baseline justify-between">
          <h2 className="text-lg font-semibold">Suas turmas</h2>
          <span className="text-sm text-tinta-suave">{turmas.length} {turmas.length === 1 ? 'turma' : 'turmas'}{busca && ' encontradas'}</span>
        </div>
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
          {turmas.map((t, i) => <CartaoTurma key={`${t.id}/${t.disciplina}`} turma={t} indice={i} />)}
        </div>
      </main>
    </div>
  );
}

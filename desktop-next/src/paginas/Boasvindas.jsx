import { useEffect, useState } from 'react';
import { ShieldCheck, LogIn, Users, BellRing, RefreshCw, ArrowRight, ArrowLeft, Sparkles, MonitorSmartphone, BookOpenCheck, Loader2 } from 'lucide-react';
import { sge } from '../lib/sge';

/** Marca do app: o mesmo "visto" do ícone, em lavanda. */
function Marca({ tamanho = 72 }) {
  return (
    <span className="inline-flex items-center justify-center rounded-[28%] bg-gradient-to-br from-lavanda to-[#b49cff] text-[#fff] shadow-[0_18px_40px_-14px_rgb(107_84_238/0.65)]" style={{ width: tamanho, height: tamanho }} aria-hidden>
      <BookOpenCheck size={tamanho * 0.5} strokeWidth={1.8} />
    </span>
  );
}

/* Três cartões de estado que se alinham ao entrar. Decorativo: oculto para leitores de tela. */
function CartoesEstado() {
  const reduzir = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const [entrou, setEntrou] = useState(reduzir);
  useEffect(() => { const t = setTimeout(() => setEntrou(true), 60); return () => clearTimeout(t); }, []);
  const cartoes = [
    { titulo: 'Pendências', cor: '#c0392b', detalhe: 'Chamada · 3 dias', pos: { x: 0, y: 26, giro: -6 } },
    { titulo: 'A conferir', cor: '#9a6510', detalhe: 'Planos · leitura parcial', pos: { x: 70, y: 12, giro: -1 } },
    { titulo: 'Em dia', cor: '#1f9d6a', detalhe: 'Conteúdo Aplicado', pos: { x: 140, y: 0, giro: 4 } },
  ];
  return (
    <div aria-hidden="true" className="relative mx-auto h-[150px] w-[320px]">
      {cartoes.map((c) => (
        <div key={c.titulo} className="absolute w-[180px] rounded-2xl border border-[#e4e0f2] bg-white px-3.5 py-3 text-left text-[#14121c] shadow-sm transition-all duration-700 ease-[cubic-bezier(0.05,0.7,0.1,1)]"
          style={{ left: entrou ? c.pos.x : 68, top: entrou ? c.pos.y : 30, opacity: entrou ? 1 : 0.4, transform: `rotate(${entrou ? c.pos.giro : 0}deg)` }}>
          <p className="text-xs font-medium" style={{ color: c.cor }}>{c.titulo}</p>
          <p className="mt-0.5 text-sm">{c.detalhe}</p>
        </div>
      ))}
    </div>
  );
}

/* ---------- Tela inicial enquanto o perfil ainda não é conhecido ---------- */
export function Boasvindas({ logado, progresso, aoEntrar, aoVerTutorial }) {
  const reconhecendo = logado === true;
  return (
    <main className="flex h-full items-center justify-center p-8">
      {/* Cartão com cores fixas: o título não depende do tema escuro para ficar legível. */}
      <div className="surgir w-[min(560px,100%)] rounded-[32px] border border-[#e4e0f2] bg-white p-10 text-center text-[#14121c]">
        <CartoesEstado />
        <h1 className="mt-6 text-3xl font-semibold tracking-tight text-[#14121c]">SGE Diário</h1>
        <p className="mx-auto mt-2 max-w-sm text-[#5f5a78]">As pendências do SGE de relance, no seu computador. Professor ou coordenação: o app se ajusta ao seu perfil.</p>

        {reconhecendo ? (
          <div className="mt-8" role="status">
            <Loader2 size={28} className="mx-auto animate-spin text-lavanda" aria-hidden />
            <p className="mt-3 font-medium text-[#14121c]">Reconhecendo o seu perfil no SGE…</p>
            <p className="text-sm text-[#5f5a78]">{progresso?.etapa ?? 'Isso leva só um instante.'}{progresso?.total ? ` · ${progresso.feitas} de ${progresso.total}` : ''}</p>
          </div>
        ) : (
          <>
            <button type="button" onClick={aoEntrar} className="mx-auto mt-8 flex h-13 items-center gap-2.5 rounded-full bg-lavanda-solido px-8 text-base font-semibold text-[#fff] shadow-[0_12px_28px_-10px_rgb(107_84_238/0.75)] transition hover:brightness-110 active:scale-[0.98]">
              <LogIn size={20} aria-hidden /> Entrar no SGE
            </button>
            <ul className="mx-auto mt-6 max-w-sm space-y-2 text-left text-sm text-[#5f5a78]">
              <li className="flex gap-2.5"><ShieldCheck size={18} className="mt-0.5 shrink-0 text-emdia" aria-hidden /> Você digita a senha na própria página do SGE. O app nunca vê nem guarda a senha.</li>
              <li className="flex gap-2.5"><ShieldCheck size={18} className="mt-0.5 shrink-0 text-emdia" aria-hidden /> Somente leitura: o app não grava nada no SGE.</li>
            </ul>
          </>
        )}
        <button type="button" onClick={aoVerTutorial} className="mt-7 text-sm font-medium text-[#6049e0] hover:underline">Ver o tutorial</button>
      </div>
    </main>
  );
}

/* ---------- Tutorial de introdução ---------- */
const PASSOS = [
  { icone: Sparkles, titulo: 'Bem-vindo ao SGE Diário', texto: 'Em poucos passos você vê o que falta no diário, sem abrir página por página do SGE.',
    itens: ['Conteúdo Aplicado, Chamada, Planos e PEI em um só painel.', 'Funciona como professor ou como coordenação.', 'Tudo fica neste computador.'] },
  { icone: ShieldCheck, titulo: 'Entre uma vez, com segurança', texto: 'Ao clicar em Entrar, abre a página do SGE para você fazer o login normalmente.',
    itens: ['A senha é digitada só no SGE; o app não a vê nem a guarda.', 'O app guarda apenas a sessão e só lê dados: nunca grava nada no SGE.', 'Para sair a qualquer momento: Configurações → Sair do SGE.'] },
  { icone: Users, titulo: 'Professor ou coordenação, automático', texto: 'O app reconhece o perfil ativo no SGE e muda as abas sozinho.',
    itens: ['Servidor: Home, Semana, Turmas, Histórico e Relatórios.', 'Unidade escolar: Visão geral, Professores, Turmas e Componentes, por bimestre ou ano letivo.', 'Trocou de perfil no SGE? O app acompanha na próxima sincronização.'] },
  { icone: MonitorSmartphone, titulo: 'Abra o SGE na tela certa', texto: 'Para o professor, cada pendência leva direto ao lugar de resolver.',
    itens: ['Fazer diário abre o SGE dentro do app, já logado.', 'Em cada turma, Abrir no SGE seleciona turma, componente e o dia pendente.', 'Quem coordena consulta e cobra; o diário é do professor.'] },
  { icone: BellRing, titulo: 'Avisos que ajudam', texto: 'O app trabalha em segundo plano e avisa só quando importa.',
    itens: ['Notificação quando aparece pendência nova.', 'Resumo no fim da tarde, em dias úteis (o horário é seu).', 'O ícone ao lado do relógio mostra o número de pendências.'] },
  { icone: RefreshCw, titulo: 'Sempre em dia', texto: 'Nada para atualizar à mão.',
    itens: ['O app se atualiza sozinho quando sai versão nova.', 'Tema claro, escuro ou o do sistema, em Configurações.', 'Coordenação: mensagens de cobrança prontas para o WhatsApp, PDF e planilha.'] },
];

export function Tutorial({ aoConcluir }) {
  const [i, setI] = useState(0);
  const p = PASSOS[i]; const Icone = p.icone; const ultimo = i === PASSOS.length - 1;
  useEffect(() => {
    const tecla = (e) => { if (e.key === 'ArrowRight' && !ultimo) setI((n) => n + 1); if (e.key === 'ArrowLeft' && i > 0) setI((n) => n - 1); if (e.key === 'Escape') aoConcluir(); };
    window.addEventListener('keydown', tecla);
    return () => window.removeEventListener('keydown', tecla);
  }, [i, ultimo, aoConcluir]);
  return (
    <main className="flex h-full items-center justify-center p-8">
      <div className="vidro-forte w-[min(640px,100%)] rounded-[32px] p-10">
        <div className="flex items-center justify-between">
          <Marca tamanho={40} />
          <button type="button" onClick={aoConcluir} className="text-sm font-medium text-tinta-suave hover:text-tinta">Pular</button>
        </div>
        <div key={i} className="surgir mt-8">
          <span className="inline-flex rounded-2xl bg-lavanda-clara p-3.5 text-lavanda-texto"><Icone size={30} strokeWidth={1.8} aria-hidden /></span>
          <h1 className="mt-5 text-[28px] leading-tight font-semibold tracking-tight">{p.titulo}</h1>
          <p className="mt-2 text-tinta-suave">{p.texto}</p>
          <ul className="mt-5 space-y-2.5">
            {p.itens.map((t) => <li key={t} className="flex gap-3 rounded-2xl bg-white/60 px-4 py-3 text-[15px]"><span className="mt-2 size-1.5 shrink-0 rounded-full bg-lavanda-solido" aria-hidden />{t}</li>)}
          </ul>
        </div>
        <div className="mt-8 flex items-center justify-between">
          <div className="flex gap-1.5" role="tablist" aria-label="Passos do tutorial">
            {PASSOS.map((_, n) => <button key={n} type="button" role="tab" aria-selected={n === i} aria-label={`Passo ${n + 1}`} onClick={() => setI(n)} className={`h-2 rounded-full transition-all duration-300 ${n === i ? 'w-7 bg-lavanda-solido' : 'w-2 bg-tinta/20 hover:bg-tinta/35'}`} />)}
          </div>
          <div className="flex gap-2">
            {i > 0 && <button type="button" onClick={() => setI(i - 1)} className="flex h-11 items-center gap-1.5 rounded-full bg-white/70 px-5 text-sm font-semibold ring-1 ring-tinta/10 hover:bg-white"><ArrowLeft size={16} aria-hidden /> Voltar</button>}
            <button type="button" onClick={() => (ultimo ? aoConcluir() : setI(i + 1))} className="flex h-11 items-center gap-1.5 rounded-full bg-lavanda-solido px-6 text-sm font-semibold text-[#fff] shadow-[0_10px_24px_-10px_rgb(107_84_238/0.75)] hover:brightness-110">
              {ultimo ? 'Começar' : 'Próximo'} {!ultimo && <ArrowRight size={16} aria-hidden />}
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}

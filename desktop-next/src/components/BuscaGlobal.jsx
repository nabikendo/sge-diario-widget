import { useEffect, useMemo, useRef, useState } from 'react';
import { Search, CornerDownLeft } from 'lucide-react';

const norm = (t) => String(t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();

/** Paleta de busca (Ctrl+K): acha professor, turma e componente de qualquer tela e leva até lá. */
export default function BuscaGlobal({ itens, aoEscolher, aoFechar }) {
  const [q, setQ] = useState('');
  const [i, setI] = useState(0);
  const campo = useRef(null);
  useEffect(() => campo.current?.focus(), []);
  const achados = useMemo(() => {
    const t = norm(q);
    const lista = t ? itens.filter((x) => norm(`${x.rotulo} ${x.sub ?? ''}`).includes(t)) : itens;
    return lista.slice(0, 40);
  }, [q, itens]);
  useEffect(() => setI(0), [q]);
  const tecla = (e) => {
    if (e.key === 'Escape') aoFechar();
    else if (e.key === 'ArrowDown') { e.preventDefault(); setI((n) => Math.min(n + 1, achados.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setI((n) => Math.max(n - 1, 0)); }
    else if (e.key === 'Enter' && achados[i]) { aoEscolher(achados[i]); aoFechar(); }
  };
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/30 px-6 pt-[12vh] backdrop-blur-sm" onMouseDown={(e) => e.target === e.currentTarget && aoFechar()} role="dialog" aria-modal="true" aria-label="Busca">
      <div className="vidro-forte w-[min(620px,100%)] overflow-hidden rounded-3xl shadow-2xl">
        <label className="flex items-center gap-3 border-b border-tinta/10 px-5 py-4">
          <Search size={20} className="text-tinta-suave" aria-hidden />
          <input ref={campo} aria-label="Buscar" role="combobox" aria-expanded="true" aria-controls="resultados-busca" aria-activedescendant={achados[i] ? `op-${i}` : undefined} value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={tecla} placeholder="Buscar professor, turma ou componente…"
            className="min-w-0 flex-1 bg-transparent text-lg outline-none placeholder:text-tinta-suave" />
          <kbd className="rounded-md bg-tinta/10 px-2 py-0.5 text-xs text-tinta-suave">Esc</kbd>
        </label>
        <ul className="max-h-[50vh] overflow-y-auto p-2" role="listbox" id="resultados-busca" aria-label="Resultados">
          {achados.length === 0 && <li className="px-4 py-6 text-center text-tinta-suave">Nada encontrado.</li>}
          {achados.map((x, n) => (
            <li key={`${x.tipo}|${x.chave}`} role="option" id={`op-${n}`} aria-selected={n === i} onMouseEnter={() => setI(n)} onClick={() => { aoEscolher(x); aoFechar(); }}
              className={`flex w-full cursor-pointer items-center gap-3 rounded-2xl px-4 py-2.5 text-left ${n === i ? 'bg-lavanda-clara' : ''}`}>
              <>
                <span className="w-24 shrink-0 text-xs font-semibold tracking-wide text-lavanda-texto uppercase">{x.tipoRotulo}</span>
                <span className="min-w-0 flex-1"><span className="block truncate font-medium">{x.rotulo}</span>{x.sub && <span className="block truncate text-xs text-tinta-suave">{x.sub}</span>}</span>
                {n === i && <CornerDownLeft size={15} className="text-tinta-suave" aria-hidden />}
              </>
            </li>
          ))}
        </ul>
        <p className="border-t border-tinta/10 px-5 py-2.5 text-xs text-tinta-suave">↑ ↓ para escolher · Enter para abrir · busca por nome de aluno não está disponível</p>
      </div>
    </div>
  );
}

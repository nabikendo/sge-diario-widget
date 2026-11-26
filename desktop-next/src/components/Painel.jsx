import { useEffect, useRef } from 'react';
import * as Dialog from '@radix-ui/react-dialog';

/**
 * Janela sobre o conteúdo (painel lateral ou caixa central) com o comportamento do Radix:
 * o foco fica preso dentro, Esc e clique fora fecham, e o foco volta ao botão que abriu.
 * O visual é o mesmo de antes; quem usa só passa o conteúdo.
 */
export default function Painel({ rotulo, lado = 'direita', aoFechar, className = '', children }) {
  const lateral = lado === 'direita';
  // Quem usa o Painel o desmonta ao fechar (não passa open=false), e aí o Radix não devolve o foco.
  // Guarda o elemento que tinha o foco ao abrir e volta para ele quando o painel sai da tela.
  const quemAbriu = useRef(typeof document !== 'undefined' ? document.activeElement : null);
  useEffect(() => () => {
    const el = quemAbriu.current;
    setTimeout(() => { if (el?.isConnected && typeof el.focus === 'function') el.focus(); }, 0);
  }, []);
  return (
    <Dialog.Root open onOpenChange={(aberto) => { if (!aberto) aoFechar(); }}>
      <Dialog.Portal>
        <Dialog.Overlay
          className={`fixed inset-0 z-40 flex bg-tinta/15 backdrop-blur-[2px] ${lateral ? 'justify-end' : 'items-center justify-center bg-tinta/20 p-6'}`}
        >
          <Dialog.Content aria-describedby={undefined} style={{ outline: 'none' }} className={`${lateral ? 'deslizar m-3' : 'surgir max-h-full'} vidro-forte flex flex-col rounded-[28px] p-6 shadow-2xl ${className}`}>
            <Dialog.Title className="sr-only">{rotulo}</Dialog.Title>
            {children}
          </Dialog.Content>
        </Dialog.Overlay>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

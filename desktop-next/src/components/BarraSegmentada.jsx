/**
 * Barra de progresso em segmentos (mesma linguagem do app Android 2.9.2).
 * progresso: 0–1, ou null para "carregando" (onda que percorre a barra).
 */
export default function BarraSegmentada({ progresso, segmentos = 28, altura = 12, cor = 'var(--color-lavanda)', trilho = 'rgb(107 84 238 / 0.16)', rotulo }) {
  const alvo = progresso == null ? null : Math.min(1, Math.max(0, progresso));
  return (
    <div
      role="progressbar"
      aria-label={rotulo}
      aria-valuemin={0} aria-valuemax={100}
      aria-valuenow={alvo == null ? undefined : Math.round(alvo * 100)}
      className="flex w-full gap-[3px]"
      style={{ height: altura }}
    >
      {Array.from({ length: segmentos }, (_, i) => {
        const aceso = alvo == null ? 0 : Math.min(1, Math.max(0, alvo * segmentos - i));
        return (
          <span
            key={i}
            className={'flex-1 rounded-full transition-[background-color] duration-300 ease-(--ease-padrao) ' + (alvo == null ? 'animate-pulse' : '')}
            style={{
              background: alvo == null ? cor : aceso >= 1 ? cor : aceso > 0 ? `color-mix(in oklab, ${cor} ${Math.round(aceso * 100)}%, ${trilho})` : trilho,
              opacity: alvo == null ? 0.25 + 0.75 * Math.abs(Math.sin((i / segmentos) * Math.PI)) : 1,
              animationDelay: alvo == null ? `${i * 45}ms` : undefined,
              transitionDelay: alvo == null ? undefined : `${i * 12}ms`,
            }}
          />
        );
      })}
    </div>
  );
}

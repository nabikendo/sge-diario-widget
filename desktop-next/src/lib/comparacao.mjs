/** Comparação com a semana passada, a partir dos históricos que o app já guarda. */
const DIA = 86_400_000;

/** Registro de referência: o mais recente com pelo menos 5 dias (ideal: ~7). Sem ele, não há comparação. */
export function referenciaSemanal(lista, agora = Date.now()) {
  const antigos = (lista ?? []).filter((h) => agora - Date.parse(h.em) >= 5 * DIA);
  return antigos.length ? antigos[antigos.length - 1] : null;
}

/** Diferença por chave (turma ou professor). Chave que sumiu = 0 agora; chave nova = 0 antes. */
export function diferencas(atual, antigo) {
  const chaves = new Set([...Object.keys(atual ?? {}), ...Object.keys(antigo ?? {})]);
  const itens = [...chaves].map((chave) => {
    const agora = atual?.[chave] ?? 0; const antes = antigo?.[chave] ?? 0;
    return { chave, antes, agora, delta: agora - antes };
  });
  return {
    melhoraram: itens.filter((i) => i.delta < 0).sort((a, b) => a.delta - b.delta),
    pioraram: itens.filter((i) => i.delta > 0).sort((a, b) => b.delta - a.delta),
    iguais: itens.filter((i) => i.delta === 0 && i.agora > 0),
    totalAntes: itens.reduce((s, i) => s + i.antes, 0), totalAgora: itens.reduce((s, i) => s + i.agora, 0),
  };
}

import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis } from 'recharts';

/**
 * Gráfico de barras por dia (Recharts). A última barra é a mais recente e fica em destaque.
 * dados: [{ rotulo: '24/11', valor: 12, titulo?: '24/11: 12 dias' }]
 * O gráfico em si é só visual: o texto de `descricao` e a lista escondida são o que o leitor de tela lê.
 */
export default function GraficoBarras({ dados, descricao, unidade = '' }) {
  return (
    <div className="mt-6 h-48" role="img" aria-label={`${descricao}. ${dados.length} dias.`}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={dados} margin={{ top: 18, right: 4, bottom: 0, left: 4 }} barCategoryGap="14%">
          <XAxis dataKey="rotulo" tickLine={false} axisLine={false} interval="preserveStartEnd" tick={{ fontSize: 10, fill: 'currentColor' }} className="text-tinta-suave" />
          <Tooltip
            cursor={{ fill: 'rgb(107 84 238 / 0.06)' }}
            formatter={(v) => [`${v}${unidade}`, '']}
            labelFormatter={(_, itens) => itens?.[0]?.payload?.titulo ?? ''}
            separator=""
            contentStyle={{ borderRadius: 12, border: 'none', boxShadow: '0 4px 20px rgb(0 0 0 / 0.12)', fontSize: 12 }}
          />
          <Bar dataKey="valor" radius={[10, 10, 4, 4]} minPointSize={6} isAnimationActive={false} label={{ position: 'top', fontSize: 11, fill: 'currentColor', className: 'text-tinta-suave' }}>
            {dados.map((d, i) => <Cell key={d.rotulo + i} style={{ fill: 'var(--color-lavanda-solido)', fillOpacity: i === dados.length - 1 ? 1 : 0.35 }} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <ul className="sr-only">{dados.map((d) => <li key={d.rotulo}>{d.titulo ?? `${d.rotulo}: ${d.valor}`}</li>)}</ul>
    </div>
  );
}

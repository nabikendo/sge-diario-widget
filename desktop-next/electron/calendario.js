'use strict';
/**
 * Dias sem aula: feriados nacionais, de Tocantins e móveis (Carnaval, Sexta-feira Santa, Corpus Christi),
 * mais datas que a pessoa informa nas Configurações. "Dias letivos extras" (sábado letivo, feriado trabalhado)
 * anulam qualquer bloqueio naquela data. Sem Electron: usado pelo motor e pelos testes.
 */

const pad = (n) => String(n).padStart(2, '0');
const iso = (d) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
const somar = (d, dias) => new Date(d.getTime() + dias * 86400000);

/** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher), em UTC. */
function pascoa(ano) {
  const a = ano % 19, b = Math.floor(ano / 100), c = ano % 100, d = Math.floor(b / 4), e = b % 4;
  const f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4), k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
  const mes = Math.floor((h + l - 7 * m + 114) / 31), dia = ((h + l - 7 * m + 114) % 31) + 1;
  return new Date(Date.UTC(ano, mes - 1, dia));
}

const FIXOS = [
  ['01-01', 'Confraternização Universal'], ['03-18', 'Autonomia do Tocantins'], ['04-21', 'Tiradentes'], ['05-01', 'Dia do Trabalho'],
  ['09-07', 'Independência do Brasil'], ['09-08', 'Nossa Senhora da Natureza'], ['10-05', 'Criação do Estado do Tocantins'],
  ['10-12', 'Nossa Senhora Aparecida'], ['11-02', 'Finados'], ['11-15', 'Proclamação da República'],
  ['11-20', 'Consciência Negra'], ['12-25', 'Natal'],
];

const cache = new Map();
/** Feriados do ano: Map "AAAA-MM-DD" → nome. */
function feriadosDoAno(ano) {
  if (cache.has(ano)) return cache.get(ano);
  const mapa = new Map(FIXOS.map(([md, nome]) => [`${ano}-${md}`, nome]));
  const p = pascoa(ano);
  mapa.set(iso(somar(p, -48)), 'Carnaval (segunda)');
  mapa.set(iso(somar(p, -47)), 'Carnaval (terça)');
  mapa.set(iso(somar(p, -2)), 'Sexta-feira Santa');
  mapa.set(iso(somar(p, 60)), 'Corpus Christi');
  cache.set(ano, mapa);
  return mapa;
}

/** Texto das Configurações → Map. Uma data por linha: "2026-11-27 Recesso" ou "27/11/2026 Recesso". */
function lerLista(texto) {
  const mapa = new Map();
  for (const linha of String(texto ?? '').split(/\r?\n/)) {
    const t = linha.trim();
    let m = t.match(/^(\d{4})-(\d{2})-(\d{2})\s*(.*)$/);
    if (m) { mapa.set(`${m[1]}-${m[2]}-${m[3]}`, m[4].trim()); continue; }
    m = t.match(/^(\d{2})\/(\d{2})\/(\d{4})\s*(.*)$/);
    if (m) mapa.set(`${m[3]}-${m[2]}-${m[1]}`, m[4].trim());
  }
  return mapa;
}

/** Motivo de o dia não ter aula, ou null se for dia letivo. `config`: { diasSemAula, diasLetivos } (textos). */
function motivoSemAula(dataIso, config = {}) {
  if (lerLista(config.diasLetivos).has(dataIso)) return null;
  const propria = lerLista(config.diasSemAula).get(dataIso);
  if (propria !== undefined) return propria || 'Dia sem aula';
  const feriado = feriadosDoAno(Number(dataIso.slice(0, 4))).get(dataIso);
  if (feriado) return feriado;
  const dow = new Date(dataIso + 'T12:00:00Z').getUTCDay();
  return dow === 0 ? 'Domingo' : dow === 6 ? 'Sábado' : null;
}

/** Tira de uma lista de datas as que não são dia letivo. */
const soLetivas = (datas = [], config) => datas.filter((d) => !motivoSemAula(d, config));

module.exports = { feriadosDoAno, motivoSemAula, soLetivas, lerLista, pascoa };

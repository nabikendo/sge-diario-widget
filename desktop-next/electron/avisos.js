'use strict';
/** Regras dos avisos (sem Electron): o que é pendência nova, quando avisar no fim do dia e o texto das notificações. */

const brData = (iso) => iso.slice(8, 10) + '/' + iso.slice(5, 7);
const ROTULO = { conteudo: 'Conteúdo', frequencia: 'Chamada', planoAula: 'Plano de aula', planoCurso: 'Plano de curso', pei: 'PEI' };

/**
 * Pendências que existem no painel novo e não existiam no antigo.
 * Sem painel antigo (primeira leitura) não há "novidade": devolve lista vazia, para não avisar tudo de uma vez.
 */
function novasPendencias(antigo, novo) {
  if (!antigo?.turmas || !novo?.turmas) return [];
  const antes = new Map(antigo.turmas.map((t) => [`${t.id}/${t.disciplina}`, t]));
  const achadas = [];
  for (const t of novo.turmas) {
    const a = antes.get(`${t.id}/${t.disciplina}`);
    if (!a || t.status === 'erro') continue; // turma nova ou não lida: nada a comparar
    const m = t.modulos ?? {}; const am = a.modulos ?? {};
    for (const chave of ['conteudo', 'frequencia']) {
      const atuais = (chave === 'conteudo' ? m.conteudo?.pendentes : m.frequencia?.datas) ?? [];
      const velhas = new Set((chave === 'conteudo' ? am.conteudo?.pendentes ?? a.pendentes : am.frequencia?.datas) ?? []);
      if (chave === 'frequencia' && a.modulos?.frequencia?.erro) continue;
      const novas = atuais.filter((d) => !velhas.has(d));
      if (novas.length) achadas.push({ turma: t.nome, disciplina: t.disciplina, modulo: chave, quantidade: novas.length, datas: novas });
    }
    for (const chave of ['planoAula', 'planoCurso', 'pei']) {
      const agora = t.contagens?.[chave]; const antes2 = a.contagens?.[chave];
      if (agora != null && antes2 != null && agora > antes2) achadas.push({ turma: t.nome, disciplina: t.disciplina, modulo: chave, quantidade: agora - antes2, datas: [] });
    }
  }
  return achadas;
}

/** Texto da notificação de pendências novas. */
function textoNovas(achadas) {
  const n = achadas.reduce((s, a) => s + a.quantidade, 0);
  const titulo = n === 1 ? 'Nova pendência no SGE' : `${n} novas pendências no SGE`;
  const linhas = achadas.slice(0, 3).map((a) => `${a.turma} ${a.disciplina}: ${ROTULO[a.modulo]}${a.datas.length ? ` ${a.datas.slice(0, 2).map(brData).join(', ')}` : ''}`);
  if (achadas.length > 3) linhas.push(`e mais ${achadas.length - 3}`);
  return { titulo, corpo: linhas.join('\n') };
}

/** Total de pendências do professor (soma de todos os módulos lidos). */
const totalPainel = (painel) => (painel?.turmas ?? []).reduce((s, t) => s + (t.totalPendencias ?? t.pendentes?.length ?? 0), 0);

/** Aviso do fim da tarde: dia útil, depois da hora combinada, uma vez por dia. */
function deveAvisarFimDia(agora, horaAviso, ultimoDia) {
  const dow = agora.getDay();
  if (dow === 0 || dow === 6) return false;
  const [h, m] = /^\d{2}:\d{2}$/.test(horaAviso) ? horaAviso.split(':').map(Number) : [17, 30];
  const passou = agora.getHours() * 60 + agora.getMinutes() >= h * 60 + m;
  const hoje = `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, '0')}-${String(agora.getDate()).padStart(2, '0')}`;
  return passou && ultimoDia !== hoje;
}

function textoFimDia(painel) {
  const turmas = (painel?.turmas ?? []).filter((t) => (t.totalPendencias ?? 0) > 0);
  const n = totalPainel(painel);
  if (!n) return { titulo: 'Diário em dia', corpo: 'Nenhuma pendência no SGE. Bom descanso!' };
  return { titulo: `${n} ${n === 1 ? 'pendência' : 'pendências'} no seu diário`, corpo: `Em ${turmas.length} ${turmas.length === 1 ? 'turma' : 'turmas'}: ${turmas.slice(0, 3).map((t) => t.nome).join(', ')}${turmas.length > 3 ? '…' : ''}. Dá tempo de registrar antes de ir embora.` };
}

/**
 * Aulas que o professor deu hoje (horário do SGE) cruzadas com o painel: quantas ficaram sem conteúdo e sem chamada.
 * Devolve null se não houver aulas hoje.
 */
function resumoDoDia(aulasHoje, painel, hojeIso) {
  const aulas = aulasHoje ?? [];
  if (!aulas.length) return null;
  const turmas = painel?.turmas ?? [];
  let semConteudo = 0, semChamada = 0;
  const vistas = new Set();
  for (const a of aulas) {
    const t = turmas.find((x) => x.nome === a.turma && x.disciplina === a.disciplina);
    const chave = `${a.turma}|${a.disciplina}`;
    if (!t || vistas.has(chave)) continue; // duas aulas seguidas da mesma turma contam uma vez no registro
    vistas.add(chave);
    if (t.pendentes?.includes(hojeIso)) semConteudo++;
    if (t.modulos?.frequencia?.datas?.includes(hojeIso)) semChamada++;
  }
  return { aulas: aulas.length, turmas: vistas.size, semConteudo, semChamada };
}

function textoResumoDia(r, painel) {
  const aulas = `${r.aulas} ${r.aulas === 1 ? 'aula' : 'aulas'}`;
  const falta = [];
  if (r.semConteudo) falta.push(`o conteúdo de ${r.semConteudo}`);
  if (r.semChamada) falta.push(`a chamada de ${r.semChamada}`);
  const outras = totalPainel(painel);
  if (!falta.length) return { titulo: 'Aulas de hoje em dia', corpo: `Você deu ${aulas} hoje e o registro de hoje está completo.${outras ? ` Ainda há ${outras} ${outras === 1 ? 'pendência' : 'pendências'} de outros dias.` : ''}` };
  return { titulo: 'Falta registrar o dia', corpo: `Você deu ${aulas} hoje e falta registrar ${falta.join(' e ')}.${outras ? ` No total: ${outras} ${outras === 1 ? 'pendência' : 'pendências'}.` : ''}` };
}

module.exports = { resumoDoDia, textoResumoDia, novasPendencias, textoNovas, totalPainel, deveAvisarFimDia, textoFimDia };

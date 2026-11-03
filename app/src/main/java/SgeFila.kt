package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/** Fila persistida da verificação da coordenação: permite estimar o tempo e pausar/retomar. */
data class FilaCoord(val ids: List<String>, val total: Int, val pausada: Boolean, val puladas: Int, val mediaMs: Long,
    val concluidas: List<String> = emptyList(), val falhas: List<String> = emptyList()) {
    val resolvidas get() = (puladas + concluidas.size + falhas.size).coerceAtMost(total)
    val progresso get() = if (total <= 0) 0f else (resolvidas.toFloat() / total).coerceIn(0f, 1f)
}

data class PlanoFila(val ordem: List<String>, val puladas: Int)

object SgeFila {
    const val RECENTE_MS = 3L * 3600 * 1000
    const val MEDIA_PADRAO_MS = 90_000L
    private const val KEY = "fila_coord_v1"
    private const val KEY_MEDIA = "fila_media_ms"

    /** 0 = tem pendência confirmada; 1 = nunca verificada, com falha ou cobertura parcial; 2 = sem pendências conhecidas. */
    fun prioridade(t: CoordTurma?): Int = when {
        t == null || t.disciplinas.isEmpty() || t.erro != null -> 1
        t.disciplinas.any { r -> r.modulos.any { (it.quantidade ?: 0) > 0 } } -> 0
        t.disciplinas.any { r -> r.modulos.any { it.quantidade == null || (!it.coberturaCompleta && !it.naoConfigurado) } } -> 1
        else -> 2
    }
    fun recente(t: CoordTurma?, agora: Long) = t != null && t.erro == null && t.atualizadoMs > 0 && agora - t.atualizadoMs < RECENTE_MS

    /** Ordena pendentes primeiro e, se [pular], tira as verificadas há poucas horas. A ordem original é mantida dentro de cada grupo. */
    fun planejar(ids: List<String>, turmas: List<CoordTurma>, agora: Long, pular: Boolean): PlanoFila {
        val porId = turmas.associateBy { it.id }
        val unicos = ids.distinct()
        val manter = if (pular) unicos.filterNot { recente(porId[it], agora) } else unicos
        return PlanoFila(manter.sortedBy { prioridade(porId[it]) }, unicos.size - manter.size)
    }

    fun estimativaMin(restantes: Int, mediaMs: Long): Int = ceil(restantes * mediaMs / 60_000.0).toInt().coerceAtLeast(1)
    fun textoEstimativa(restantes: Int, mediaMs: Long): String {
        val m = estimativaMin(restantes, mediaMs)
        return if (m < 2) "cerca de 1 min" else "cerca de $m min"
    }
    /** Média móvel simples da duração por turma, para a próxima estimativa. */
    fun novaMedia(anterior: Long, duracao: Long): Long = if (duracao <= 0) anterior else ((anterior * 2 + duracao) / 3)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
    fun media(ctx: Context): Long = prefs(ctx).getLong(KEY_MEDIA, MEDIA_PADRAO_MS).coerceIn(10_000L, 600_000L)
    fun ler(ctx: Context): FilaCoord? = runCatching {
        val j = JSONObject(prefs(ctx).getString(KEY, null) ?: return null)
        val a = j.getJSONArray("ids")
        val c = j.optJSONArray("concluidas") ?: JSONArray(); val f = j.optJSONArray("falhas") ?: JSONArray()
        FilaCoord((0 until a.length()).map { a.getString(it) }, j.optInt("total"), j.optBoolean("pausada"), j.optInt("puladas"), media(ctx),
            (0 until c.length()).map { c.getString(it) }, (0 until f.length()).map { f.getString(it) })
    }.getOrNull()
    fun salvar(ctx: Context, f: FilaCoord?) {
        val e = prefs(ctx).edit()
        if (f == null || (f.ids.isEmpty() && f.falhas.isEmpty())) e.remove(KEY) else e.putString(KEY, JSONObject().put("ids", JSONArray(f.ids)).put("total", f.total)
            .put("pausada", f.pausada).put("puladas", f.puladas).put("concluidas", JSONArray(f.concluidas)).put("falhas", JSONArray(f.falhas)).toString())
        e.apply()
    }
    fun pausar(ctx: Context) { ler(ctx)?.let { salvar(ctx, it.copy(pausada = true)) } }
    fun concluir(ctx: Context, id: String, duracaoMs: Long = 0L): FilaCoord? {
        if (duracaoMs > 0) prefs(ctx).edit().putLong(KEY_MEDIA, novaMedia(media(ctx), duracaoMs)).apply()
        val f = ler(ctx) ?: return null
        val novo = f.copy(ids = f.ids - id, concluidas = (f.concluidas + id).distinct(), falhas = f.falhas - id)
        salvar(ctx, novo)
        return novo.takeIf { it.ids.isNotEmpty() || it.falhas.isNotEmpty() }
    }
    fun falhar(ctx: Context, id: String): FilaCoord? {
        val f = ler(ctx) ?: return null
        val novo = f.copy(ids = f.ids - id, falhas = (f.falhas + id).distinct(), concluidas = f.concluidas - id)
        salvar(ctx, novo)
        return novo.takeIf { it.ids.isNotEmpty() || it.falhas.isNotEmpty() }
    }
    fun textoPausada(f: FilaCoord) = "Verificação pausada: ${f.resolvidas}/${f.total} resolvidas, ${f.ids.size} turma(s) por fazer e ${f.falhas.size} falha(s). Toque em Retomar para continuar."
    fun textoAndamento(f: FilaCoord, atual: String) = "$atual · ${f.resolvidas}/${f.total} resolvidas · falhas: ${f.falhas.size} · restam ${f.ids.size}, ${textoEstimativa(f.ids.size, f.mediaMs)}."
}

/** Planos de aula, de curso e PEI mudam pouco: reconsulta semanal. Só resultados completos e sem pendência são reaproveitados. */
object SgeCachePlanos {
    const val VALIDADE_MS = 7L * 24 * 3600 * 1000
    private const val KEY = "cache_planos_v1"
    private val sufixo = Regex(" \\(reaproveitado da consulta de [^)]*\\)$")

    fun reaproveitavel(anterior: ResultadoModulo?, ts: Long, agora: Long): Boolean {
        if (anterior == null || ts <= 0 || agora - ts !in 0 until VALIDADE_MS) return false
        return anterior.naoConfigurado || (anterior.quantidade == 0 && anterior.coberturaCompleta)
    }
    fun anotar(anterior: ResultadoModulo, ts: Long): ResultadoModulo {
        val d = java.time.Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
        val base = anterior.detalhe.replace(sufixo, "")
        return anterior.copy(detalhe = "$base (reaproveitado da consulta de ${"%02d/%02d".format(d.dayOfMonth, d.monthValue)})")
    }
    private fun mapa(ctx: Context): JSONObject = runCatching { JSONObject(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, "{}")) }.getOrDefault(JSONObject())
    fun ts(ctx: Context, chave: String): Long = mapa(ctx).optLong(chave, 0L)
    fun limpar(ctx: Context) { ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY).apply() }
    fun marcar(ctx: Context, chave: String, agora: Long) {
        val m = mapa(ctx).put(chave, agora)
        ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, m.toString()).apply()
    }
}

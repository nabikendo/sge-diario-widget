package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Uma medição contém somente contagens, nunca textos ou nomes de alunos. */
data class MedicaoHistorica(
    val chave: String,
    val turmaId: String,
    val modulos: List<ResultadoModulo>,
    val turmaNome: String = turmaId
) {
    fun resultado() = ResultadoTurma(TurmaMonitorada(turmaId, turmaNome, "", chave.substringAfter('|')), modulos, "")
}

data class DiaHistorico(val data: LocalDate, val fonte: FontePainel, val medicoes: List<MedicaoHistorica>) {
    val modulos get() = SgeDashboard.resumir(medicoes.map { it.resultado() })
    val turmas get() = medicoes.map { it.turmaId }.distinct().size
    fun variacao(anterior: DiaHistorico, indice: Int): Int? {
        if (fonte != anterior.fonte || medicoes.map { it.chave }.toSet() != anterior.medicoes.map { it.chave }.toSet()) return null
        val a = anterior.modulos[indice]; val b = modulos[indice]
        return if (a.completa && b.completa) b.quantidade!! - a.quantidade!! else null
    }
}

data class RankingSemanal(val chave: String, val titulo: String, val quantidade: Int?, val anterior: Int? = null, val variacao: Int? = null)

object SgeHistorico {
    /** Mantém quatro semanas para comparar a janela atual com a semana anterior. */
    const val DIAS_RETIDOS = 31L

    fun registrar(anteriores: List<DiaHistorico>, fonte: FontePainel, resultados: List<ResultadoTurma>, hoje: LocalDate): List<DiaHistorico> {
        if (resultados.isEmpty()) return anteriores
        val novas = resultados.distinctBy(::chaveResultado).map { r ->
            MedicaoHistorica(chaveResultado(r), r.turma.id,
                (0..4).map { i -> (r.modulos.getOrNull(i) ?: ResultadoModulo()).copy(detalhe = "") }, r.turma.nome)
        }
        val dia = anteriores.firstOrNull { it.data == hoje && it.fonte == fonte }
        val chaves = novas.map { it.chave }.toSet()
        val atual = DiaHistorico(hoje, fonte, dia?.medicoes.orEmpty().filter { it.chave !in chaves } + novas)
        return (anteriores.filterNot { it.data == hoje && it.fonte == fonte } + atual)
            .filter { !it.data.isBefore(hoje.minusDays(DIAS_RETIDOS - 1)) && !it.data.isAfter(hoje) }
            .sortedWith(compareBy({ it.data }, { it.fonte.name }))
    }

    /** Compara a última medição de cada turma/componente nas duas janelas de sete dias. */
    fun rankingSemanal(
        dias: List<DiaHistorico>, fonte: FontePainel, indice: Int, hoje: LocalDate,
        por: Agrupamento, vinculos: List<VinculoDocente> = emptyList(),
        locais: Map<String, List<DocenteRef>> = emptyMap(), limite: Int = 12
    ): List<RankingSemanal> {
        require(indice in 0..4)
        fun janela(inicio: LocalDate, fim: LocalDate): Map<String, RankingSemanal> {
            val ultimas = dias.filter { it.fonte == fonte && !it.data.isBefore(inicio) && !it.data.isAfter(fim) }
                .flatMap { d -> d.medicoes.map { d.data to it } }
                .groupBy { it.second.chave }
                .values.mapNotNull { amostras -> amostras.maxByOrNull { it.first }?.second }
            val resultados = ultimas.map { it.resultado() }
            return SgeDashboard.agrupar(resultados, por, vinculos, locais).associate { grupo ->
                val modulo = grupo.modulos[indice]
                val valor = modulo.quantidade.takeIf { modulo.completa }
                grupo.chave to RankingSemanal(grupo.chave, grupo.titulo, valor)
            }
        }
        val atual = janela(hoje.minusDays(6), hoje)
        val anterior = janela(hoje.minusDays(13), hoje.minusDays(7))
        return atual.values.map { item ->
            val passado = anterior[item.chave]?.quantidade
            item.copy(anterior = passado, variacao = if (item.quantidade != null && passado != null) item.quantidade - passado else null)
        }.sortedWith(compareBy<RankingSemanal>({ if (it.quantidade == null) 1 else 0 }, { -(it.quantidade ?: 0) }, { normalizarSge(it.titulo) }))
            .take(limite.coerceIn(1, 50))
    }

    fun json(dias: List<DiaHistorico>): String = JSONArray().also { a -> dias.forEach { d -> a.put(JSONObject()
        .put("dia", d.data.toString()).put("fonte", d.fonte.name).put("medicoes", JSONArray().also { ms -> d.medicoes.forEach { m ->
            ms.put(JSONObject().put("chave", m.chave).put("turma", m.turmaId).put("nome", m.turmaNome).put("modulos", JSONArray().also { mm -> m.modulos.forEach { mm.put(JSONArray().put(it.quantidade ?: JSONObject.NULL).put(it.aguardando).put(it.aprovados).put(it.coberturaCompleta).put(it.atualizadoMs)) } }))
        } })) } }.toString()

    fun ler(raw: String): List<DiaHistorico> {
        val a = JSONArray(raw)
        return (0 until a.length()).map { i ->
            val d = a.getJSONObject(i); val ms = d.getJSONArray("medicoes")
            DiaHistorico(LocalDate.parse(d.getString("dia")), FontePainel.valueOf(d.getString("fonte")), (0 until ms.length()).map { j ->
                val m = ms.getJSONObject(j); val mm = m.getJSONArray("modulos")
                MedicaoHistorica(m.getString("chave"), m.getString("turma"), (0 until mm.length()).map { k -> mm.getJSONArray(k).let { v ->
                    ResultadoModulo(if (v.isNull(0)) null else v.getInt(0), "", v.getInt(1), v.getInt(2), v.getBoolean(3), if (v.length() > 4) v.getLong(4) else 0L)
                } }, m.optString("nome", m.getString("turma")))
            })
        }
    }
}

object SgeHistoricoStore {
    private const val KEY = "historico_semanal_v1"
    fun ler(ctx: Context) = runCatching { SgeHistorico.ler(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, "[]")!!) }.getOrDefault(emptyList())
    @Synchronized fun registrar(ctx: Context, fonte: FontePainel, resultados: List<ResultadoTurma>) {
        val dias = SgeHistorico.registrar(ler(ctx), fonte, resultados, LocalDate.now())
        SgeSessaoStore.gravarSeAtual(ctx) { check(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, SgeHistorico.json(dias)).commit()) }
    }
}

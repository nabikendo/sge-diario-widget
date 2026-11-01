package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Aula sem registro. [data] em ISO (yyyy-MM-dd) para ordenar; [hora] vem da janela do dia. */
data class PendenciaDia(val data: String, val disciplina: String, val hora: String)
data class CoordTurma(val nome: String, val pendencias: List<PendenciaDia>, val erro: String? = null)
data class CoordPainel(
    val turmas: List<CoordTurma> = emptyList(),
    val mensagem: String = "Entre com o perfil Unidade escolar, abra Escola → Conteúdo Aplicado na aba SGE Web e toque em Sincronizar coordenação.",
    val atualizado: String = "",
    val sincronizando: Boolean = false
)

object SgeCoordStore {
    private const val KEY = "painel_coord_v1"
    fun salvar(context: Context, p: CoordPainel) {
        val j = JSONObject().put("mensagem", p.mensagem).put("atualizado", p.atualizado).put("sincronizando", p.sincronizando)
            .put("turmas", JSONArray().also { a ->
                p.turmas.forEach { t ->
                    a.put(JSONObject().put("nome", t.nome).put("erro", t.erro ?: JSONObject.NULL)
                        .put("pend", JSONArray().also { pa ->
                            t.pendencias.forEach { pa.put(JSONObject().put("d", it.data).put("c", it.disciplina).put("h", it.hora)) }
                        }))
                }
            })
        context.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, j.toString()).commit()
    }
    fun ler(context: Context): CoordPainel = runCatching {
        val raw = context.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return@runCatching CoordPainel()
        val j = JSONObject(raw)
        val ts = j.getJSONArray("turmas")
        CoordPainel((0 until ts.length()).map { i ->
            val o = ts.getJSONObject(i); val pa = o.getJSONArray("pend")
            CoordTurma(o.getString("nome"), (0 until pa.length()).map { k ->
                val p = pa.getJSONObject(k); PendenciaDia(p.getString("d"), p.getString("c"), p.optString("h"))
            }, if (o.isNull("erro")) null else o.getString("erro"))
        }, j.optString("mensagem"), j.optString("atualizado"), j.optBoolean("sincronizando"))
    }.getOrElse { CoordPainel() }
}

package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Aula sem registro. [data] em ISO (yyyy-MM-dd) para ordenar; [hora] vem da janela do dia. */
data class PendenciaDia(val data: String, val disciplina: String, val hora: String)

/** Uma turma verificada na coordenação: um resultado por disciplina (conteúdos, frequência, planos, PEI). */
data class CoordTurma(
    val id: String,
    val nome: String,
    val disciplinas: List<ResultadoTurma> = emptyList(),
    val erro: String? = null,
    val atualizado: String = ""
)
data class CoordPainel(
    val turmas: List<CoordTurma> = emptyList(),
    val mensagem: String = "Entre com o perfil Unidade escolar, abra Escola → Conteúdo Aplicado na aba SGE Web e toque em Carregar turmas.",
    val sincronizando: Boolean = false
)

object SgeCoordStore {
    private const val KEY = "painel_coord_v2"
    fun salvar(context: Context, p: CoordPainel) {
        val j = JSONObject().put("mensagem", p.mensagem).put("sincronizando", p.sincronizando)
            .put("turmas", JSONArray().also { a ->
                p.turmas.forEach { t ->
                    a.put(JSONObject().put("id", t.id).put("nome", t.nome).put("erro", t.erro ?: JSONObject.NULL)
                        .put("atualizado", t.atualizado)
                        .put("disc", JSONArray().also { da -> t.disciplinas.forEach { da.put(it.json()) } }))
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
            val o = ts.getJSONObject(i); val da = o.getJSONArray("disc")
            CoordTurma(o.getString("id"), o.getString("nome"),
                (0 until da.length()).map { k -> ResultadoTurma.ler(da.getJSONObject(k)) },
                if (o.isNull("erro")) null else o.getString("erro"), o.optString("atualizado"))
        }, j.optString("mensagem"), j.optBoolean("sincronizando"))
    }.getOrElse { CoordPainel() }
}

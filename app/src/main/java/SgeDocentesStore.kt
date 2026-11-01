package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PainelDocentes(
    val professores: List<DocenteRef> = emptyList(),
    val vinculos: List<VinculoDocente> = emptyList(),
    val locais: Map<String, List<DocenteRef>> = emptyMap(),
    val mensagem: String = "Atualize os vínculos para agrupar a coordenação por professor.",
    val sincronizando: Boolean = false,
    val inicio: Long = 0L
)

object SgeDocentesStore {
    private const val KEY = "vinculos_docentes_v1"
    private fun docente(p: DocenteRef) = JSONObject().put("id", p.id).put("nome", p.nome)
    private fun lista(ps: List<DocenteRef>) = JSONArray().also { a -> ps.forEach { a.put(docente(it)) } }
    private fun lerLista(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it).let { o -> DocenteRef(o.getString("id"), o.getString("nome")) } }
    fun salvar(context: Context, p: PainelDocentes) {
        val j = JSONObject().put("professores", lista(p.professores)).put("mensagem", p.mensagem)
            .put("sincronizando", p.sincronizando).put("inicio", p.inicio)
            .put("vinculos", JSONArray().also { a -> p.vinculos.forEach { v ->
                a.put(JSONObject().put("turma", v.turmaId).put("componente", v.componente).put("professor", docente(v.docente)))
            } }).put("locais", JSONObject().also { o -> p.locais.forEach { (key, ps) -> o.put(key, lista(ps)) } })
        check(context.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, j.toString()).commit())
    }
    fun ler(context: Context): PainelDocentes = runCatching {
        val raw = context.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return@runCatching PainelDocentes()
        val j = JSONObject(raw); val vs = j.getJSONArray("vinculos"); val ls = j.optJSONObject("locais") ?: JSONObject()
        val p = PainelDocentes(lerLista(j.getJSONArray("professores")), (0 until vs.length()).map { i ->
            val v = vs.getJSONObject(i); val d = v.getJSONObject("professor")
            VinculoDocente(v.getString("turma"), v.getString("componente"), DocenteRef(d.getString("id"), d.getString("nome")))
        }, ls.keys().asSequence().associateWith { lerLista(ls.getJSONArray(it)) }, j.optString("mensagem"),
            j.optBoolean("sincronizando"), j.optLong("inicio"))
        if (p.sincronizando && System.currentTimeMillis() - p.inicio > 30 * 60 * 1000L)
            p.copy(sincronizando = false, mensagem = "Atualização de vínculos interrompida. Tente novamente.") else p
    }.getOrElse { PainelDocentes(mensagem = "Não foi possível ler os vínculos locais. Atualize os vínculos.") }
    fun vincularLocal(context: Context, resultado: ResultadoTurma, docentes: List<DocenteRef>?) {
        val p = ler(context); val key = chaveResultado(resultado)
        salvar(context, p.copy(locais = if (docentes == null) p.locais - key else p.locais + (key to docentes)))
    }
}

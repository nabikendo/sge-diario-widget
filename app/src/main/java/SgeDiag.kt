package br.to.seduc.sgediariowidget.data

import org.json.JSONObject
import org.jsoup.Jsoup

/** Resume só a ESTRUTURA de uma resposta (ids, nomes de campos, colunas); nunca valores nem textos de alunos. */
object SgeDiag {
    fun estrutura(body: String): String {
        val t = body.trimStart()
        if (t.startsWith("{")) {
            val j = runCatching { JSONObject(t) }.getOrNull()
            if (j != null) {
                val inner = j.optString("html").takeIf { it.isNotBlank() }
                return "json{" + j.keys().asSequence().joinToString(",") + "}" +
                    (inner?.let { " html: " + html(it) } ?: "")
            }
        }
        return html(body)
    }
    private fun html(h: String): String = runCatching {
        val d = Jsoup.parse(h)
        val ids = d.select("[id]").map { it.id() }.take(40)
        val selects = d.select("select").take(10).joinToString(",") {
            it.id().ifBlank { it.attr("name") } + "(" + it.select("option").size + ")"
        }
        val campos = d.select("input[name],input[id]").take(15).joinToString(",") { it.id().ifBlank { it.attr("name") } }
        val tabelas = d.select("table").take(5).joinToString(",") {
            it.id().ifBlank { "?" } + "[" + it.select("thead th").size + "col/" + it.select("tbody tr").size + "lin]"
        }
        "title=" + d.title().take(60) + " ids=" + ids + " selects=" + selects + " campos=" + campos + " tabelas=" + tabelas
    }.getOrElse { "html ilegível" }
}

package br.to.seduc.sgediariowidget.data

import org.jsoup.Jsoup

/** Reutiliza a estrutura do horário já empregada pela consulta pessoal; não presume IDs nem nomes. */
object SgeDocentesParsing {
    fun professores(html: String): List<DocenteRef> = Jsoup.parse(html).select("#ProfessorId option[value]").toList()
        .filter { it.attr("value").isNotBlank() && it.attr("value") != "0" && !it.hasAttr("disabled") }
        .filter { !normalizarSge(it.text()).startsWith("selecione") }
        .map { DocenteRef(it.attr("value"), it.text().trim()) }.filter { it.nome.isNotBlank() }
        .distinctBy { it.id }.sortedBy { normalizarSge(it.nome) }

    fun tipoHorario(html: String): String {
        val doc = Jsoup.parse(html)
        val campo = doc.selectFirst("#Tipo") ?: doc.selectFirst("[name=Tipo]")
        return campo?.attr("value")?.takeIf { it.isNotBlank() }
            ?: campo?.selectFirst("option[selected]")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: campo?.selectFirst("option[value]:not([value=''])")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: error("Tipo de horário não identificado no SGE.")
    }

    fun vinculos(html: String, professor: DocenteRef, turmas: List<Pair<String, String>>): List<VinculoDocente> {
        val linhas = Jsoup.parse(html).select("tr").map { it.select("td") }
            .filter { it.size == 3 && normalizarSge(it[0].text()).contains(" as ") && Regex("\\d{1,2}:\\d{2}").containsMatchIn(it[0].text()) }
        check(linhas.isNotEmpty()) { "O horário não retornou aulas com turma e componente reconhecidos." }
        return linhas.mapNotNull { cells ->
            val nome = normalizarSge(cells[2].text()); val componente = cells[1].text().trim()
            val exatas = turmas.filter { normalizarSge(it.second) == nome }
            val candidatas = exatas.ifEmpty { turmas.filter { normalizarSge(it.second.substringBefore('/')) == nome.substringBefore('/') } }
            val ids = candidatas.map { it.first }.distinct()
            if (ids.size == 1 && componente.isNotBlank()) VinculoDocente(ids.single(), componente, professor) else null
        }.distinctBy { chaveVinculo(it.turmaId, it.componente) }
    }
}

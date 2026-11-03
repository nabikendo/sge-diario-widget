package br.to.seduc.sgediariowidget.data

import org.jsoup.Jsoup

data class DiagnosticoHorario(
    val professorId: String,
    val professorNome: String,
    val turmaHorario: String,
    val componente: String,
    val candidatos: List<Pair<String, String>>,
    val tipo: String
)
data class ResultadoVinculosDocente(val vinculos: List<VinculoDocente>, val diagnosticos: List<DiagnosticoHorario>)

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

    /** Mantém a API anterior para consumidores que só precisam dos vínculos sem diagnóstico. */
    fun vinculos(html: String, professor: DocenteRef, turmas: List<Pair<String, String>>): List<VinculoDocente> =
        analisarVinculos(html, professor, turmas).vinculos

    /** Candidatos sem decisão automática: linhas sem correspondência única são devolvidas para revisão manual. */
    fun analisarVinculos(html: String, professor: DocenteRef, turmas: List<Pair<String, String>>): ResultadoVinculosDocente {
        val linhas = Jsoup.parse(html).select("tr").map { it.select("td") }
            .filter { it.size == 3 && normalizarSge(it[0].text()).contains(" as ") && Regex("\\d{1,2}:\\d{2}").containsMatchIn(it[0].text()) }
        check(linhas.isNotEmpty()) { "O horário não retornou aulas com turma e componente reconhecidos." }
        val vinculos = mutableListOf<VinculoDocente>()
        val diagnosticos = mutableListOf<DiagnosticoHorario>()
        linhas.forEach { cells ->
            val nomeNormalizado = normalizarSge(cells[2].text())
            val nomeOriginal = cells[2].text().trim()
            val componente = cells[1].text().trim()
            val exatas = turmas.filter { normalizarSge(it.second) == nomeNormalizado }
            val candidatas = exatas.ifEmpty { turmas.filter { normalizarSge(it.second.substringBefore('/')) == nomeNormalizado.substringBefore('/') } }
            val ids = candidatas.map { it.first }.distinct()
            if (ids.size == 1 && componente.isNotBlank()) {
                vinculos += VinculoDocente(ids.single(), componente, professor)
            } else {
                diagnosticos += DiagnosticoHorario(professor.id, professor.nome, nomeOriginal, componente,
                    candidatas.distinctBy { it.first }, when {
                        ids.size > 1 -> "ambiguo"
                        ids.isEmpty() -> "sem_correspondencia"
                        else -> "componente_ausente"
                    })
            }
        }
        val unicos = vinculos.distinctBy { chaveVinculo(it.turmaId, it.componente) }
        val diagnosticosUnicos = diagnosticos.distinctBy {
            "${it.professorId}|${normalizarSge(it.turmaHorario)}|${normalizarSge(it.componente)}|${it.tipo}"
        }
        return ResultadoVinculosDocente(unicos, diagnosticosUnicos)
    }
}

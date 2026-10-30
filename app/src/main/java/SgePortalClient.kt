package br.to.seduc.sgediariowidget.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Read-only SGE queries. Unknown results are never converted to zero. */
class SgePortalClient(private val cookies: String) {
    private val base = "https://sge.seduc.to.pontoid.com.br"
    private fun request(path: String, params: Map<String, String> = emptyMap()): String {
        val response = Jsoup.connect(base + path).header("Cookie", cookies)
            .header("X-Requested-With", "XMLHttpRequest")
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36")
            .data(params).timeout(30000).ignoreContentType(true).execute()
        check(!response.url().path.contains("/Acesso/Entrar", true)) { "Sessão expirada" }
        val text = response.body()
        check(!text.contains("id=\"login-form\"")) { "Sessão expirada" }
        return text
    }

    fun turmasDoHorario(): List<TurmaMonitorada> {
        val home = Jsoup.parse(request("/"))
        val horario = Jsoup.parse(request("/horariodeauladoprofessor"))
        val professor = horario.selectFirst("#ProfessorId option[selected]")?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: horario.selectFirst("#ProfessorId option[value]:not([value=''])")?.attr("value")
            ?: error("Professor não identificado no horário")
        val tipo = horario.selectFirst("#Tipo option[selected]")?.attr("value")
            ?: horario.selectFirst("#Tipo option[value]:not([value=''])")?.attr("value")
            ?: error("Tipo de horário não identificado")
        val result = JSONObject(request("/HorarioDeAulaDoProfessor/QuadroHorarioProfessor", mapOf(
            "dataReferencia" to LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            "tipo" to tipo, "colaboradorId" to professor, "escolaId" to "",
            "turno" to "", "somenteConflitos" to "false"
        )))
        check(result.has("html")) { "Horário não retornou dados" }
        val rows = Jsoup.parse(result.getString("html")).select("tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size != 3 || !cells[0].text().contains("às")) null
            else cells[2].text().trim() to cells[1].text().trim()
        }.distinct()
        check(rows.isNotEmpty()) { "Horário sem turmas para a data de referência" }
        val options = home.select("#TurmaContextoId option[data-turmadescricao]")
        return rows.map { (nome, disciplina) ->
            val matching = options.asSequence()
                .filter { it.attr("data-turmadescricao") == nome }
                .toList()
            check(matching.size == 1) { "Turma $nome sem identificador único" }
            val option = matching.single()
            TurmaMonitorada(option.attr("value").substringBefore('_'), nome,
                option.attr("data-escoladescricao"), disciplina)
        }
    }

    fun planos(turma: TurmaMonitorada, nomeTipo: String): ConsultaModulo {
        return runCatching {
            val page = Jsoup.parse(request("/planejamentoturma", mapOf("turmaId" to turma.id)))
            val tipo = page.select("#TipoDeRegistroPedagogicoId option").singleOrNull {
                it.text().trim().equals(nomeTipo, true)
            }?.attr("value") ?: error("Tipo de planejamento indisponível")
            val grades = JSONObject(request("/PlanejamentoTurma/CarregueGradesEscolares", mapOf("turmaId" to turma.id)))
                .getJSONArray("grades")
            val grade = (0 until grades.length()).map { grades.getJSONObject(it) }.singleOrNull {
                it.optString("Descricao").equals(turma.disciplina, true)
            }?.get("Codigo")?.toString() ?: error("Componente não identificado")
            val response = JSONObject(request("/PlanejamentoTurma/PartialPlanejamentos", mapOf(
                "TurmaId" to turma.id, "TipoDeRegistroPedagogicoId" to tipo,
                "GradeEscolarId" to grade, "AtividadeDeAEEId" to "", "ModuloUnidadeCurricularId" to "",
                "Situacao" to "", "Periodos" to "[]"
            )))
            check(response.has("html")) { "Consulta sem resposta de registros" }
            val doc = Jsoup.parse(response.getString("html"))
            val table = doc.selectFirst("#tabela_planejamentos")
                ?: error("Grade de planejamentos não encontrada")
            val headers = table.select("thead th").map { it.text().trim() }
            val statusIndex = headers.indexOfFirst { it.equals("Situação", true) }
            check(statusIndex >= 0) { "Coluna de situação não encontrada" }
            val statuses = table.select("tbody tr").mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size <= statusIndex) null else cells[statusIndex].text().trim()
            }
            val pending = statuses.count { it.equals("Não Enviado", true) || it.equals("Reprovado", true) }
            val waiting = statuses.count { it.equals("Aguardando Avaliação", true) }
            ConsultaModulo(pending, "$pending não enviados/reprovados; $waiting aguardando avaliação. Ausências de planos ainda não verificadas.")
        }.getOrElse { ConsultaModulo(null, it.message ?: "Falha na consulta") }
    }
}

data class ConsultaModulo(val quantidade: Int?, val detalhe: String)

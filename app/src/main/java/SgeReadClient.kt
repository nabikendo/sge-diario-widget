package br.to.seduc.sgediariowidget.data

import org.jsoup.Jsoup
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TurmaMonitorada(val id: String, val nome: String, val escola: String, val disciplina: String)

class SessaoExpirada : IllegalStateException("Sessão expirada. Entre novamente no SGE.")

/** Only the ListarFrequencia search uses POST; no save endpoint is called. */
class SgeReadClient(cookies: String, private val userAgent: String, private val onCookie: (String) -> Unit) {
    private val jar = cookies.split(';').mapNotNull {
        val p = it.trim().split('=', limit = 2)
        if (p.size == 2) p[0] to p[1] else null
    }.toMap().toMutableMap()
    private val grades = mutableMapOf<String, String>()
    private val base = "https://sge.seduc.to.pontoid.com.br"
    private fun request(path: String, params: Map<String, String> = emptyMap(), post: Boolean = false): String {
        val response = Jsoup.connect(base + path).cookies(jar).header("X-Requested-With", "XMLHttpRequest")
            .userAgent(userAgent).data(params).timeout(30000).ignoreContentType(true).maxBodySize(12 * 1024 * 1024)
            .method(if (post) org.jsoup.Connection.Method.POST else org.jsoup.Connection.Method.GET).execute()
        jar.putAll(response.cookies())
        response.headers("Set-Cookie").forEach(onCookie)
        if (response.url().path.contains("/Acesso/Entrar", true)) throw SessaoExpirada()
        val body = response.body()
        if (body.contains("id=\"login-form\"") || body.contains("id=\"btn-entrar\"")) throw SessaoExpirada()
        return body
    }
    fun turmas(): List<TurmaMonitorada> {
        val home = Jsoup.parse(request("/"))
        val horario = Jsoup.parse(request("/horariodeauladoprofessor"))
        val professor = horario.selectFirst("#idProfessor")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: horario.selectFirst("#ProfessorId option[selected]")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: horario.select("#ProfessorId option[value]:not([value=''])").singleOrNull()?.attr("value")
            ?: error("Professor não identificado")
        val tipo = horario.selectFirst("#Tipo option[selected]")?.attr("value")?.takeIf { it.isNotBlank() }
            ?: horario.selectFirst("#Tipo option[value]:not([value=''])")?.attr("value")
            ?: error("Tipo de horário não identificado")
        val json = JSONObject(request("/HorarioDeAulaDoProfessor/QuadroHorarioProfessor", mapOf(
            "dataReferencia" to LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),
            "tipo" to tipo, "colaboradorId" to professor, "escolaId" to "", "turno" to "", "somenteConflitos" to "false")))
        val doc = Jsoup.parse(json.getString("html"))
        val options = home.select("#TurmaContextoId option[data-turmadescricao]")
        val found = doc.select("tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size != 3 || !cells[0].text().contains("às")) null else {
                val nome = cells[2].text().trim()
                val matches = options.filter { it.attr("data-turmadescricao") == nome }
                check(matches.size == 1) { "Identificação ambígua da turma $nome" }
                val opt = matches.single()
                check(opt.attr("data-tipodaturmaenum") == "0") { "Tipo de turma ainda não suportado" }
                TurmaMonitorada(opt.attr("value").substringBefore('_'), nome,
                    opt.attr("data-escoladescricao"), cells[1].text().trim())
            }
        }.distinct()
        check(found.isNotEmpty()) { "Horário sem turmas para a data de referência" }
        return found.sortedWith(compareBy({ it.escola }, { it.nome }, { it.disciplina }))
    }
    private fun grade(t: TurmaMonitorada): String = grades.getOrPut(t.id + "/" + t.disciplina) {
        val a = JSONObject(request("/PlanejamentoTurma/CarregueGradesEscolares", mapOf("turmaId" to t.id))).getJSONArray("grades")
        (0 until a.length()).map { a.getJSONObject(it) }.singleOrNull {
            it.optString("Descricao").equals(t.disciplina, true)
        }?.get("Codigo")?.toString() ?: error("Componente não identificado")
    }
    private fun consulta(block: () -> ResultadoModulo): ResultadoModulo = try { block() }
        catch (e: SessaoExpirada) { throw e }
        catch (_: Exception) { ResultadoModulo(detalhe = "Não verificado: resposta indisponível ou formato inesperado do SGE.") }

    fun conteudos(t: TurmaMonitorada): ResultadoModulo = consulta {
        val j = JSONObject(request("/Conteudo/CarregarDadosConteudoAplicado", mapOf(
            "TurmaId" to t.id, "TipoDeTurma" to "0", "GradeEscolarId" to grade(t),
            "StatusConteudoAplicado" to "", "DataInicio" to "", "DataFinal" to "")))
        SgeParsing.conteudos(j.getString("html"), t.disciplina, LocalDate.now())
    }
    fun frequencia(t: TurmaMonitorada): ResultadoModulo = consulta {
        val j = JSONObject(request("/NotasFaltas/ObtenhaInicioFimParametros", mapOf("turmaId" to t.id, "bimestre" to "", "mes" to "")))
        val format = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        val inicio = LocalDate.parse(j.getString("dataInicio"), format)
        val fim = minOf(LocalDate.now(), LocalDate.parse(j.getString("dataFim"), format))
        if (fim < inicio) return@consulta ResultadoModulo(0, "Período letivo ainda não iniciado.", coberturaCompleta = true)
        val response = JSONObject(request("/NotasFaltas/ListarFrequencia", mapOf(
            "turmaId" to t.id, "gradeEscolarId" to grade(t), "bimestre" to "", "mes" to "",
            "dataInicial" to inicio.format(format), "dataFinal" to fim.format(format), "frequenciaNaoConcluida" to "true"), true))
        SgeParsing.frequencia(response.getString("html"), fim)
    }
    fun planos(t: TurmaMonitorada, tipoNome: String): ResultadoModulo = consulta {
        val page = Jsoup.parse(request("/planejamentoturma", mapOf("turmaId" to t.id)))
        val tipo = page.select("#TipoDeRegistroPedagogicoId option").singleOrNull {
            it.text().trim().equals(tipoNome, true)
        }?.attr("value") ?: error("Tipo de planejamento indisponível")
        val response = JSONObject(request("/PlanejamentoTurma/PartialPlanejamentos", mapOf(
            "TurmaId" to t.id, "TipoDeRegistroPedagogicoId" to tipo, "GradeEscolarId" to grade(t),
            "AtividadeDeAEEId" to "", "ModuloUnidadeCurricularId" to "", "Situacao" to "", "Periodos" to "[]")))
        SgeParsing.planos(response.getString("html"))
    }
}

object SgeParsing {
    private val dateRegex = Regex("\\d{2}/\\d{2}/\\d{4}")
    private val format = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    private fun date(title: String) = LocalDate.parse(dateRegex.find(title)?.value ?: error("Data ausente"), format)
    fun conteudos(html: String, disciplina: String, hoje: LocalDate): ResultadoModulo {
        val doc = Jsoup.parse(html)
        check(doc.select(".calendario .semana").isNotEmpty()) { "Calendário ausente" }
        val pending = doc.select(".calendario .semana .vermelho").count { day ->
            val d = date(day.attr("data-original-title").ifBlank { day.attr("title") })
            val components = Jsoup.parse(day.attr("data-content")).select("tr td:first-child").map { it.text().trim() }
            check(components.isNotEmpty()) { "Componente ausente no detalhe" }
            !d.isAfter(hoje) && components.any { it.equals(disciplina, true) }
        }
        return ResultadoModulo(pending, "Dias pendentes no mapa do SGE até ${hoje.format(format)}.", coberturaCompleta = true)
    }
    fun frequencia(html: String, ate: LocalDate): ResultadoModulo {
        val table = Jsoup.parse(html).selectFirst("#gridFrequencia") ?: error("Grade de frequência ausente")
        val dates = table.select("thead .cabecalhoMes").map { date(it.attr("title")) }
        return ResultadoModulo(dates.distinct().count { !it.isAfter(ate) },
            "Dias do filtro oficial Somente Frequência Não Concluída até ${ate.format(format)}.", coberturaCompleta = true)
    }
    fun planos(html: String): ResultadoModulo {
        val table = Jsoup.parse(html).selectFirst("#tabela_planejamentos") ?: error("Grade de planos ausente")
        val i = table.select("thead th").indexOfFirst { it.text().trim().equals("Situação", true) }
        check(i >= 0)
        val statuses = table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size <= i) null else cells[i].text().trim()
        }
        val known = setOf("Não Enviado", "Aguardando Avaliação", "Aprovado", "Reprovado")
        check(statuses.all { s -> known.any { it.equals(s, true) } }) { "Situação desconhecida" }
        val pending = statuses.count { it.equals("Não Enviado", true) || it.equals("Reprovado", true) }
        val waiting = statuses.count { it.equals("Aguardando Avaliação", true) }
        val approved = statuses.count { it.equals("Aprovado", true) }
        return ResultadoModulo(pending, "${statuses.size} registros: $waiting aguardando avaliação; $approved aprovados. " +
            "* Somente registros existentes; planos e PEIs ainda não criados precisam de conferência de cobertura.", waiting, approved)
    }
}

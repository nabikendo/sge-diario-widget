package br.to.seduc.sgediariowidget.data

import org.jsoup.Jsoup
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import org.jsoup.nodes.Document

data class TurmaMonitorada(val id: String, val nome: String, val escola: String, val disciplina: String)

class SessaoExpirada : IllegalStateException("Sessão expirada. Entre novamente no SGE.")

/** Only the ListarFrequencia search uses POST; no save endpoint is called. */
class SgeReadClient(cookies: String, private val userAgent: String, private val onCookie: (String) -> Unit) {
    private val jar = cookies.split(';').mapNotNull {
        val p = it.trim().split('=', limit = 2)
        if (p.size == 2) p[0] to p[1] else null
    }.toMap().toMutableMap()
    private val grades = mutableMapOf<String, String>()
    private val conteudosHtml = mutableMapOf<String, String>()
    private val base = "https://sge.seduc.to.pontoid.com.br"
    private fun request(path: String, params: Map<String, String> = emptyMap(), post: Boolean = false): String {
        val response = try {
            Jsoup.connect(base + path).cookies(jar).header("X-Requested-With", "XMLHttpRequest")
            .userAgent(userAgent).data(params).timeout(30000).ignoreContentType(true).maxBodySize(12 * 1024 * 1024)
            .method(if (post) org.jsoup.Connection.Method.POST else org.jsoup.Connection.Method.GET).execute()
        } catch (e: Exception) {
            throw IllegalStateException("Falha de rede em $path: ${e.message ?: e.javaClass.simpleName}", e)
        }
        jar.putAll(response.cookies())
        response.headers("Set-Cookie").forEach(onCookie)
        if (response.url().path.contains("/Acesso/Entrar", true)) throw SessaoExpirada()
        val body = response.body()
        if (body.contains("id=\"login-form\"") || body.contains("id=\"btn-entrar\"")) throw SessaoExpirada()
        if (response.statusCode() !in 200..299) throw IllegalStateException("SGE respondeu HTTP ${response.statusCode()} em $path")
        if (body.isBlank()) throw IllegalStateException("SGE retornou resposta vazia em $path")
        return body
    }
    fun turmas(): List<TurmaMonitorada> {
        val home = Jsoup.parse(request("/"))
        // O SGE carrega esta tela em #content por AJAX. O GET direto da rota
        // retorna a página de entrada sem o formulário/identidade do professor.
        val horario = run { val resposta = request("/horariodeauladoprofessor"); Jsoup.parse(runCatching { JSONObject(resposta).let { it.optString("html").ifBlank { it.optString("data") } }.takeIf { it.isNotBlank() } }.getOrNull() ?: resposta) }
        check(horario.selectFirst("#idProfessor") != null || horario.selectFirst("#ProfessorId") != null) {
            "A página de horário não retornou o formulário esperado (resposta AJAX do SGE)."
        }
        val professor = horario.selectFirst("#idProfessor")?.let { it.attr("value").ifBlank { it.attr("data-id") } }
            ?.takeIf { it.isNotBlank() }
            ?: horario.selectFirst("#ProfessorId")?.let { el ->
                el.attr("value").ifBlank { el.selectFirst("option:checked")?.attr("value") ?: "" }
            }?.takeIf { it.isNotBlank() }
            ?: horario.select("[name='colaboradorId'], [name='ColaboradorId']").firstOrNull()?.attr("value")
                ?.takeIf { it.isNotBlank() }
            ?: error("O SGE não informou o identificador do professor no horário.")
        val tipo = horario.selectFirst("#Tipo")?.let { el ->
            el.attr("value").ifBlank { el.selectFirst("option:checked")?.attr("value") ?: "" }
        }?.takeIf { it.isNotBlank() }
            ?: horario.select("[name='Tipo'] option:checked").firstOrNull()?.attr("value")?.takeIf { it.isNotBlank() }
            ?: error("O SGE não informou o tipo de horário selecionado.")
        val json = JSONObject(request("/HorarioDeAulaDoProfessor/QuadroHorarioProfessor", mapOf(
            "dataReferencia" to LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),
            "tipo" to tipo, "colaboradorId" to professor, "escolaId" to "", "turno" to "", "somenteConflitos" to "false")))
        val doc = Jsoup.parse(json.getString("html"))
        val options = home.select("#TurmaContextoId option[data-turmadescricao]")
        val found = doc.select("tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size != 3 || !cells[0].text().contains("às")) null else {
                val nome = cells[2].text().trim()
                // Elements.filter é o filtro estrutural do Jsoup (NodeFilter), não o
                // filtro de coleções do Kotlin. Convertemos para Sequence antes de
                // comparar o nome mostrado no quadro de horários.
                val matches = options.asSequence()
                    .filter { it.attr("data-turmadescricao") == nome }
                    .toList()
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
        catch (e: Exception) {
            val causa = e.message?.replace(Regex("\\s+"), " ")?.take(180) ?: e.javaClass.simpleName
            ResultadoModulo(detalhe = "Não verificado: $causa")
        }

    fun conteudos(t: TurmaMonitorada): ResultadoModulo = consulta {
        val j = JSONObject(request("/Conteudo/CarregarDadosConteudoAplicado", mapOf(
            "TurmaId" to t.id, "TipoDeTurma" to "0", "GradeEscolarId" to grade(t),
            "StatusConteudoAplicado" to "", "DataInicio" to "", "DataFinal" to "")))
        val html = j.getString("html")
        conteudosHtml[t.id + "/" + t.disciplina] = html
        SgeParsing.conteudos(html, t.disciplina, LocalDate.now())
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
        val html = response.getString("html")
        val existentes = SgeParsing.planos(html)
        try {
            val cobertura = ausentes(t, tipo, Jsoup.parse(html))
            existentes.copy(quantidade = existentes.quantidade!! + cobertura,
                detalhe = "$cobertura documentos ausentes; ${existentes.quantidade} não enviados/reprovados; " +
                    "${existentes.aguardando} aguardando avaliação; ${existentes.aprovados} aprovados. " +
                    "Cobertura dos períodos iniciados, conforme configuração e elegibilidade do SGE.", coberturaCompleta = true)
        } catch (e: SessaoExpirada) { throw e }
        catch (e: Exception) {
            val causa = e.message?.replace(Regex("\\s+"), " ")?.take(150) ?: e.javaClass.simpleName
            existentes.copy(detalhe = existentes.detalhe + " Cobertura não verificada: $causa")
        }
    }

    private fun ausentes(t: TurmaMonitorada, tipo: String, lista: Document): Int {
        val gradeId = grade(t)
        val form = Jsoup.parse(request("/PlanejamentoTurma/Editar/0", mapOf(
            "tipoDeTurma" to "0", "tipoDeRegistroPedagogicoId" to tipo, "gradeEscolarId" to gradeId, "turmaId" to t.id)))
        val configuracao = JSONObject(request("/PlanejamentoTurma/CarregarConfiguracoes", mapOf(
            "gradeEscolarId" to gradeId, "turmaId" to t.id, "tipoDeRegistroPedagogicoId" to tipo, "componentesAssociados" to "[]")))
        check(configuracao.has("exibirPeriodicidade") && !configuracao.getBoolean("exibirPeriodicidade")) {
            "A periodicidade precisa ser definida pelo professor"
        }
        val periodicidade = form.selectFirst("#TipoPeriodicidade")?.attr("value")
            ?.takeIf { it.isNotBlank() } ?: error("Periodicidade não identificada")
        val periodoJson = JSONObject(request("/PlanejamentoTurma/CarreguePeriodos", mapOf(
            "GradeEscolarId" to gradeId, "Periodicidade" to "", "TurmaId" to t.id, "TipoDeRegistroPedagogicoId" to tipo)))
        val periodos = periodoJson.getJSONArray("periodos")
        val calendario = Jsoup.parse(conteudosHtml[t.id + "/" + t.disciplina] ?: error("Calendário de aulas não verificado"))
        val mesesComAula = SgeParsing.mesesComAula(calendario)
        val limites = JSONObject(request("/NotasFaltas/ObtenhaInicioFimParametros", mapOf("turmaId" to t.id, "bimestre" to "", "mes" to "")))
        val formato = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        val inicioLetivo = LocalDate.parse(limites.getString("dataInicio"), formato)
        val fimLetivo = LocalDate.parse(limites.getString("dataFim"), formato)
        val hoje = LocalDate.now()
        val tabela = lista.selectFirst("#tabela_planejamentos") ?: error("Listagem ausente")
        val headers = tabela.select("thead th").map { it.text().trim() }
        val indicePeriodo = headers.indexOfFirst { it.equals("Período", true) }
        check(indicePeriodo >= 0)
        val periodosExistentes = tabela.select("tbody tr").mapNotNull {
            it.select("td").getOrNull(indicePeriodo)?.text()?.trim()?.lowercase()
        }.toSet()
        val pei = form.selectFirst("#AlunosTurmaId") != null
        // Read existing PEI assignments once, to avoid counting a student already covered.
        val atribuicoes = mutableMapOf<String, MutableSet<String>>()
        if (pei) {
            val ids = tabela.select(".checkbox_item[data-id]").map { it.attr("data-id") }.distinct()
            val editLinks = tabela.select("[onclick]").mapNotNull {
                Regex("carregarPaginaEditarPlanejamento\\((\\d+)\\)").find(it.attr("onclick"))?.groupValues?.get(1)
            }.distinct()
            for (id in (ids + editLinks).distinct()) {
                val registro = Jsoup.parse(request("/PlanejamentoTurma/Editar", mapOf("id" to id, "acessoPelaTurma" to "true", "tipoDeTurma" to "0")))
                val alunos = registro.select("#AlunosTurmaId option[selected]").map { it.attr("value") }.filter { it.isNotBlank() }
                val periods = registro.select("#Periodo option[selected]").map { it.attr("value") }.ifEmpty {
                    registro.select("input#Periodo[value]").map { it.attr("value") }.filter { it.isNotBlank() }
                }
                check(alunos.isNotEmpty() && periods.isNotEmpty()) { "Vínculos do PEI não identificados" }
                for (p in periods) atribuicoes.getOrPut(p) { mutableSetOf() }.addAll(alunos)
            }
        }
        var ausentes = 0
        for (i in 0 until periodos.length()) {
            val periodo = periodos.getJSONObject(i)
            val codigo = periodo.get("Codigo").toString()
            val descricao = periodo.getString("Descricao")
            val janela = SgeParsing.janela(descricao, codigo, inicioLetivo.year)
            if (janela.first.isAfter(hoje) || janela.first.isAfter(fimLetivo) || janela.second.isBefore(inicioLetivo)) continue
            val temAula = mesesComAula.any { month -> month >= YearMonth.from(janela.first) &&
                month <= YearMonth.from(janela.second) && month <= YearMonth.from(hoje) }
            if (!temAula) continue
            if (!pei) {
                if (descricao.trim().lowercase() !in periodosExistentes) ausentes++
            } else {
                val elegiveis = JSONObject(request("/PlanejamentoTurma/ObtenhaAlunosElegiveis", mapOf(
                    "planejamentoId" to "0", "turmaId" to t.id, "tipoDeRegistroPedagogicoId" to tipo,
                    "gradeEscolarId" to gradeId, "periodicidade" to periodicidade, "periodos" to codigo))).getJSONArray("alunos")
                val alunos = (0 until elegiveis.length()).map { elegiveis.getJSONObject(it).get("Codigo").toString() }.toSet()
                ausentes += (alunos - (atribuicoes[codigo] ?: emptySet())).size
            }
        }
        return ausentes
    }
}

object SgeParsing {
    private val dateRegex = Regex("\\d{2}/\\d{2}/\\d{4}")
    private val format = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    private fun date(title: String) = LocalDate.parse(dateRegex.find(title)?.value ?: error("Data ausente"), format)
    private val meses = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
    fun mesesComAula(doc: Document): Set<YearMonth> {
        val cells = doc.select(".calendario .semana [data-original-title], .calendario .semana [title]")
        check(cells.isNotEmpty()) { "Datas do calendário ausentes" }
        val ano = date(cells.first()!!.attr("data-original-title").ifBlank { cells.first()!!.attr("title") }).year
        return doc.select(".calendario .mes").mapNotNull { month ->
            val numero = meses.indexOf(month.select(".mes-titulo").text().trim().lowercase()) + 1
            val total = Regex("(\\d+)\\s+de\\s+(\\d+)\\s+registrados", RegexOption.IGNORE_CASE)
                .find(month.select(".mes-subtitulo").text())?.groupValues?.get(2)?.toIntOrNull()
            if (numero in 1..12 && total != null && total > 0) YearMonth.of(ano, numero) else null
        }.toSet()
    }
    fun janela(descricao: String, codigo: String, ano: Int): Pair<LocalDate, LocalDate> {
        val texto = descricao.trim().lowercase()
        val mes = meses.indexOfFirst { texto.startsWith(it + " de ") } + 1
        if (mes in 1..12) {
            val y = Regex("\\b(20\\d{2})\\b").find(texto)?.value?.toInt() ?: ano
            val ym = YearMonth.of(y, mes)
            return ym.atDay(1) to ym.atEndOfMonth()
        }
        if (texto == "primeiro semestre") return LocalDate.of(ano, 1, 1) to LocalDate.of(ano, 6, 30)
        if (texto == "segundo semestre") return LocalDate.of(ano, 7, 1) to LocalDate.of(ano, 12, 31)
        val range = dateRegex.findAll(descricao).map { LocalDate.parse(it.value, format) }.toList()
        if (range.size == 2) return range[0] to range[1]
        error("Datas do período $codigo não identificadas")
    }
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

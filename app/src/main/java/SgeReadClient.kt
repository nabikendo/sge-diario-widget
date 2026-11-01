package br.to.seduc.sgediariowidget.data

import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import org.jsoup.nodes.Document

data class TurmaMonitorada(val id: String, val nome: String, val escola: String, val disciplina: String)

class SessaoExpirada : IllegalStateException("Sessão expirada. Entre novamente no SGE.")

/** Only the ListarFrequencia search uses POST; no save endpoint is called. */
class SgeReadClient(cookies: String, private val userAgent: String, private val contextoSalvo: String? = null, private val onCookie: (String) -> Unit) {
    private val jar = cookies.split(';').mapNotNull {
        val p = it.trim().split('=', limit = 2)
        if (p.size == 2) p[0] to p[1] else null
    }.toMap().toMutableMap()
    private val grades = mutableMapOf<String, String>()
    private val conteudosHtml = mutableMapOf<String, String>()
    val historico = mutableListOf<String>()
    private val base = "https://sge.seduc.to.pontoid.com.br"
    private fun request(path: String, params: Map<String, String> = emptyMap(), post: Boolean = false, ajax: Boolean = true): String {
        val response = try {
            Jsoup.connect(base + path).cookies(jar).apply { if (ajax) header("X-Requested-With", "XMLHttpRequest") }
            .userAgent(userAgent).data(params).timeout(30000).ignoreContentType(true).maxBodySize(12 * 1024 * 1024)
            .method(if (post) org.jsoup.Connection.Method.POST else org.jsoup.Connection.Method.GET).execute()
        } catch (e: Exception) {
            historico.add("$path -> falha de rede: ${e.javaClass.simpleName}")
            throw IllegalStateException("Falha de rede em $path: ${e.message ?: e.javaClass.simpleName}", e)
        }
        jar.putAll(response.cookies())
        // CookieManager.setCookie expects the cookie pair, not the complete
        // Set-Cookie header (which also contains Path/Expires/SameSite).
        // Passing the header verbatim makes refreshed SGE sessions unreliable
        // on Android WebView.
        response.cookies().forEach { (name, value) -> onCookie("$name=$value") }
        registrar(path, response)
        if (response.url().path.contains("/Acesso/Entrar", true)) throw SessaoExpirada()
        val body = response.body()
        if (body.contains("id=\"login-form\"") || body.contains("id=\"btn-entrar\"")) throw SessaoExpirada()
        if (response.statusCode() !in 200..299) throw IllegalStateException("SGE respondeu HTTP ${response.statusCode()} em $path")
        if (body.isBlank()) throw IllegalStateException("SGE retornou resposta vazia em $path")
        return body
    }
    private fun registrar(path: String, r: org.jsoup.Connection.Response) {
        val b = runCatching { r.body() }.getOrDefault("")
        historico.add("$path -> HTTP ${r.statusCode()} final=${r.url().path} ${b.length}B ${SgeDiag.estrutura(b)}".take(900))
        if (historico.size > 40) historico.removeAt(0)
    }

    /** Perfil ativo na sessão: true = Unidade escolar (coordenação); false = Servidor (professor). */
    fun ehPerfilEscola(): Boolean {
        val resposta = request("/horariodeauladoprofessor")
        val html = runCatching { JSONObject(resposta) }.getOrNull()?.optString("html")?.takeIf { it.isNotBlank() } ?: resposta
        val d = Jsoup.parse(html)
        fun v(id: String) = d.selectFirst("#$id")?.attr("value")?.trim()?.lowercase()
        val esc = v("ehPerfilEscola"); val prof = v("ehPerfilProfessor"); val n = d.select("#ProfessorId option").size
        historico.add("Perfil: ehPerfilEscola=$esc ehPerfilProfessor=$prof professores=$n")
        return when { esc == "true" -> true; prof == "true" -> false; else -> n > 3 }
    }
    /** Coordenação: aulas sem registro (ícone +) de uma turma, em todos os componentes. */
    fun pendenciasConteudoTurma(turmaId: String, tipo: String): List<PendenciaDia> {
        val j = JSONObject(request("/Conteudo/CarregarDadosConteudoAplicado", mapOf(
            "TurmaId" to turmaId, "TipoDeTurma" to tipo.ifBlank { "0" }, "GradeEscolarId" to "",
            "StatusConteudoAplicado" to "", "DataInicio" to "", "DataFinal" to "")))
        return SgeParsing.pendenciasCoordenacao(j.getString("html"), LocalDate.now())
    }

    fun turmas(): List<TurmaMonitorada> {
        val home = Jsoup.parse(request("/", ajax = false))
        // O SGE carrega esta tela em #content por AJAX. O GET direto da rota
        // retorna a página de entrada sem o formulário/identidade do professor.
        // The portal loads this route with jQuery .load(), which normally
        // returns a raw HTML fragment. Some deployments wrap fragments in JSON.
        val horarioResposta = request("/horariodeauladoprofessor")
        val horarioJson = runCatching { JSONObject(horarioResposta) }.getOrNull()
        val horarioHtml = horarioJson?.optString("html")?.takeIf { it.isNotBlank() }
            ?: horarioJson?.optString("data")?.takeIf { it.isNotBlank() }
            ?: horarioResposta
        val horario = Jsoup.parse(horarioHtml)
        check(horario.selectFirst("#idProfessor") != null || horario.selectFirst("#ProfessorId") != null) {
            "A página de horário não retornou o formulário esperado (resposta AJAX do SGE)."
        }
        val professor = horario.selectFirst("#idProfessor")?.let { it.attr("value").ifBlank { it.attr("data-id") } }
            ?.takeIf { it.isNotBlank() }
            ?: horario.selectFirst("#ProfessorId")?.let { el ->
                el.attr("value").ifBlank { el.selectFirst("option[selected]")?.attr("value")
                ?: el.selectFirst("option[value]:not([value=''])")?.attr("value") ?: "" }
            }?.takeIf { it.isNotBlank() }
            ?: horario.select("[name='colaboradorId'], [name='ColaboradorId']").firstOrNull()?.attr("value")
                ?.takeIf { it.isNotBlank() }
            ?: error("O SGE não informou o identificador do professor no horário.")
        val tipo = horario.selectFirst("#Tipo")?.let { el ->
            el.attr("value").ifBlank { el.selectFirst("option[selected]")?.attr("value")
                ?: el.selectFirst("option[value]:not([value=''])")?.attr("value") ?: "" }
        }?.takeIf { it.isNotBlank() }
            ?: horario.select("[name='Tipo'] option[selected]").firstOrNull()?.attr("value")?.takeIf { it.isNotBlank() }
            ?: error("O SGE não informou o tipo de horário selecionado.")
        val json = JSONObject(request("/HorarioDeAulaDoProfessor/QuadroHorarioProfessor", mapOf(
            "dataReferencia" to LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),
            "tipo" to tipo, "colaboradorId" to professor, "escolaId" to "", "turno" to "", "somenteConflitos" to "false")))
        val doc = Jsoup.parse(json.getString("html"))
        fun norm(x: String) = x.replace('\u00a0', ' ').trim().replace(Regex("\\s+"), " ").lowercase()
        data class Op(val valor: String, val desc: String, val escola: String, val tipo: String)
        val doHtml = home.select("#TurmaContextoId option").asSequence().filter { it.attr("value").isNotBlank() }.map {
            Op(it.attr("value"), norm(it.attr("data-turmadescricao").ifBlank { it.text() }),
                it.attr("data-escoladescricao"), it.attr("data-tipodaturmaenum"))
        }.toList()
        // A lista de turmas pode ser montada por JavaScript; nesse caso o app a lê do WebView logado.
        val doApp = if (doHtml.isNotEmpty()) emptyList() else runCatching {
            val a = JSONArray(contextoSalvo ?: "[]")
            (0 until a.length()).map { i -> val o = a.getJSONObject(i); Op(o.getString("v"), norm(o.getString("d")), o.optString("e"), o.optString("t")) }
        }.getOrDefault(emptyList())
        val opcoes = doHtml.ifEmpty { doApp }
        historico.add("Turmas disponíveis: html=${doHtml.size}, lidas do WebView=${doApp.size}; amostra: " +
            opcoes.take(5).joinToString(" ") { "[v=${it.valor} t=${it.tipo} d=${it.desc.take(25)}]" })
        check(opcoes.isNotEmpty()) {
            "O SGE não listou as turmas na página inicial. Abra a aba SGE Web, espere a página inicial carregar e toque em Sincronizar agora."
        }
        val nomesQuadro = doc.select("tr").mapNotNull { row ->
            val c = row.select("td"); if (c.size == 3 && c[0].text().contains("às")) c[2].text().trim() else null
        }.distinct()
        historico.add("Turmas no quadro de horário: " + nomesQuadro.joinToString(" | "))
        val found = doc.select("tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size != 3 || !cells[0].text().contains("às")) null else {
                val nome = cells[2].text().trim()
                val alvo = norm(nome)
                val exatas = opcoes.filter { it.desc == alvo }
                val matches = exatas.ifEmpty { opcoes.filter { it.desc.isNotEmpty() && (it.desc.startsWith(alvo) || alvo.startsWith(it.desc)) } }
                val escolarizacao = matches.filter { it.tipo == "0" || it.tipo.isBlank() }
                if (matches.isEmpty()) {
                    throw IllegalStateException("Turma \"$nome\" do quadro não encontrada na lista de turmas do SGE")
                } else if (escolarizacao.isEmpty()) {
                    historico.add("Turma ignorada (tipo não suportado): " + matches.joinToString { "tipo=" + it.tipo })
                    null
                } else {
                    val ids = escolarizacao.map { it.valor.substringBefore('_') }.distinct()
                    check(ids.size == 1) { "Identificação ambígua da turma $nome: " + escolarizacao.joinToString { "valor=" + it.valor } }
                    TurmaMonitorada(ids.single(), nome, escolarizacao.first().escola, cells[1].text().trim())
                }
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
        val htmlFreq = response.optString("html")
        if (htmlFreq.isBlank()) {
            // Sem tabela: o SGE pode estar informando que não há registros, ou uma falha.
            val msg = listOf("mensagem", "Mensagem", "message", "erro").firstNotNullOfOrNull { k ->
                response.optString(k).takeIf { it.isNotBlank() }
            }
            val vazio = msg != null && Regex("nenhum|não há|nao ha|sem registro|não encontr", RegexOption.IGNORE_CASE).containsMatchIn(msg)
            if (vazio) return@consulta ResultadoModulo(0, "O SGE informou: ${msg!!.take(120)}", coberturaCompleta = true)
            error("O SGE respondeu sem a tabela de frequência (campos: ${response.keys().asSequence().joinToString(",")}" +
                (msg?.let { "; mensagem: ${it.take(120)}" } ?: "") + ")")
        }
        SgeParsing.frequencia(htmlFreq, fim)
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
    private val curto = DateTimeFormatter.ofPattern("dd/MM")
    private fun listaDatas(l: List<LocalDate>) =
        if (l.isEmpty()) "." else ": " + l.take(15).joinToString(", ") { it.format(curto) } + if (l.size > 15) "…" else ""
    fun conteudos(html: String, disciplina: String, hoje: LocalDate): ResultadoModulo {
        val doc = Jsoup.parse(html)
        check(doc.select(".calendario .semana").isNotEmpty()) { "Calendário ausente" }
        val pendentes = doc.select(".calendario .semana .vermelho").mapNotNull { day ->
            val d = date(day.attr("data-original-title").ifBlank { day.attr("title") })
            val popup = Jsoup.parse(day.attr("data-content"))
            check(popup.select("tr td:first-child").isNotEmpty()) { "Componente ausente no detalhe" }
            val linhas = popup.select("tr").asSequence()
                .filter { it.selectFirst("td")?.text()?.trim().equals(disciplina, true) }.toList()
            if (d.isAfter(hoje) || linhas.isEmpty()) null else {
                // Na janela do dia, o ícone "+" é aula sem registro e o ícone de editar é aula já lançada.
                val temIcones = popup.select("i.fa").isNotEmpty()
                if (!temIcones || linhas.any { it.select("i.fa-plus-circle, i.fa-plus").isNotEmpty() }) d else null
            }
        }.distinct().sorted()
        return ResultadoModulo(pendentes.size,
            "Dias pendentes no mapa do SGE até ${hoje.format(format)}" + listaDatas(pendentes), coberturaCompleta = true)
    }
    fun pendenciasCoordenacao(html: String, hoje: LocalDate): List<PendenciaDia> {
        val doc = Jsoup.parse(html)
        check(doc.select(".calendario .semana").isNotEmpty()) { "Calendário ausente" }
        return doc.select(".calendario .semana .vermelho").flatMap { day ->
            val d = date(day.attr("data-original-title").ifBlank { day.attr("title") })
            if (d.isAfter(hoje)) emptyList() else Jsoup.parse(day.attr("data-content")).select("tr").mapNotNull { tr ->
                val tds = tr.select("td")
                val disc = tds.firstOrNull()?.text()?.trim().orEmpty()
                if (disc.isNotEmpty() && tr.select("i.fa-plus-circle, i.fa-plus").isNotEmpty())
                    PendenciaDia(d.toString(), disc, tds.getOrNull(1)?.text()?.trim().orEmpty()) else null
            }
        }.distinct().sortedWith(compareBy({ it.data }, { it.hora }))
    }

    fun frequencia(html: String, ate: LocalDate): ResultadoModulo {
        val table = Jsoup.parse(html).selectFirst("#gridFrequencia") ?: error("Grade de frequência ausente")
        val dates = table.select("thead .cabecalhoMes").map { date(it.attr("title")) }
            .filter { !it.isAfter(ate) }.distinct().sorted()
        return ResultadoModulo(dates.size,
            "Dias do filtro oficial Somente Frequência Não Concluída até ${ate.format(format)}" + listaDatas(dates), coberturaCompleta = true)
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

package br.to.seduc.sgediariowidget.data

import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import org.jsoup.nodes.Document

data class TurmaMonitorada(val id: String, val nome: String, val escola: String, val disciplina: String, val tipo: String = "")

class SessaoExpirada : IllegalStateException("Sessão expirada. Entre novamente no SGE.")

/** Only the ListarFrequencia search uses POST; no save endpoint is called. */
class SgeReadClient(cookies: String, private val userAgent: String, private val contextoSalvo: String? = null, private val onCookie: (String) -> Unit) {
    var perfilObservado: PerfilSge = PerfilSge.DESCONHECIDO
        private set
    private val jar = cookies.split(';').mapNotNull {
        val p = it.trim().split('=', limit = 2)
        if (p.size == 2) p[0] to p[1] else null
    }.toMap().toMutableMap()
    private val grades = mutableMapOf<String, String>()
    private val conteudosHtml = mutableMapOf<String, String>()
    val historico = mutableListOf<String>()
    private val base = "https://sge.seduc.to.pontoid.com.br"
    /** As consultas só leem dados; repetir é seguro. Cobre DNS/rede instáveis de madrugada ("Unable to resolve host"). */
    private fun <T> comRetentativa(bloco: () -> T): T {
        val esperas = longArrayOf(1_500, 4_000)
        var i = 0
        while (true) {
            try { return bloco() }
            catch (e: java.io.IOException) {
                val transitoria = e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException || e is java.net.NoRouteToHostException
                if (!transitoria || i >= esperas.size) throw e
                historico.add("tentativa ${i + 2}: ${e.javaClass.simpleName}")
                Thread.sleep(esperas[i++])
            }
        }
    }
    private fun request(path: String, params: Map<String, String> = emptyMap(), post: Boolean = false, ajax: Boolean = true): String {
        val response = try { comRetentativa {
            Jsoup.connect(base + path).cookies(jar).apply { if (ajax) header("X-Requested-With", "XMLHttpRequest") }
            .userAgent(userAgent).data(params).timeout(30000).ignoreContentType(true).maxBodySize(12 * 1024 * 1024)
            .method(if (post) org.jsoup.Connection.Method.POST else org.jsoup.Connection.Method.GET).execute() }
        } catch (e: Exception) {
            historico.add("$path -> falha de rede: ${e.javaClass.simpleName}")
            throw IllegalStateException("Falha de rede em $path: ${e.message ?: e.javaClass.simpleName}", e)
        }
        jar.putAll(response.cookies())
        // Mantém Path, Expires, HttpOnly, Secure e SameSite ao devolver cookies ao WebView.
        response.headers("Set-Cookie").forEach(onCookie)
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
        perfilObservado = SgePerfil.ler(html)
        val d = Jsoup.parse(html)
        fun v(id: String) = d.selectFirst("#$id")?.attr("value")?.trim()?.lowercase()
        val esc = v("ehPerfilEscola"); val prof = v("ehPerfilProfessor"); val n = d.select("#ProfessorId option").size
        historico.add("Perfil: ehPerfilEscola=$esc ehPerfilProfessor=$prof professores=$n")
        return when (perfilObservado) { PerfilSge.COORDENACAO -> true; PerfilSge.PROFESSOR -> false; else -> error("Perfil do SGE não confirmado. Abra o horário no SGE Web e tente novamente.") }
    }
    /** Coordenação: aulas sem registro (ícone +) de uma turma, em todos os componentes. */
    fun pendenciasConteudoTurma(turmaId: String, tipo: String): List<PendenciaDia> {
        val j = JSONObject(request("/Conteudo/CarregarDadosConteudoAplicado", mapOf(
            "TurmaId" to turmaId, "TipoDeTurma" to tipo.ifBlank { "0" }, "GradeEscolarId" to "",
            "StatusConteudoAplicado" to "", "DataInicio" to "", "DataFinal" to "")))
        return SgeParsing.pendenciasCoordenacao(j.getString("html"), LocalDate.now())
    }

    /** Componentes curriculares da turma (o SGE lista só os que ela tem). */
    fun disciplinasDaTurma(turmaId: String): List<String> {
        val a = JSONObject(request("/PlanejamentoTurma/CarregueGradesEscolares", mapOf("turmaId" to turmaId))).getJSONArray("grades")
        return (0 until a.length()).map { a.getJSONObject(it).optString("Descricao").trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    private fun horarioHtml(): String {
        val resposta = request("/horariodeauladoprofessor")
        val json = runCatching { JSONObject(resposta) }.getOrNull()
        return json?.optString("html")?.takeIf { it.isNotBlank() }
            ?: json?.optString("data")?.takeIf { it.isNotBlank() } ?: resposta
    }

    fun professoresDaEscola(): List<DocenteRef> = SgeDocentesParsing.professores(horarioHtml()).also {
        check(it.isNotEmpty()) { "O SGE não retornou a lista de professores do perfil Unidade escolar." }
    }

    fun vinculosDocente(professor: DocenteRef, turmas: List<Pair<String, String>>): List<VinculoDocente> =
        analisarVinculosDocente(professor, turmas).vinculos

    fun analisarVinculosDocente(professor: DocenteRef, turmas: List<Pair<String, String>>): ResultadoVinculosDocente {
        val form = horarioHtml()
        val doc = Jsoup.parse(form)
        val escola = doc.selectFirst("#EscolaId")?.let { el ->
            el.attr("value").ifBlank { el.selectFirst("option[selected]")?.attr("value").orEmpty() }
        }.orEmpty()
        val json = JSONObject(request("/HorarioDeAulaDoProfessor/QuadroHorarioProfessor", mapOf(
            "dataReferencia" to LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),
            "tipo" to SgeDocentesParsing.tipoHorario(form), "colaboradorId" to professor.id,
            "escolaId" to escola, "turno" to "", "somenteConflitos" to "false")))
        return SgeDocentesParsing.analisarVinculos(json.getString("html"), professor, turmas)
    }

    fun turmas(incluir: Set<String> = emptySet()): List<TurmaMonitorada> {
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
                // Categoria pelo nome: 1–2 dígitos = Escolarização; 3 = Diversificada; 4 = AEE.
                val categoria = categoriaTurma(nome)
                val escolarizacao = matches.filter { it.tipo == "0" || it.tipo.isBlank() }
                // Padrão: só Escolarização. Diversificada e AEE entram só quando o usuário ativa a categoria em Ajustes.
                val escolhidas = when {
                    categoria == "ESCOLARIZACAO" -> escolarizacao
                    categoria in incluir -> matches
                    else -> emptyList()
                }
                if (matches.isEmpty()) {
                    throw IllegalStateException("Turma \"$nome\" do quadro não encontrada na lista de turmas do SGE")
                } else if (escolhidas.isEmpty()) {
                    historico.add("Turma ignorada (tipo não suportado): " + matches.joinToString { "tipo=" + it.tipo })
                    null
                } else {
                    val ids = escolhidas.map { it.valor.substringBefore('_') }.distinct()
                    check(ids.size == 1) { "Identificação ambígua da turma $nome: " + escolhidas.joinToString { "valor=" + it.valor } }
                    TurmaMonitorada(ids.single(), nome, escolhidas.first().escola, cells[1].text().trim(), escolhidas.first().tipo)
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
        SgeParsing.conteudos(html, t.disciplina, LocalDate.now(), janelaDoFiltro(t.id))
    }
    fun frequencia(t: TurmaMonitorada): ResultadoModulo = consulta {
        val j = JSONObject(request("/NotasFaltas/ObtenhaInicioFimParametros", mapOf("turmaId" to t.id, "bimestre" to "", "mes" to "")))
        val format = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        val inicio = LocalDate.parse(j.getString("dataInicio"), format)
        val fim = minOf(LocalDate.now(), LocalDate.parse(j.getString("dataFim"), format))
        if (fim < inicio) return@consulta ResultadoModulo(0, "Período letivo ainda não iniciado.", coberturaCompleta = true)
        // Recorte pelo bimestre escolhido (ou o atual): consulta só o intervalo dele.
        val janela = janelaDoFiltro(t.id)
        val ini = janela?.let { maxOf(it.first, inicio) } ?: inicio
        val fimJanela = janela?.let { minOf(it.second, fim) } ?: fim
        if (fimJanela < ini) return@consulta ResultadoModulo(0, "Ainda não há aulas neste bimestre até hoje.", coberturaCompleta = true)
        val response = JSONObject(request("/NotasFaltas/ListarFrequencia", mapOf(
            "turmaId" to t.id, "gradeEscolarId" to grade(t), "bimestre" to "", "mes" to "",
            "dataInicial" to ini.format(format), "dataFinal" to fimJanela.format(format), "frequenciaNaoConcluida" to "true"), true))
        val htmlFreq = response.optString("html")
        if (htmlFreq.isBlank()) {
            // Sem tabela: o SGE pode estar informando que não há registros, ou uma falha.
            val msg = listOf("mensagem", "Mensagem", "message", "erro").firstNotNullOfOrNull { k ->
                response.optString(k).takeIf { it.isNotBlank() }
            }
            val vazio = msg != null && Regex("nenhum|não há|nao ha|sem registro|não encontr|não (foram|foi) encontr", RegexOption.IGNORE_CASE).containsMatchIn(msg)
            if (vazio) return@consulta ResultadoModulo(0, "O SGE informou: ${msg!!.take(120)}", coberturaCompleta = true)
            error("O SGE respondeu sem a tabela de frequência (campos: ${response.keys().asSequence().joinToString(",")}" +
                (msg?.let { "; mensagem: ${it.take(120)}" } ?: "") + ")")
        }
        SgeParsing.frequencia(htmlFreq, fimJanela)
    }
    fun planos(t: TurmaMonitorada, tipoNome: String): ResultadoModulo = consulta {
        val page = Jsoup.parse(request("/planejamentoturma", mapOf("turmaId" to t.id)))
        val opcoesTipo = page.select("#TipoDeRegistroPedagogicoId option")
        // Nome exato primeiro; depois prefixo (o SGE mostra "PEI - Plano de Ensino Individualizado").
        val tipo = (opcoesTipo.singleOrNull { it.text().trim().equals(tipoNome, true) }
            ?: opcoesTipo.singleOrNull { it.text().trim().startsWith(tipoNome, true) })?.attr("value") ?: return@consulta ResultadoModulo(null, "Tipo de planejamento não encontrado no seletor do SGE: $tipoNome. Confira a turma e o diagnóstico.", coberturaCompleta = false)
        val response = JSONObject(request("/PlanejamentoTurma/PartialPlanejamentos", mapOf(
            "TurmaId" to t.id, "TipoDeRegistroPedagogicoId" to tipo, "GradeEscolarId" to grade(t),
            "AtividadeDeAEEId" to "", "ModuloUnidadeCurricularId" to "", "Situacao" to "", "Periodos" to "[]")))
        val html = response.getString("html")
        val existentes = SgeParsing.planos(html)
        try {
            val (cobertura, semDatas) = ausentes(t, tipo, Jsoup.parse(html))
            existentes.copy(quantidade = existentes.quantidade!! + cobertura,
                detalhe = "$cobertura documentos ausentes; ${existentes.quantidade} não enviados/reprovados; " +
                    "${existentes.aguardando} aguardando avaliação; ${existentes.aprovados} aprovados. " +
                    (if (semDatas.isEmpty()) "Cobertura dos períodos iniciados, conforme configuração e elegibilidade do SGE."
                     else "Períodos sem datas identificadas e não contados: ${semDatas.take(4).joinToString(", ")}. Valor parcial."),
                coberturaCompleta = semDatas.isEmpty())
        } catch (e: SessaoExpirada) { throw e }
        catch (e: Exception) {
            val causa = e.message?.replace(Regex("\\s+"), " ")?.take(150) ?: e.javaClass.simpleName
            existentes.copy(detalhe = existentes.detalhe + " Cobertura não verificada: $causa")
        }
    }

    private fun ausentes(t: TurmaMonitorada, tipo: String, lista: Document): Pair<Int, List<String>> {
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
        val semDatas = mutableListOf<String>()
        for (i in 0 until periodos.length()) {
            val periodo = periodos.getJSONObject(i)
            val codigo = periodo.get("Codigo").toString()
            val descricao = periodo.getString("Descricao")
            val janela = SgeParsing.janelaOuNull(descricao, codigo, inicioLetivo.year) ?: janelaBimestre(descricao, t.id)
            if (janela == null) { semDatas.add("${descricao.trim().take(30)} (cód. $codigo)"); continue }
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
        if (semDatas.isNotEmpty()) historico.add("Períodos sem datas (${t.disciplina}): " + semDatas.joinToString(" | "))
        return ausentes to semDatas
    }
    private var bimestreZero: Boolean? = null
    /** 0 = bimestre atual (pela data de hoje); 1 a 4 = bimestre escolhido pelo usuário. */
    var bimestreFiltro: Int = 0
    private val janelasBim = mutableMapOf<String, Pair<LocalDate, LocalDate>?>()
    /** Datas do bimestre do filtro, lidas do próprio SGE. Null = sem recorte (ano inteiro). */
    fun janelaDoFiltro(turmaId: String): Pair<LocalDate, LocalDate>? {
        if (bimestreFiltro == 5) return null
        val hoje = LocalDate.now()
        val zero = bimestreZero ?: (janelaDoBimestre("0", turmaId) != null).also { bimestreZero = it }
        fun janelaN(n: Int) = janelasBim.getOrPut("$turmaId|$n") { janelaDoBimestre((if (zero) n - 1 else n).toString(), turmaId) }
        if (bimestreFiltro in 1..4) return janelaN(bimestreFiltro) ?: error("Não encontrei as datas do ${bimestreFiltro}º bimestre no SGE")
        return (1..4).asSequence().map { janelaN(it) }.firstOrNull { it != null && !hoje.isBefore(it.first) && !hoje.isAfter(it.second) }
    }
    private fun janelaDoBimestre(param: String, turmaId: String): Pair<LocalDate, LocalDate>? = runCatching {
        val j = JSONObject(request("/NotasFaltas/ObtenhaInicioFimParametros", mapOf("turmaId" to turmaId, "bimestre" to param, "mes" to "")))
        val f = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        val a = LocalDate.parse(j.getString("dataInicio"), f); val b = LocalDate.parse(j.getString("dataFim"), f)
        if (!b.isBefore(a) && java.time.temporal.ChronoUnit.DAYS.between(a, b) in 30..140) a to b else null
    }.getOrNull()
    /** "Primeiro Bimestre" ou "1º bimestre": pede ao próprio SGE as datas do bimestre (numeração detectada uma vez). */
    private fun janelaBimestre(descricao: String, turmaId: String): Pair<LocalDate, LocalDate>? {
        val n = SgeParsing.numeroBimestre(descricao) ?: return null
        if (n !in 1..4) return null
        val zero = bimestreZero ?: (janelaDoBimestre("0", turmaId) != null).also { bimestreZero = it }
        return janelaDoBimestre((if (zero) n - 1 else n).toString(), turmaId)
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
        val blocosMes = doc.select(".calendario .mes")
        check(blocosMes.isNotEmpty()) { "Meses do calendário ausentes" }
        val resultado = mutableSetOf<YearMonth>()
        for (month in blocosMes) {
            val numero = meses.indexOf(month.select(".mes-titulo").text().trim().lowercase()) + 1
            check(numero in 1..12) { "Mês do calendário não reconhecido" }
            val resumo = month.selectFirst(".mes-subtitulo")?.text()?.trim().orEmpty()
            val total = Regex("(\\d+)\\s+de\\s+(\\d+)\\s+registrados", RegexOption.IGNORE_CASE)
                .find(resumo)?.groupValues?.get(2)?.toIntOrNull()
            when {
                total != null -> if (total > 0) resultado += YearMonth.of(ano, numero)
                resumo.contains("nenhum registro a ser feito", ignoreCase = true) ||
                    resumo.contains("sem dias letivos", ignoreCase = true) -> Unit
                else -> error("Resumo mensal do calendário não reconhecido")
            }
        }
        return resultado
    }
    private val ordinais = mapOf("primeiro" to 1, "segundo" to 2, "terceiro" to 3, "quarto" to 4, "i" to 1, "ii" to 2, "iii" to 3, "iv" to 4)
    private val bimNumeroAntes = Regex("(?<![\\p{L}\\d])(\\d|primeiro|segundo|terceiro|quarto|iv|iii|ii|i)\\s*[º°ªo]?\\s*[-.]?\\s*(?:bimestre|bim)(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val bimNumeroDepois = Regex("(?<![\\p{L}])(?:bimestre|bim)\\.?\\s*[-:nº°.]*\\s*(\\d|primeiro|segundo|terceiro|quarto|iv|iii|ii|i)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
    /** Número (1 a 4) de um período descrito como "1º Bimestre", "Bimestre 1", "II Bim." etc.; null se não reconhecido. */
    fun numeroBimestre(descricao: String): Int? {
        val bruto = (bimNumeroAntes.find(descricao) ?: bimNumeroDepois.find(descricao))?.groupValues?.get(1)?.lowercase() ?: return null
        val n = bruto.toIntOrNull() ?: ordinais[bruto] ?: return null
        return n.takeIf { it in 1..4 }
    }
    private val abreviacoes = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    /** Identifica a janela de datas de um período do SGE; devolve null quando o formato não é reconhecido (nunca estima). */
    fun janelaOuNull(descricao: String, codigo: String, ano: Int): Pair<LocalDate, LocalDate>? {
        val texto = descricao.trim().lowercase()
        fun mesInteiro(y: Int, m: Int): Pair<LocalDate, LocalDate>? =
            runCatching { YearMonth.of(y, m).let { it.atDay(1) to it.atEndOfMonth() } }.getOrNull()
        val anoNoTexto = Regex("\\b(20\\d{2})\\b").find(texto)?.value?.toInt()
        val mes = meses.indexOfFirst { texto.startsWith(it) }.let { i -> if (i >= 0) i + 1 else abreviacoes.indexOfFirst { texto.startsWith(it) } + 1 }
        if (mes in 1..12) return mesInteiro(anoNoTexto ?: ano, mes)
        // "13ª Semana de 2026" (cód. 202613): semana ISO 8601 (segunda a domingo). Sem isso, o código 2026NN
        // das semanas 1 a 12 seria lido como mês e as semanas 13+ ficariam sem datas.
        Regex("(\\d{1,2})\\s*[ªº°a]?\\s*semana(?:\\s+de)?\\s*(20\\d{2})?").find(texto)?.let { s ->
            val semana = s.groupValues[1].toLong(); val anoSemana = s.groupValues[2].toIntOrNull() ?: anoNoTexto ?: ano
            return runCatching {
                val segunda = LocalDate.of(anoSemana, 1, 4).with(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR, semana)
                    .with(java.time.DayOfWeek.MONDAY)
                segunda to segunda.plusDays(6)
            }.getOrNull()
        }
        Regex("(1|2|primeiro|segundo)\\s*[º°o]?\\s*semestre").find(texto)?.let { s ->
            val primeiro = s.groupValues[1].let { it == "1" || it == "primeiro" }
            return if (primeiro) LocalDate.of(ano, 1, 1) to LocalDate.of(ano, 6, 30) else LocalDate.of(ano, 7, 1) to LocalDate.of(ano, 12, 31)
        }
        val completas = dateRegex.findAll(descricao).map { LocalDate.parse(it.value, format) }.toList()
        if (completas.size >= 2) return minOf(completas[0], completas[1]) to maxOf(completas[0], completas[1])
        if (completas.isEmpty()) {
            Regex("\\b(\\d{1,2})/(20\\d{2})\\b").find(descricao)?.let { return mesInteiro(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
            Regex("\\b(20\\d{2})-(\\d{2})\\b").find(descricao)?.let { return mesInteiro(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            Regex("^(20\\d{2})(0[1-9]|1[0-2])$").find(codigo.trim())?.let { return mesInteiro(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            val curtas = Regex("\\b(\\d{1,2})/(\\d{1,2})\\b").findAll(descricao).toList()
            if (curtas.size == 2) return runCatching {
                val a = LocalDate.of(ano, curtas[0].groupValues[2].toInt(), curtas[0].groupValues[1].toInt())
                var b = LocalDate.of(ano, curtas[1].groupValues[2].toInt(), curtas[1].groupValues[1].toInt())
                if (b.isBefore(a)) b = b.plusYears(1)
                a to b
            }.getOrNull()
        }
        return null
    }
    fun janela(descricao: String, codigo: String, ano: Int): Pair<LocalDate, LocalDate> =
        janelaOuNull(descricao, codigo, ano)
            ?: error("Datas do período $codigo não identificadas (descrição no SGE: \"${descricao.trim().take(40)}\")")
    private val curto = DateTimeFormatter.ofPattern("dd/MM")
    private fun listaDatas(l: List<LocalDate>) =
        if (l.isEmpty()) "." else ": " + l.take(15).joinToString(", ") { it.format(curto) } + if (l.size > 15) "…" else ""
    fun conteudos(html: String, disciplina: String, hojeReal: LocalDate, janela: Pair<LocalDate, LocalDate>? = null): ResultadoModulo {
        val hoje = janela?.let { minOf(it.second, hojeReal) } ?: hojeReal
        val desde = janela?.first ?: LocalDate.MIN
        val doc = Jsoup.parse(html)
        check(doc.select(".calendario .semana").isNotEmpty()) { "Calendário ausente" }
        val pendentes = doc.select(".calendario .semana .vermelho").mapNotNull { day ->
            val d = date(day.attr("data-original-title").ifBlank { day.attr("title") })
            val popup = Jsoup.parse(day.attr("data-content"))
            check(popup.select("tr td:first-child").isNotEmpty()) { "Componente ausente no detalhe" }
            val linhas = popup.select("tr").asSequence()
                .filter { it.selectFirst("td")?.text()?.trim().equals(disciplina, true) }.toList()
            if (d.isAfter(hoje) || d.isBefore(desde) || linhas.isEmpty()) null else {
                // Janela do dia: ícone de editar = aula já lançada. Com o filtro do componente, dia vermelho
                // com alguma aula da disciplina sem esse ícone = conteúdo ainda não registrado.
                if (linhas.any { it.select("i.fa-edit").isEmpty() }) d else null
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

/** Bimestre escolhido no Ajustes: 0 = atual (padrão), 1 a 4 = bimestre específico. */
object SgeBimestre {
    const val CHAVE = "bimestre_filtro"
    fun ler(context: android.content.Context): Int =
        context.getSharedPreferences(SgePainelStore.PREFS_NAME, android.content.Context.MODE_PRIVATE).getInt(CHAVE, 0).coerceIn(0, 5)
    fun rotulo(valor: Int): String = when (valor) { in 1..4 -> "$valor° bimestre"; 5 -> "todos os bimestres"; else -> "bimestre atual" }
    fun salvar(context: android.content.Context, valor: Int) {
        context.getSharedPreferences(SgePainelStore.PREFS_NAME, android.content.Context.MODE_PRIVATE).edit().putInt(CHAVE, valor.coerceIn(0, 5)).apply()
    }
}

/** Categoria da turma pelo nome: 3 dígitos antes do ponto = diversificada; 4 = AEE; demais = escolarização. */
fun categoriaTurma(nome: String): String {
    val digitos = Regex("^(\\d+)\\.").find(nome.trim())?.groupValues?.get(1)?.length ?: 0
    return when (digitos) { 3 -> "DIVERSIFICADA"; 4 -> "AEE"; else -> "ESCOLARIZACAO" }
}

/** Categorias incluídas na busca de turmas. Padrão: nenhuma (só Escolarização). */
object SgeEscopo {
    const val CHAVE_DIVERSIFICADA = "incluir_diversificada"
    const val CHAVE_AEE = "incluir_aee"
    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(SgePainelStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    fun incluidas(context: android.content.Context): Set<String> = buildSet {
        if (prefs(context).getBoolean(CHAVE_DIVERSIFICADA, false)) add("DIVERSIFICADA")
        if (prefs(context).getBoolean(CHAVE_AEE, false)) add("AEE")
    }
    fun ativa(context: android.content.Context, categoria: String): Boolean = categoria in incluidas(context)
    /** Turma pelo nome: entra nas listas de coordenação e de vínculos só se a categoria estiver permitida. */
    fun permitida(context: android.content.Context, nome: String): Boolean {
        val c = categoriaTurma(nome)
        return c == "ESCOLARIZACAO" || c in incluidas(context)
    }
    fun salvar(context: android.content.Context, categoria: String, valor: Boolean) {
        val chave = if (categoria == "AEE") CHAVE_AEE else CHAVE_DIVERSIFICADA
        prefs(context).edit().putBoolean(chave, valor).apply()
    }
}

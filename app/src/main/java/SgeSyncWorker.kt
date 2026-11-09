package br.to.seduc.sgediariowidget.worker

import android.content.Context
import android.webkit.CookieManager
import androidx.work.*
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.SgeWidgets
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

class SgeSyncWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private suspend fun mostrar(painel: PainelSge) {
        SgePainelStore.salvar(ctx, painel)
        SgeWidgets.atualizar(ctx)
    }
    override suspend fun doWork(): Result {
        val trava = if (inputData.getString("coord_alvo") == null) travaPessoal else travaCoord
        return withContext(SgeSessaoStore.contextoConsulta(ctx)) { trava.withLock { executar() } }
    }
    private suspend fun executar(): Result {
        val geracao = SgeSessaoStore.geracao(ctx)
        val antigo = SgePainelStore.ler(ctx)
        val alvo = inputData.getString("coord_alvo")
        val coordenacao = alvo != null
        val rodada = inputData.getString("coord_rodada")
        val filaInicial = if (coordenacao) SgeFila.ler(ctx) else null
        val bimestre = filaInicial?.bimestre?.takeIf { it >= 0 } ?: SgeBimestre.ler(ctx)
        if (filaInicial?.pausada == true || rodada != null && filaInicial?.let { it.rodada == rodada && alvo != null && alvo in it.ids } != true)
            return Result.success()
        val manual = if (coordenacao) !inputData.getBoolean("auto", false) else inputData.getBoolean("manual", false)
        val notificacao = if (manual) SgeProgressoNotificacao(ctx, id.toString(), coordenacao) else null
        val inicioWorker = System.currentTimeMillis()
        val retomando = inputData.getBoolean("retomar", false) || runAttemptCount > 0
        if (!coordenacao && !retomando) {
            // A pausa pedida pelo usuário vale também para consultas automáticas; atualizar manualmente recomeça do zero.
            if (!inputData.getBoolean("manual", false) && SgePausaPessoal.ler(ctx)?.pausada == true) return Result.success()
            SgePausaPessoal.limpar(ctx)
        }
        if (!coordenacao) mostrar(antigo.copy(sincronizando = true, inicioConsulta = System.currentTimeMillis(), mensagem = "Consultando o horário do professor…"))
        var cliente: SgeReadClient? = null
        var cancelado = false
        var bloqueada = false
        val updatedCookies = mutableListOf<String>()
        try {
            notificacao?.mostrar(filaInicial?.resolvidas ?: 0, filaInicial?.total ?: 0)
            val cookies = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(BASE) }
            if (cookies.isNullOrBlank()) throw SessaoExpirada()
            val agent = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getString("web_user_agent", null) ?: error("Abra o SGE no aplicativo antes de sincronizar.")
            val client = SgeReadClient(cookies, agent, ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getString("turmas_contexto", null)) { updatedCookies.add(it) }
            client.bimestreFiltro = bimestre
            cliente = client
            // Dois perfis no SGE: Servidor (professor) e Unidade escolar (coordenação). Cada um tem seu painel.
            val escola = withTimeout(30_000L) { withContext(Dispatchers.IO) { client.ehPerfilEscola() } }
            SgeSessaoStore.salvar(ctx, client.perfilObservado)
            if (escola) {
                if (coordenacao) return sincronizarCoordenacao(client, alvo!!)
                guardarDiagnostico("Perfil Unidade escolar ativo; painel do professor preservado", client)
                mostrar(antigo.copy(sincronizando = false, inicioConsulta = 0L,
                    mensagem = "Perfil Unidade escolar ativo. Seu diário atualiza no perfil Servidor. Dados abaixo são da última consulta."))
                return Result.success()
            }
            if (coordenacao) {
                bloqueada = true
                pausarFilaAtual(rodada)
                SgeCoordStore.salvar(ctx, SgeCoordStore.ler(ctx).copy(sincronizando = false,
                    mensagem = "O perfil ativo é Servidor. Entre no perfil Unidade escolar no SGE Web e toque em Continuar."))
                return Result.success()
            }

            val turmas = withTimeout(90_000L) {
                withContext(Dispatchers.IO) { client.turmas(SgeEscopo.incluidas(ctx)) }
            }
            val todosIds = turmas.map { chaveVinculo(it.id, it.disciplina) }.toSet()
            // Continuar segue de onde parou; qualquer outra consulta recomeça do zero.
            SgePausaPessoal.definirEscopo(ctx, todosIds, bimestre)
            val feitasAntes = if (retomando) SgePausaPessoal.ler(ctx)?.feitas.orEmpty() else emptySet()
            val idsFeitasAntes = turmas.filter { chaveVinculo(it.id, it.disciplina) in feitasAntes }.map { it.id }.toSet()
            val mudancasAnteriores = antigo.mudancas.filter { it.turmaId in idsFeitasAntes }
            val resultados = antigo.turmas.filter { chaveResultado(it) in feitasAntes }.toMutableList()
            val novos = mutableListOf<ResultadoTurma>()
            var pausou = false
            for (turma in turmas) {
                val chave = chaveVinculo(turma.id, turma.disciplina)
                if (chave in feitasAntes) continue
                if (pausaPedida()) { pausou = true; break }
                notificacao?.mostrar(resultados.size, turmas.size)
                val parcial = SgeDashboard.progresso(antigo.turmas, resultados, todosIds)
                val mudancasParciais = mudancasAnteriores + SgeComparacao.calcular(antigo.turmas, novos)
                val modulos = withTimeout(90_000L) {
                    consultarModulos(client, turma, antigo.turmas, ::pausaPedida) { etapa ->
                        SgePainelStore.salvar(ctx, PainelSge(parcial, "${resultados.size}/${turmas.size} consultas processadas · ${turma.nome} · $etapa",
                            true, true, System.currentTimeMillis(), mudancasParciais))
                    }
                }
                if (modulos == null) { pausou = true; break } // pausa no meio da turma: ela é refeita ao continuar
                val lida = ResultadoTurma(turma, modulos, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")))
                resultados.add(lida); novos.add(lida)
                mostrar(PainelSge(SgeDashboard.progresso(antigo.turmas, resultados, todosIds),
                    "${resultados.size}/${turmas.size} consultas processadas.", true, true, System.currentTimeMillis(),
                    mudancasAnteriores + SgeComparacao.calcular(antigo.turmas, novos)))
                SgePausaPessoal.marcarFeita(ctx, chave)
            }
            val mudancas = mudancasAnteriores + SgeComparacao.calcular(antigo.turmas, novos)
            if (pausou) {
                guardarDiagnostico("Pausado pelo usuário", cliente, resultados)
                mostrar(PainelSge(SgeDashboard.progresso(antigo.turmas, resultados, todosIds),
                    "Pausado: ${resultados.size}/${turmas.size} consultas feitas. Toque em Continuar para seguir de onde parou.", false, true, 0L, mudancas))
                return Result.success()
            }
            SgeHistoricoStore.registrar(ctx, FontePainel.PESSOAL, resultados)
            SgePausaPessoal.limpar(ctx)
            val falhas = resultados.sumOf { r -> r.modulos.count { it.quantidade == null } }
            val parciais = resultados.sumOf { r -> r.modulos.count { !it.coberturaCompleta } }
            guardarDiagnostico(if (falhas > 0 || parciais > 0) "Módulos com falha ou cobertura parcial" else "Consulta completa", cliente, resultados)
            mostrar(PainelSge(resultados, "${turmas.map { it.id }.distinct().size} turmas consultadas. " +
                if (falhas > 0) "$falhas consultas não verificadas. Toque na turma para conferir os detalhes." else
                    if (parciais > 0) "$parciais consultas com cobertura parcial (*). Confira os detalhes." else
                        "Documentos ausentes e registros não enviados/reprovados conferidos nos períodos iniciados.", false, true, mudancas = mudancas))
            return Result.success()
        } catch (e: TimeoutCancellationException) {
            guardarDiagnostico("Tempo limite de 90s excedido", cliente)
            if (coordenacao) registrarErroCoord(alvo!!, "Tempo limite excedido; consulta não concluída.")
            if (!coordenacao && manual) SgePausaPessoal.pedirPausa(ctx)
            avisar(coordenacao, SgePainelStore.ler(ctx).copy(mensagem = "A consulta excedeu o tempo limite. Verifique a conexão e tente novamente.",
                sincronizando = false, inicioConsulta = 0L))
            return if (coordenacao) Result.success() else Result.failure()
        } catch (e: CancellationException) { cancelado = true; throw e }
        catch (e: SessaoExpirada) {
            SgeSessaoStore.salvar(ctx, PerfilSge.EXPIRADO)
            bloqueada = true
            if (coordenacao) pausarFilaAtual(rodada) else SgePausaPessoal.pedirPausa(ctx)
            if (!manual) SgeAvisos.sessaoExpirada(ctx)
            guardarDiagnostico("Sessão expirada", cliente)
            avisar(coordenacao, SgePainelStore.ler(ctx).copy(mensagem = "Sessão expirada. Entre novamente no SGE Web e toque em Continuar.",
                sincronizando = false, sessaoAtiva = false, inicioConsulta = 0L))
            return Result.success()
        } catch (e: Exception) {
            // O SGE varia o formato de algumas telas. Exibir a etapa que falhou
            // evita que uma resposta inesperada pareça, indevidamente, falta de rede.
            val motivo = "${e.javaClass.simpleName}: ${e.message ?: ""}".replace(Regex("\\s+"), " ").take(500)
            guardarDiagnostico("Erro: $motivo", cliente)
            if (coordenacao) registrarErroCoord(alvo!!, motivo)
            if (!coordenacao && manual && runAttemptCount >= 2) SgePausaPessoal.pedirPausa(ctx)
            avisar(coordenacao, SgePainelStore.ler(ctx).copy(mensagem = "Não foi possível completar a consulta: $motivo.",
                sincronizando = false, inicioConsulta = 0L))
            return if (coordenacao) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            notificacao?.encerrar()
            if (alvo != null && !bloqueada && !cancelado) finalizarFila(rodada, alvo, inicioWorker)
            // Refresh after every exit path, including cancellation/retry, so the
            // launcher widget never stays visually stuck on a stale loading view.
            withContext(NonCancellable + Dispatchers.Main) {
                if (!cancelado && !bloqueada && SgeSessaoStore.geracao(ctx) == geracao) {
                    updatedCookies.forEach { CookieManager.getInstance().setCookie(BASE, it) }
                    CookieManager.getInstance().flush()
                }
                SgeWidgets.atualizar(ctx)
            }
        }
    }
    private suspend fun avisar(coordenacao: Boolean, p: PainelSge) {
        if (coordenacao) SgeCoordStore.salvar(ctx, SgeCoordStore.ler(ctx).copy(mensagem = p.mensagem, sincronizando = false, inicioConsulta = 0L)) else mostrar(p)
    }
    private fun pausarFilaAtual(rodada: String?) {
        SgeSessaoStore.gravarSeAtual(ctx) {
            if (rodada == null || SgeFila.ler(ctx)?.rodada == rodada) SgeFila.pausar(ctx)
        }
    }
    private fun finalizarFila(rodada: String?, alvo: String, inicio: Long) {
        SgeSessaoStore.gravarSeAtual(ctx) {
            val fila = SgeFila.ler(ctx)
            if (fila?.pausada == true || rodada != null && fila?.rodada != rodada) return@gravarSeAtual
            val atual = SgeCoordStore.ler(ctx)
            val erro = atual.turmas.firstOrNull { it.id == alvo }?.erro
            val restante = if (erro != null) SgeFila.falhar(ctx, alvo) else SgeFila.concluir(ctx, alvo, System.currentTimeMillis() - inicio)
            val rodando = restante?.ids?.isNotEmpty() == true
            val mensagem = when {
                rodando -> SgeFila.textoAndamento(restante!!, "Fila de coordenação")
                restante?.falhas?.isNotEmpty() == true ->
                    "Fila concluída: ${restante.concluidas.size} consulta(s) concluída(s), ${restante.falhas.size} falha(s). Toque em Tentar falhas para repetir."
                else -> if (erro == null) "Fila concluída sem falhas." else atual.mensagem
            }
            SgeCoordStore.salvar(ctx, atual.copy(sincronizando = rodando, inicioConsulta = if (rodando) System.currentTimeMillis() else 0L,
                mensagem = mensagem))
        }
    }
    /** Coordenação: verifica UMA turma por vez (todas as disciplinas dela × 5 módulos), reaproveitando as consultas do professor. */
    private suspend fun sincronizarCoordenacao(client: SgeReadClient, alvo: String): Result {
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        val lista = runCatching { JSONArray(prefs.getString("turmas_escola", "[]")) }.getOrDefault(JSONArray())
        val o = (0 until lista.length()).map { lista.getJSONObject(it) }.firstOrNull { it.getString("v") == alvo && SgeEscopo.permitida(ctx, it.optString("t")) }
        var painel = SgeCoordStore.ler(ctx)
        if (o == null) {
            registrarErroCoord(alvo, "Turma não encontrada na lista atual do SGE.")
            SgeCoordStore.salvar(ctx, SgeCoordStore.ler(ctx).copy(sincronizando = false, mensagem = "Turma não encontrada. Abra Escola → Conteúdo Aplicado na aba SGE Web e toque em Carregar turmas."))
            return Result.success()
        }
        val nome = o.getString("t")
        val agora = { LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) }
        suspend fun gravar(t: CoordTurma, msg: String, ocupado: Boolean, mudancas: List<MudancaModulo>? = null) {
            val novasMudancas = mudancas?.let { painel.mudancas.filterNot { m -> m.turmaId == alvo } + it } ?: painel.mudancas
            painel = painel.copy(turmas = painel.turmas.filter { it.id != alvo } + t, mensagem = msg, sincronizando = ocupado,
                inicioConsulta = if (ocupado) System.currentTimeMillis() else 0L, mudancas = novasMudancas)
            SgeCoordStore.salvar(ctx, painel)
            SgeWidgets.atualizar(ctx)
        }
        val anterior = painel.turmas.firstOrNull { it.id == alvo } ?: CoordTurma(alvo, nome)
        gravar(anterior.copy(erro = null), "Lendo as disciplinas de $nome…", true)
        // Sincronização por professor: só as disciplinas dele; as demais continuam como estavam.
        val escolhidos = inputData.getString("coord_componentes")?.let { s ->
            JSONArray(s).let { a -> (0 until a.length()).map { normalizarSge(a.getString(it)) }.toSet() }
        }
        val todas = withTimeout(60_000L) { withContext(Dispatchers.IO) { client.disciplinasDaTurma(alvo) } }
        val nomes = if (escolhidos == null) todas else todas.filter { normalizarSge(it) in escolhidos }
        val mantidas = if (escolhidos == null) emptyList() else anterior.disciplinas.filter { normalizarSge(it.turma.disciplina) !in escolhidos }
        if (nomes.isEmpty()) {
            registrarErroCoord(alvo, "Nenhuma disciplina do recorte foi identificada no SGE.")
            return Result.success()
        }
        val chaves = nomes.map { chaveVinculo(alvo, it) }.toSet()
        val feitas = mutableListOf<ResultadoTurma>()
        for ((i, d) in nomes.withIndex()) {
            val t = TurmaMonitorada(alvo, nome, "", d)
            val parcial = anterior.copy(disciplinas = mantidas + SgeDashboard.progresso(anterior.disciplinas, feitas, chaves), erro = null)
            val modulos = try {
                withTimeout(180_000L) {
                    consultarModulos(client, t, anterior.disciplinas) { etapa ->
                        val atual = "$nome • $d (${i + 1}/${nomes.size}) • $etapa"
                        gravar(parcial, SgeFila.ler(ctx)?.let { SgeFila.textoAndamento(it, atual) } ?: atual, true)
                    }.orEmpty()
                }
            } catch (e: TimeoutCancellationException) { List(5) { ResultadoModulo(detalhe = "Não verificado: tempo esgotado") } }
            feitas.add(ResultadoTurma(t, modulos, agora()))
        }
        val temModuloFalho = feitas.any { r -> r.modulos.any { it.quantidade == null } }
        val fimTurma = CoordTurma(alvo, nome, mantidas + feitas,
            if (temModuloFalho) "Uma ou mais consultas de módulo não foram verificadas." else null, agora(), System.currentTimeMillis())
        val mudancas = SgeComparacao.calcular(if (escolhidos == null) anterior.disciplinas else anterior.disciplinas.filterNot { it in mantidas }, feitas)
        val restaTurma = SgeFila.ler(ctx)?.ids.orEmpty().any { it != alvo }
        val mensagem = if (temModuloFalho) "$nome consultada com falhas. ${feitas.size} disciplina(s) lida(s); algumas consultas precisam ser repetidas."
            else "$nome verificada: ${feitas.size} disciplina(s)."
        gravar(fimTurma, mensagem, restaTurma, mudancas)
        SgeHistoricoStore.registrar(ctx, FontePainel.COORDENACAO, feitas)
        guardarDiagnostico("Coordenação: $nome", client, feitas)
        return Result.success()
    }

    private suspend fun consultarModulos(client: SgeReadClient, turma: TurmaMonitorada, anteriores: List<ResultadoTurma>,
        interromper: () -> Boolean = { false }, status: suspend (String) -> Unit): List<ResultadoModulo>? {
        val etapas = listOf<() -> ResultadoModulo>(
            { client.conteudos(turma).carimbar() },
            { client.frequencia(turma).carimbar() },
            { planosComCache(client, turma, "PLANO DE AULA", 2, anteriores) },
            { planosComCache(client, turma, "PLANO DE CURSO", 3, anteriores) },
            { planosComCache(client, turma, "PEI - Plano de Ensino Individualizado", 4, anteriores) }
        )
        val resultados = mutableListOf<ResultadoModulo>()
        for ((indice, etapa) in etapas.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (interromper()) return null
            status(SgePainelStore.NOMES_MODULOS[indice])
            resultados.add(withContext(Dispatchers.IO) { etapa() })
        }
        return resultados
    }

    /** Planos e PEIs mudam pouco: reaproveita a última consulta completa e sem pendências por até sete dias. */
    private fun planosComCache(client: SgeReadClient, t: TurmaMonitorada, tipo: String, indice: Int, anteriores: List<ResultadoTurma>): ResultadoModulo {
        val agora = System.currentTimeMillis()
        val chave = "${t.id}|${t.disciplina}|$indice"
        val prev = anteriores.firstOrNull { it.turma.id == t.id && it.turma.disciplina == t.disciplina }?.modulos?.getOrNull(indice)
        val ts = SgeCachePlanos.ts(ctx, chave)
        val consultaManual = if (inputData.getString("coord_alvo") != null) !inputData.getBoolean("auto", false)
            else inputData.getBoolean("manual", false)
        if (!consultaManual && SgeCachePlanos.reaproveitavel(prev, ts, agora)) return SgeCachePlanos.anotar(prev!!, ts)
            .copy(atualizadoMs = prev.atualizadoMs.takeIf { it > 0L } ?: ts)
        val novo = client.planos(t, tipo).carimbar()
        if (novo.quantidade != null && (novo.coberturaCompleta || novo.naoConfigurado)) SgeCachePlanos.marcar(ctx, chave, agora)
        return novo
    }
    private fun pausaPedida() = SgePausaPessoal.ler(ctx)?.pausada == true
    private fun ResultadoModulo.carimbar() = copy(atualizadoMs = System.currentTimeMillis())
    private fun guardarDiagnostico(titulo: String, c: SgeReadClient?, resultados: List<ResultadoTurma> = emptyList()) {
        val t = buildString {
            appendLine("${LocalDateTime.now()} · $titulo")
            SgeDiagnosticoAgrupado.linhas(resultados).forEach { appendLine(it) }
            c?.historico?.forEach { appendLine(it) }
        }
        SgeSessaoStore.gravarSeAtual(ctx) { ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putString(if (inputData.getString("coord_alvo") != null) "diagnostico_coord_v2" else "diagnostico_pessoal_v2", t.take(20000)).commit() }
    }

    private fun registrarErroCoord(id: String, erro: String) {
        val painel = SgeCoordStore.ler(ctx)
        val turma = painel.turmas.firstOrNull { it.id == id } ?: CoordTurma(id, id)
        SgeCoordStore.salvar(ctx, painel.copy(turmas = painel.turmas.filterNot { it.id == id } + turma.copy(erro = erro.take(240))))
    }

    companion object {
        const val BASE = "https://sge.seduc.to.pontoid.com.br"
        private val travaPessoal = Mutex()
        private val travaCoord = Mutex()
        private fun rede() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun agendarPeriodico(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sge_periodic_sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SgeSyncWorker>(SgeEconomia.ler(context).horas.toLong(), TimeUnit.HOURS).setConstraints(SgeEconomia.restricoes(context)).build())
        }
        /** Coordenação: enfileira uma consulta por turma, em sequência (cada uma cabe no limite de tempo do WorkManager). */
        fun verificarTurmas(context: Context, ids: List<String>, substituir: Boolean = true, automatica: Boolean = false,
            componentes: Map<String, Set<String>> = emptyMap()) {
            if (ids.isEmpty()) return
            val atual = SgeFila.ler(context)
            if (automatica && atual != null && atual.pausada && atual.ids.isNotEmpty()) return
            if (!substituir && atual != null && atual.ids.isNotEmpty()) return
            // Consultas manuais refazem o recorte escolhido, mesmo após consulta recente em outro bimestre.
            val plano = SgeFila.planejar(ids, SgeCoordStore.ler(context).turmas, System.currentTimeMillis(), pular = automatica)
            if (plano.ordem.isEmpty()) {
                SgeFila.salvar(context, null)
                SgeCoordStore.salvar(context, SgeCoordStore.ler(context).copy(sincronizando = false, inicioConsulta = 0L,
                    mensagem = "Todas as ${plano.puladas} turmas foram verificadas há menos de 3 horas. Nada a consultar agora."))
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { SgeWidgets.atualizar(context) }; return
            }
            val fila = FilaCoord(plano.ordem, plano.ordem.size + plano.puladas, false, plano.puladas, SgeFila.media(context),
                bimestre = SgeBimestre.ler(context), componentes = componentes)
            SgeFila.salvar(context, fila)
            SgeCoordStore.salvar(context, SgeCoordStore.ler(context).copy(sincronizando = true, inicioConsulta = System.currentTimeMillis(),
                mensagem = "Na fila: ${fila.total} turma(s), ${SgeFila.textoEstimativa(fila.total, fila.mediaMs)}" + (if (plano.puladas > 0) "; ${plano.puladas} pulada(s), verificadas há menos de 3 h" else "") + ". Toque em verificar para pausar."))
            enfileirar(context, fila, substituir, automatica)
        }
        fun pausar(context: Context) {
            SgeFila.pausar(context)
            WorkManager.getInstance(context).cancelUniqueWork("sge_coord")
            SgeFila.ler(context)?.let { SgeCoordStore.salvar(context, SgeCoordStore.ler(context).copy(sincronizando = false, inicioConsulta = 0L, mensagem = SgeFila.textoPausada(it))) }
        }
        fun retomar(context: Context) {
            val f = SgeFila.ler(context)?.takeIf { it.pausada && it.ids.isNotEmpty() } ?: return
            if (f.bimestre >= 0 && f.bimestre != SgeBimestre.ler(context)) {
                verificarTurmas(context, f.concluidas + f.falhas + f.ids, componentes = f.componentes)
                return
            }
            val nova = f.copy(pausada = false, rodada = UUID.randomUUID().toString())
            SgeFila.salvar(context, nova)
            SgeCoordStore.salvar(context, SgeCoordStore.ler(context).copy(sincronizando = true, inicioConsulta = System.currentTimeMillis(),
                mensagem = "Retomando: restam ${f.ids.size} turma(s), ${SgeFila.textoEstimativa(f.ids.size, f.mediaMs)}."))
            enfileirar(context, nova, true, false)
        }
        /** Meu diário: para na próxima etapa guardando o que já foi lido. */
        fun pausarPessoal(context: Context) {
            SgePausaPessoal.pedirPausa(context)
            val p = SgePainelStore.ler(context)
            if (p.sincronizando) SgePainelStore.salvar(context, p.copy(mensagem = "Pausando… a consulta termina a etapa atual e para."))
        }
        /** Meu diário: segue a consulta de onde parou, sem refazer o que já foi lido. */
        fun retomarPessoal(context: Context) {
            if (!SgePausaPessoal.retomar(context)) return
            val p = SgePainelStore.ler(context)
            SgePainelStore.salvar(context, p.copy(sincronizando = true, inicioConsulta = System.currentTimeMillis(), mensagem = "Retomando a consulta de onde parou…"))
            WorkManager.getInstance(context).enqueueUniqueWork("sge_sync_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SgeSyncWorker>().setConstraints(rede()).setInputData(workDataOf("manual" to true, "retomar" to true)).build())
        }
        fun tentarFalhas(context: Context) {
            val fila = SgeFila.ler(context)?.takeIf { it.falhas.isNotEmpty() } ?: return
            verificarTurmas(context, fila.falhas, componentes = fila.componentes)
        }
        private fun enfileirar(context: Context, fila: FilaCoord, substituir: Boolean, automatica: Boolean) {
            val reqs = fila.ids.map { id ->
                val dados = Data.Builder().putString("coord_alvo", id).putBoolean("auto", automatica).putString("coord_rodada", fila.rodada)
                fila.componentes[id]?.let { dados.putString("coord_componentes", JSONArray(it.toList()).toString()) }
                OneTimeWorkRequestBuilder<SgeSyncWorker>()
                    .setConstraints(if (automatica) SgeEconomia.restricoes(context) else rede()).setInputData(dados.build()).build()
            }
            var c = WorkManager.getInstance(context).beginUniqueWork("sge_coord", if (substituir) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, reqs.first())
            reqs.drop(1).forEach { c = c.then(it) }
            SgeSessaoStore.gravarSeAtual(context) { c.enqueue() }
        }
        /** Sincroniza só as disciplinas indicadas: turmaId → componentes. */
        fun verificarComponentes(context: Context, pares: Map<String, Set<String>>) {
            verificarTurmas(context, pares.keys.toList(), componentes = pares)
        }
        fun sincronizarAgora(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("sge_sync_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SgeSyncWorker>().setConstraints(rede()).setInputData(workDataOf("manual" to true)).build())
        }
    }
}

package br.to.seduc.sgediariowidget.worker

import android.content.Context
import android.webkit.CookieManager
import androidx.work.*
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.SgeWidgets
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class SgeSyncWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private suspend fun mostrar(painel: PainelSge) {
        SgePainelStore.salvar(ctx, painel)
        SgeWidgets.atualizar(ctx)
    }
    override suspend fun doWork(): Result {
        val antigo = SgePainelStore.ler(ctx)
        val alvo = inputData.getString("coord_alvo")
        val coordenacao = alvo != null
        if (!coordenacao) mostrar(antigo.copy(sincronizando = true, inicioConsulta = System.currentTimeMillis(), mensagem = "Consultando o horário do professor…"))
        var cliente: SgeReadClient? = null
        try {
            val cookies = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(BASE) }
            if (cookies.isNullOrBlank()) throw SessaoExpirada()
            val agent = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getString("web_user_agent", null) ?: error("Abra o SGE no aplicativo antes de sincronizar.")
            val updatedCookies = mutableListOf<String>()
            val client = SgeReadClient(cookies, agent, ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getString("turmas_contexto", null)) { updatedCookies.add(it) }
            cliente = client
            // Dois perfis no SGE: Servidor (professor) e Unidade escolar (coordenação). Cada um tem seu painel.
            val escola = withTimeout(30_000L) { withContext(Dispatchers.IO) { client.ehPerfilEscola() } }
            if (escola) {
                if (coordenacao) return sincronizarCoordenacao(client, alvo!!)
                guardarDiagnostico("Perfil Unidade escolar ativo; painel do professor preservado", client)
                mostrar(antigo.copy(sincronizando = false, inicioConsulta = 0L,
                    mensagem = "Perfil Unidade escolar ativo. Troque para Servidor no SGE Web para atualizar este painel. Dados abaixo são da última consulta."))
                return Result.success()
            }
            if (coordenacao) {
                SgeCoordStore.salvar(ctx, SgeCoordStore.ler(ctx).copy(sincronizando = false,
                    mensagem = "O perfil ativo é Servidor. Troque para Unidade escolar no SGE Web e tente de novo."))
                return Result.success()
            }

            val turmas = withTimeout(90_000L) {
                withContext(Dispatchers.IO) { client.turmas() }
            }
            val resultados = mutableListOf<ResultadoTurma>()
            for ((indice, turma) in turmas.withIndex()) {
                mostrar(PainelSge(SgeDashboard.progresso(antigo.turmas, resultados, turmas.map { chaveVinculo(it.id, it.disciplina) }.toSet()), "Consultando ${indice + 1}/${turmas.size}: ${turma.nome} • ${turma.disciplina}", true, true, System.currentTimeMillis()))
                val modulos = withTimeout(90_000L) {
                    withContext(Dispatchers.IO) { listOf(
                        client.conteudos(turma), client.frequencia(turma), client.planos(turma, "PLANO DE AULA"),
                        client.planos(turma, "PLANO DE CURSO"), client.planos(turma, "PEI - Plano de Ensino Individualizado"))
                    }
                }
                resultados.add(ResultadoTurma(turma, modulos, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))))
            }
            withContext(Dispatchers.Main) {
                updatedCookies.forEach { CookieManager.getInstance().setCookie(BASE, it) }
                CookieManager.getInstance().flush()
            }
            val falhas = resultados.sumOf { r -> r.modulos.count { it.quantidade == null } }
            val parciais = resultados.sumOf { r -> r.modulos.count { !it.coberturaCompleta } }
            if (falhas > 0 || parciais > 0) guardarDiagnostico("Módulos com falha ou cobertura parcial", cliente, resultados)
            mostrar(PainelSge(resultados, "${turmas.map { it.id }.distinct().size} turmas consultadas. " +
                if (falhas > 0) "$falhas consultas não verificadas. Toque na turma para conferir os detalhes." else
                    if (parciais > 0) "$parciais consultas com cobertura parcial (*). Confira os detalhes." else
                        "Documentos ausentes e registros não enviados/reprovados conferidos nos períodos iniciados.", false, true))
            return Result.success()
        } catch (e: TimeoutCancellationException) {
            guardarDiagnostico("Tempo limite de 90s excedido", cliente)
            avisar(coordenacao, antigo.copy(mensagem = "A consulta demorou mais de 90 segundos. Verifique a conexão e tente novamente.",
                sincronizando = false, inicioConsulta = 0L))
            return Result.failure()
        } catch (e: CancellationException) { throw e }
        catch (e: SessaoExpirada) {
            guardarDiagnostico("Sessão expirada", cliente)
            avisar(coordenacao, antigo.copy(mensagem = "Sessão expirada. Entre novamente no SGE. Dados abaixo são da última consulta.", sincronizando = false, sessaoAtiva = false))
            return Result.success()
        } catch (e: Exception) {
            // O SGE varia o formato de algumas telas. Exibir a etapa que falhou
            // evita que uma resposta inesperada pareça, indevidamente, falta de rede.
            val motivo = "${e.javaClass.simpleName}: ${e.message ?: ""}".replace(Regex("\\s+"), " ").take(500)
            guardarDiagnostico("Erro: $motivo", cliente)
            avisar(coordenacao, antigo.copy(mensagem = "Não foi possível consultar o horário: $motivo. Dados abaixo são da última consulta.", sincronizando = false))
            return if (coordenacao) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            if (coordenacao) SgeCoordStore.ler(ctx).let { c ->
                if (c.sincronizando) SgeCoordStore.salvar(ctx, c.copy(sincronizando = false, mensagem = "Consulta interrompida. Toque em Verificar para tentar de novo."))
            }
            // Refresh after every exit path, including cancellation/retry, so the
            // launcher widget never stays visually stuck on a stale loading view.
            withContext(NonCancellable + Dispatchers.Main) { SgeWidgets.atualizar(ctx) }
        }
    }
    private suspend fun avisar(coordenacao: Boolean, p: PainelSge) {
        if (coordenacao) SgeCoordStore.salvar(ctx, SgeCoordStore.ler(ctx).copy(mensagem = p.mensagem, sincronizando = false)) else mostrar(p)
    }
    /** Coordenação: verifica UMA turma por vez (todas as disciplinas dela × 5 módulos), reaproveitando as consultas do professor. */
    private suspend fun sincronizarCoordenacao(client: SgeReadClient, alvo: String): Result {
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        val lista = runCatching { JSONArray(prefs.getString("turmas_escola", "[]")) }.getOrDefault(JSONArray())
        val o = (0 until lista.length()).map { lista.getJSONObject(it) }.firstOrNull { it.getString("v") == alvo }
        var painel = SgeCoordStore.ler(ctx)
        if (o == null) {
            SgeCoordStore.salvar(ctx, painel.copy(sincronizando = false, mensagem = "Turma não encontrada. Abra Escola → Conteúdo Aplicado na aba SGE Web e toque em Carregar turmas."))
            return Result.success()
        }
        val nome = o.getString("t")
        val agora = { LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) }
        suspend fun gravar(t: CoordTurma, msg: String, ocupado: Boolean) {
            painel = painel.copy(turmas = painel.turmas.filter { it.id != alvo } + t, mensagem = msg, sincronizando = ocupado, inicioConsulta = if (ocupado) System.currentTimeMillis() else 0L)
            SgeCoordStore.salvar(ctx, painel)
            SgeWidgets.atualizar(ctx)
        }
        val anterior = painel.turmas.firstOrNull { it.id == alvo } ?: CoordTurma(alvo, nome)
        gravar(anterior, "Lendo as disciplinas de $nome…", true)
        val nomes = withTimeout(60_000L) { withContext(Dispatchers.IO) { client.disciplinasDaTurma(alvo) } }
        val feitas = mutableListOf<ResultadoTurma>()
        for ((i, d) in nomes.withIndex()) {
            gravar(CoordTurma(alvo, nome, SgeDashboard.progresso(anterior.disciplinas, feitas, nomes.map { chaveVinculo(alvo, it) }.toSet()), null, anterior.atualizado), "Consultando $nome • $d (${i + 1}/${nomes.size})", true)
            val t = TurmaMonitorada(alvo, nome, "", d)
            val modulos = try {
                withTimeout(180_000L) { withContext(Dispatchers.IO) { listOf(
                    client.conteudos(t), client.frequencia(t), client.planos(t, "PLANO DE AULA"),
                    client.planos(t, "PLANO DE CURSO"), client.planos(t, "PEI - Plano de Ensino Individualizado")) } }
            } catch (e: TimeoutCancellationException) { List(5) { ResultadoModulo(detalhe = "Não verificado: tempo esgotado") } }
            feitas.add(ResultadoTurma(t, modulos, agora()))
        }
        gravar(CoordTurma(alvo, nome, feitas, null, agora()), "$nome verificada: ${feitas.size} disciplina(s).", false)
        guardarDiagnostico("Coordenação: $nome", client, feitas)
        return Result.success()
    }

    private fun guardarDiagnostico(titulo: String, c: SgeReadClient?, resultados: List<ResultadoTurma> = emptyList()) {
        val t = buildString {
            appendLine(titulo)
            resultados.forEachIndexed { i, r -> r.modulos.forEachIndexed { j, m ->
                if (m.quantidade == null || !m.coberturaCompleta)
                    appendLine("Turma ${i + 1} ${SgePainelStore.NOMES_MODULOS.getOrElse(j) { "Módulo" }}: ${m.resumo()} ${m.detalhe}")
            } }
            c?.historico?.forEach { appendLine(it) }
        }
        ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putString("diagnostico_v1", t.take(20000)).commit()
    }

    companion object {
        const val BASE = "https://sge.seduc.to.pontoid.com.br"
        private fun rede() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun agendarPeriodico(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sge_periodic_sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SgeSyncWorker>(2, TimeUnit.HOURS).setConstraints(rede()).build())
        }
        /** Coordenação: enfileira uma consulta por turma, em sequência (cada uma cabe no limite de tempo do WorkManager). */
        fun verificarTurmas(context: Context, ids: List<String>) {
            if (ids.isEmpty()) return
            val reqs = ids.map { OneTimeWorkRequestBuilder<SgeSyncWorker>().setConstraints(rede()).setInputData(workDataOf("coord_alvo" to it)).build() }
            var c = WorkManager.getInstance(context).beginUniqueWork("sge_coord", ExistingWorkPolicy.REPLACE, reqs.first())
            reqs.drop(1).forEach { c = c.then(it) }
            c.enqueue()
        }
        fun sincronizarAgora(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("sge_sync_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SgeSyncWorker>().setConstraints(rede()).build())
        }
    }
}

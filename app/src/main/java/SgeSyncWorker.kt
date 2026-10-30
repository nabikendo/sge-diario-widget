package br.to.seduc.sgediariowidget.worker

import android.content.Context
import android.webkit.CookieManager
import androidx.glance.appwidget.updateAll
import androidx.work.*
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.DiarioGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class SgeSyncWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private suspend fun mostrar(painel: PainelSge) {
        SgePainelStore.salvar(ctx, painel)
        DiarioGlanceWidget().updateAll(ctx)
    }
    override suspend fun doWork(): Result {
        val antigo = SgePainelStore.ler(ctx)
        mostrar(antigo.copy(sincronizando = true, mensagem = "Consultando o horário do professor…"))
        try {
            val cookies = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(BASE) }
            if (cookies.isNullOrBlank()) throw SessaoExpirada()
            val agent = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getString("web_user_agent", null) ?: error("Abra o SGE no aplicativo antes de sincronizar.")
            val updatedCookies = mutableListOf<String>()
            val client = SgeReadClient(cookies, agent) { updatedCookies.add(it) }
            val turmas = withContext(Dispatchers.IO) { client.turmas() }
            val resultados = mutableListOf<ResultadoTurma>()
            for ((indice, turma) in turmas.withIndex()) {
                mostrar(PainelSge(resultados.toList(), "Consultando ${indice + 1}/${turmas.size}: ${turma.nome} • ${turma.disciplina}", true, true))
                val modulos = withContext(Dispatchers.IO) { listOf(
                    client.conteudos(turma), client.frequencia(turma), client.planos(turma, "PLANO DE AULA"),
                    client.planos(turma, "PLANO DE CURSO"), client.planos(turma, "PEI - Plano de Ensino Individualizado")) }
                resultados.add(ResultadoTurma(turma, modulos, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))))
            }
            withContext(Dispatchers.Main) {
                updatedCookies.forEach { CookieManager.getInstance().setCookie(BASE, it) }
                CookieManager.getInstance().flush()
            }
            val falhas = resultados.sumOf { r -> r.modulos.count { it.quantidade == null } }
            mostrar(PainelSge(resultados, "${turmas.map { it.id }.distinct().size} turmas consultadas. " +
                if (falhas > 0) "$falhas consultas não verificadas. Toque na turma para conferir os detalhes." else
                    "* Planos/PEIs: estados dos registros existentes; cobertura de documentos ainda não criados não verificada.", false, true))
            return Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: SessaoExpirada) {
            mostrar(antigo.copy(mensagem = "Sessão expirada. Entre novamente no SGE. Dados abaixo são da última consulta.", sincronizando = false, sessaoAtiva = false))
            return Result.success()
        } catch (_: Exception) {
            mostrar(antigo.copy(mensagem = "Não foi possível consultar o horário. Confira a sessão e a conexão. Dados abaixo são da última consulta.", sincronizando = false))
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
    companion object {
        const val BASE = "https://sge.seduc.to.pontoid.com.br"
        private fun rede() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun agendarPeriodico(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sge_periodic_sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SgeSyncWorker>(2, TimeUnit.HOURS).setConstraints(rede()).build())
        }
        fun sincronizarAgora(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("sge_sync_now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SgeSyncWorker>().setConstraints(rede()).build())
        }
    }
}

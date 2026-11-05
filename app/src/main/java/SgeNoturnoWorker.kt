package br.to.seduc.sgediariowidget.worker

import android.content.Context
import android.webkit.CookieManager
import androidx.work.*
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.SgeWidgetStore
import kotlinx.coroutines.*
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object SgeAgendaNoturna {
    fun atraso(agora: ZonedDateTime, hora: Int = 3): Long {
        var alvo = agora.toLocalDate().atTime(hora.coerceIn(0, 23), 0).atZone(agora.zone)
        if (!alvo.isAfter(agora)) alvo = alvo.plusDays(1)
        return Duration.between(agora, alvo).toMillis()
    }
}
class SgeNoturnoWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(SgeSessaoStore.contextoConsulta(ctx)) { executar() }
    private suspend fun executar(): Result {
        val geracao = SgeSessaoStore.geracao(ctx)
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("coord_noturna", false)) return Result.success()
        val ids = SgeWidgetStore.conhecidas(ctx).map { it.first }.distinct()
        if (ids.isEmpty() || SgeCoordStore.ler(ctx).sincronizando) return Result.success()
        val cookiesAtualizados = mutableListOf<String>()
        try {
            val cookie = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(SgeSyncWorker.BASE) }
            if (cookie.isNullOrBlank()) throw SessaoExpirada()
            val agent = prefs.getString("web_user_agent", null) ?: return Result.success()
            val client = SgeReadClient(cookie, agent, prefs.getString("turmas_contexto", null)) { cookiesAtualizados.add(it) }
            val escola = withContext(Dispatchers.IO) { withTimeout(40_000L) { client.ehPerfilEscola() } }
            SgeSessaoStore.salvar(ctx, client.perfilObservado)
            if (!escola) { status("Verificação noturna: mantenha uma sessão Unidade escolar para consultar a escola."); return Result.success() }
            status("Verificação noturna enfileirada para ${ids.size} turmas.")
            // KEEP evita substituir uma fila manual que começa enquanto o perfil é verificado.
            SgeSyncWorker.verificarTurmas(ctx, ids, substituir = false, automatica = true)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            status("Verificação noturna: tempo esgotado. Uma nova tentativa foi agendada para a manhã.", repetir = true)
        }
        catch (e: SessaoExpirada) { SgeSessaoStore.salvar(ctx, PerfilSge.EXPIRADO); status("Verificação noturna: entre novamente no SGE Web."); SgeAvisos.sessaoExpirada(ctx) }
        catch (e: Exception) {
            val falhaDeRede = generateSequence(e as Throwable?) { it.cause }.any { it is java.io.IOException }
            status(if (falhaDeRede) "Verificação noturna sem internet. Tentaremos novamente pela manhã, quando houver conexão."
                else "Verificação noturna não concluída. Tentaremos novamente pela manhã.", repetir = true)
        }
        finally { withContext(NonCancellable + Dispatchers.Main) { if (SgeSessaoStore.geracao(ctx) == geracao) { cookiesAtualizados.forEach { CookieManager.getInstance().setCookie(SgeSyncWorker.BASE, it) }; CookieManager.getInstance().flush() } } }
        return Result.success()
    }
    private fun status(texto: String, repetir: Boolean = false) {
        SgeSessaoStore.gravarSeAtual(ctx) { ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("coord_noturna_status", texto).putBoolean("coord_retry_pending", repetir).apply() }
    }
    companion object {
        fun agendar(ctx: Context, ativo: Boolean, recriar: Boolean = false) {
            val wm = WorkManager.getInstance(ctx)
            SgeRepetirFalhasWorker.agendar(ctx, ativo, recriar)
            if (!ativo) { wm.cancelUniqueWork("sge_coord_noturna"); return }
            val rede = SgeEconomia.restricoes(ctx)
            wm.enqueueUniquePeriodicWork("sge_coord_noturna", if (recriar) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SgeNoturnoWorker>(24, TimeUnit.HOURS).setInitialDelay(SgeAgendaNoturna.atraso(ZonedDateTime.now(), SgeEconomia.ler(ctx).horaNoturna), TimeUnit.MILLISECONDS).setConstraints(rede).build())
        }
    }
}

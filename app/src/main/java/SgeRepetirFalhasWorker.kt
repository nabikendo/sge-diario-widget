package br.to.seduc.sgediariowidget.worker

import android.content.Context
import androidx.work.*
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.SgeWidgetStore
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Repetição matinal de falhas da fila noturna; respeita a rede e não substitui uma fila ativa. */
class SgeRepetirFalhasWorker(private val ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("coord_noturna", false)) return Result.success()
        if (SgeCoordStore.ler(ctx).sincronizando) return Result.success()

        val falhaNoturna = prefs.getBoolean("coord_retry_pending", false)
        val filaAtual = SgeFila.ler(ctx)
        if (filaAtual?.pausada == true || filaAtual?.ids?.isNotEmpty() == true) return Result.success()
        val falhasFila = filaAtual?.falhas.orEmpty()
        if (!falhaNoturna && falhasFila.isEmpty()) return Result.success()

        prefs.edit().putString("coord_noturna_status", "Tentando novamente as consultas que falharam. O app aguardará uma conexão de internet.").apply()
        if (falhaNoturna) {
            val ids = SgeWidgetStore.conhecidas(ctx).map { it.first }.distinct()
            if (ids.isNotEmpty()) SgeSyncWorker.verificarTurmas(ctx, ids, substituir = false, automatica = true)
            prefs.edit().putBoolean("coord_retry_pending", false).apply()
        } else {
            SgeSyncWorker.tentarFalhas(ctx)
        }
        return Result.success()
    }

    companion object {
        fun agendar(ctx: Context, ativo: Boolean, recriar: Boolean = false) {
            val wm = WorkManager.getInstance(ctx)
            if (!ativo) { wm.cancelUniqueWork("sge_retry_morning"); return }
            val rede = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val trabalho = PeriodicWorkRequestBuilder<SgeRepetirFalhasWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(SgeAgendaNoturna.atraso(ZonedDateTime.now(), 8), TimeUnit.MILLISECONDS)
                .setConstraints(rede).build()
            wm.enqueueUniquePeriodicWork("sge_retry_morning", if (recriar) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, trabalho)
        }
    }
}

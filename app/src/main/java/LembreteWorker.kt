package br.to.seduc.sgediariowidget.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import br.to.seduc.sgediariowidget.MainActivity
import br.to.seduc.sgediariowidget.R
import br.to.seduc.sgediariowidget.data.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Lembrete diário às 17h com o resumo das pendências da última consulta (nunca consulta o SGE). */
class LembreteWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("lembrete", false) || !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return Result.success()
        val papel = prefs.getInt("papel", 0)
        fun pendentes(rs: List<ResultadoTurma>) = SgeDashboard.agrupar(rs, Agrupamento.TURMA).count { it.estado == EstadoGrupo.PENDENTE }
        val meu = if (papel != 2) pendentes(SgePainelStore.ler(ctx).turmas) else 0
        val escola = if (papel != 1) pendentes(SgeCoordStore.ler(ctx).turmas.flatMap { it.disciplinas }) else 0
        val total = meu + escola
        val anterior = prefs.getInt("lembrete_ultimo", -1)
        prefs.edit().putInt("lembrete_ultimo", total).apply()
        if (total == 0) return Result.success()
        val partes = buildList {
            if (meu > 0) add("$meu ${if (meu == 1) "turma" else "turmas"} com pendências no seu diário")
            if (escola > 0) add("$escola ${if (escola == 1) "turma" else "turmas"} com pendências na coordenação")
        }
        val variacao = when { anterior < 0 -> ""; total > anterior -> " (+${total - anterior} desde o último lembrete)"
            total < anterior -> " (${anterior - total} a menos desde o último lembrete)"; else -> "" }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("lembrete", "Lembrete diário", NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "lembrete").setSmallIcon(R.drawable.ic_widget_refresh)
            .setContentTitle("SGE Diário").setContentText(partes.joinToString("; ") + variacao)
            .setStyle(NotificationCompat.BigTextStyle().bigText(partes.joinToString("\n") + variacao + "\nDados da última consulta."))
            .setContentIntent(abrir).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1717, n) }
        return Result.success()
    }
    companion object {
        fun agendar(context: Context, ativo: Boolean, recriar: Boolean = false) {
            val wm = WorkManager.getInstance(context)
            if (!ativo) { wm.cancelUniqueWork("sge_lembrete"); return }
            val agora = LocalDateTime.now()
            var alvo = agora.toLocalDate().atTime(17, 0)
            if (!alvo.isAfter(agora)) alvo = alvo.plusDays(1)
            val espera = Duration.between(agora, alvo).toMillis()
            wm.enqueueUniquePeriodicWork("sge_lembrete", if (recriar) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LembreteWorker>(24, TimeUnit.HOURS).setInitialDelay(espera, TimeUnit.MILLISECONDS).build())
        }
    }
}

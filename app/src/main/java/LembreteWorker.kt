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

/** Lembrete diário no horário preferido, com dados da última consulta (nunca consulta o SGE). */
object LembreteHorario {
    fun proximaExecucao(agora: LocalDateTime, minutoDoDia: Int): LocalDateTime {
        val minuto = minutoDoDia.coerceIn(0, 1439)
        var alvo = agora.toLocalDate().atStartOfDay().plusMinutes(minuto.toLong())
        if (!alvo.isAfter(agora)) alvo = alvo.plusDays(1)
        return alvo
    }
}

object LembreteResumo {
    fun pendenciasPorModulo(rs: List<ResultadoTurma>): List<String> {
        val unicos = rs.distinctBy(::chaveResultado)
        return (0..4).mapNotNull { indice ->
            val quantidade = unicos.count { (it.modulos.getOrNull(indice)?.quantidade ?: 0) > 0 }
            if (quantidade == 0) null else "${SgeDashboard.nomes[indice]}: $quantidade turma(s)/componente(s)"
        }
    }
}

class LembreteWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("lembrete", false) || !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            NotificationManagerCompat.from(ctx).cancel(1717)
            return Result.success()
        }
        val papel = prefs.getInt("papel", 0)
        val meu = if (papel != 2) LembreteResumo.pendenciasPorModulo(SgePainelStore.ler(ctx).turmas) else emptyList()
        val escola = if (papel != 1) LembreteResumo.pendenciasPorModulo(SgeCoordStore.ler(ctx).turmas.flatMap { it.disciplinas }) else emptyList()
        if (meu.isEmpty() && escola.isEmpty()) {
            NotificationManagerCompat.from(ctx).cancel(1717)
            return Result.success()
        }
        val partes = buildList {
            if (meu.isNotEmpty()) add("Seu diário — ${meu.joinToString("; ")}")
            if (escola.isNotEmpty()) add(if (SgeSessaoStore.protegida(ctx)) "Coordenação: abra o app para conferir as pendências."
                else "Coordenação — ${escola.joinToString("; ")}")
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("lembrete", "Lembrete diário", NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "lembrete").setSmallIcon(R.drawable.ic_widget_refresh)
            .setContentTitle("Pendências para conferir").setContentText(partes.joinToString("; "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(partes.joinToString("\n") + "\nDados da última consulta; o lembrete não consulta o SGE."))
            .setContentIntent(abrir).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1717, n) }
        return Result.success()
    }
    companion object {
        fun agendar(context: Context, ativo: Boolean, recriar: Boolean = false) {
            val wm = WorkManager.getInstance(context)
            if (!ativo) { wm.cancelUniqueWork("sge_lembrete"); NotificationManagerCompat.from(context).cancel(1717); return }
            val agora = LocalDateTime.now()
            val minuto = context.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("lembrete_minuto", 17 * 60).coerceIn(0, 1439)
            val alvo = LembreteHorario.proximaExecucao(agora, minuto)
            val espera = Duration.between(agora, alvo).toMillis()
            wm.enqueueUniquePeriodicWork("sge_lembrete", if (recriar) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LembreteWorker>(24, TimeUnit.HOURS).setInitialDelay(espera, TimeUnit.MILLISECONDS).build())
        }
    }
}

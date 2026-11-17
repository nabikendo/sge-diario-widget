package br.to.seduc.sgediariowidget.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.to.seduc.sgediariowidget.MainActivity
import br.to.seduc.sgediariowidget.R
import br.to.seduc.sgediariowidget.data.*
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Avisos opcionais (Ajustes → Alertas e extras). Usam só dados já consultados; nunca abrem o SGE. */
object SgeNotificacoes {
    private const val CANAL = "extras"

    private fun enviar(ctx: Context, id: Int, titulo: String, texto: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CANAL, "Alertas do diário", NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CANAL).setSmallIcon(R.drawable.ic_widget_refresh)
            .setContentTitle(titulo).setContentText(texto.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(abrir).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    /** Chamado quando uma consulta do diário pessoal termina. */
    fun aoConcluirConsulta(ctx: Context, antes: List<ResultadoTurma>, depois: List<ResultadoTurma>) {
        val extras = SgeExtras.ler(ctx)
        if (extras.avisoZerou) {
            val zeradas = SgeAlertas.turmasZeradas(antes, depois)
            if (zeradas.isNotEmpty()) enviar(ctx, 1930, if (zeradas.size == 1) "Turma em dia" else "${zeradas.size} turmas em dia",
                "Sem pendências agora: " + zeradas.joinToString("; "))
        }
        if (extras.alertaAntiga) {
            val antigas = SgeAlertas.pendenciasAntigas(depois, LocalDate.now(), extras.diasAntiga)
            val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
            val hoje = LocalDate.now().toString()
            // No máximo um alerta por dia.
            if (antigas.isNotEmpty() && prefs.getString("extras_alerta_antiga_dia", "") != hoje) {
                prefs.edit().putString("extras_alerta_antiga_dia", hoje).apply()
                enviar(ctx, 1931, "Pendências antigas (${extras.diasAntiga}+ dias)", antigas.take(8).joinToString("\n") +
                    if (antigas.size > 8) "\ne mais ${antigas.size - 8}…" else "")
            }
        }
    }

    fun resumoSemanal(ctx: Context) {
        enviar(ctx, 1932, "Resumo da semana", SgeAlertas.resumoSemanal(SgePainelStore.ler(ctx).turmas))
    }

    fun lembreteBimestre(ctx: Context, bimestre: Int) {
        enviar(ctx, 1933, "Planejamento do ${bimestre}º bimestre",
            "O ${bimestre}º bimestre começa em cerca de uma semana (data aproximada). Confira o plano de curso e os planos de aula.")
    }
}

/** Roda uma vez por dia: resumo na segunda-feira e lembrete uma semana antes de cada bimestre. */
class SgeExtrasWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val extras = SgeExtras.ler(ctx)
        val hoje = LocalDate.now()
        if (extras.resumoSemanal && hoje.dayOfWeek == DayOfWeek.MONDAY) SgeNotificacoes.resumoSemanal(ctx)
        if (extras.lembreteBimestre) SgeAlertas.bimestreComecandoEm(hoje)?.let { SgeNotificacoes.lembreteBimestre(ctx, it) }
        return Result.success()
    }

    companion object {
        private const val NOME = "sge_extras_diario"
        /** Agenda para as 8h do próximo dia; cancela se nenhum aviso periódico estiver ligado. */
        fun agendar(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            val e = SgeExtras.ler(ctx)
            if (!e.resumoSemanal && !e.lembreteBimestre) { wm.cancelUniqueWork(NOME); return }
            val agora = LocalDateTime.now()
            var alvo = agora.toLocalDate().atStartOfDay().plusHours(8)
            if (!alvo.isAfter(agora)) alvo = alvo.plusDays(1)
            wm.enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SgeExtrasWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(Duration.between(agora, alvo).toMillis(), TimeUnit.MILLISECONDS).build())
        }
    }
}

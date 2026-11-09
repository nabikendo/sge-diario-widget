package br.to.seduc.sgediariowidget.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.SpannableStringBuilder
import br.to.seduc.sgediariowidget.MainActivity
import br.to.seduc.sgediariowidget.R

/** Atualizações manuais mostram só progresso, sem nomes de turmas ou dados escolares. */
internal class SgeProgressoNotificacao(private val ctx: Context, private val tag: String, private val coordenacao: Boolean) {
    private val manager = ctx.getSystemService(NotificationManager::class.java)

    fun mostrar(feitas: Int, total: Int) {
        val manager = manager ?: return
        if (!manager.areNotificationsEnabled()) return
        val titulo = if (coordenacao) "Verificando turmas" else "Atualizando meu diário"
        val percentual = if (total > 0) (100 * feitas.toLong() / total).toInt().coerceIn(0, 100) else 0
        val texto = if (total > 0) "$feitas de $total consultas processadas" else "Preparando consulta…"
        val abrir = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("sge_atalho", if (coordenacao) "coordenacao" else "diario")
        }
        runCatching {
            manager.createNotificationChannel(NotificationChannel(CANAL, "Progresso da consulta", NotificationManager.IMPORTANCE_LOW))
            val builder = Notification.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_widget_refresh)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setContentIntent(PendingIntent.getActivity(ctx, if (coordenacao) 1931 else 1930, abrir,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setTimeoutAfter(4 * 60_000L)
                .setProgress(100, percentual, total == 0)
            if (Build.VERSION.SDK_INT >= 37 && total > 0 && manager.canPostPromotedNotifications()) {
                val textoInformativo = SpannableStringBuilder()
                    .append("$feitas de $total", Notification.createSemanticStyleAnnotation(Notification.SEMANTIC_STYLE_INFO), 0)
                    .append(" consultas processadas")
                builder.setContentText(textoInformativo)
                    .setRequestPromotedOngoing(true)
                    .setShortCriticalText("$percentual%")
                    .setStyle(Notification.ProgressStyle().setProgress(percentual))
            }
            manager.notify(tag, ID, builder.build())
        }
    }

    fun encerrar() { manager?.cancel(tag, ID) }

    private companion object {
        const val CANAL = "progresso_consulta"
        const val ID = 1930
    }
}

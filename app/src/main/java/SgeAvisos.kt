package br.to.seduc.sgediariowidget.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.to.seduc.sgediariowidget.MainActivity
import br.to.seduc.sgediariowidget.R
import br.to.seduc.sgediariowidget.data.SgePainelStore

object SgeAvisos {
    /** No máximo um aviso a cada 12 horas, para não incomodar. */
    fun sessaoExpirada(ctx: Context) {
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        val agora = System.currentTimeMillis()
        if (agora - prefs.getLong("aviso_sessao_ts", 0L) < 12L * 3600 * 1000) return
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        prefs.edit().putLong("aviso_sessao_ts", agora).apply()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("sessao", "Sessão do SGE", NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "sessao").setSmallIcon(R.drawable.ic_widget_refresh)
            .setContentTitle("Sessão do SGE expirada")
            .setContentText("A verificação automática parou. Abra o app e entre novamente no SGE.")
            .setContentIntent(abrir).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1818, n) }
    }
}

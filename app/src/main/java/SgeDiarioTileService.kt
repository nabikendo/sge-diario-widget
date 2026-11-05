package br.to.seduc.sgediariowidget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Atalho opcional do painel rápido do Android para voltar ao diário pessoal. */
class SgeDiarioTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Meu diário"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val abrir = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("sge_atalho", "diario")
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pendente = PendingIntent.getActivity(this, 410, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendente)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(abrir)
        }
    }
}

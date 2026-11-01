package br.to.seduc.sgediariowidget.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType

data class EconomiaSge(val wifi: Boolean = false, val carregando: Boolean = false, val bateria: Boolean = true, val horas: Int = 2, val horaNoturna: Int = 3)
object SgeEconomia {
    val chaves = setOf("economia_wifi", "economia_carregando", "economia_bateria", "economia_horas", "economia_hora_noturna")
    fun ler(ctx: Context): EconomiaSge = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, 0).let { EconomiaSge(it.getBoolean("economia_wifi", false), it.getBoolean("economia_carregando", false), it.getBoolean("economia_bateria", true), it.getInt("economia_horas", 2).takeIf { h -> h in listOf(2,6,12,24) } ?: 2, it.getInt("economia_hora_noturna", 3).coerceIn(0,23)) }
    fun salvar(ctx: Context, d: EconomiaSge) { require(d.horas in listOf(2,6,12,24) && d.horaNoturna in 0..23); ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, 0).edit().putBoolean("economia_wifi", d.wifi).putBoolean("economia_carregando", d.carregando).putBoolean("economia_bateria", d.bateria).putInt("economia_horas", d.horas).putInt("economia_hora_noturna", d.horaNoturna).apply() }
    fun restricoes(ctx: Context): Constraints = ler(ctx).let { Constraints.Builder().setRequiredNetworkType(if (it.wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresCharging(it.carregando).setRequiresBatteryNotLow(it.bateria).build() }
}

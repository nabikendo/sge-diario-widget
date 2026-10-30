package br.to.seduc.sgediariowidget.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SgeSummary(
    val conteudos: Int = 0,
    val frequencia: Int = 0,
    val planosAula: Int = 0,
    val planoCurso: Int = 0,
    val peis: Int = 0,
    val detalhes: String = "Sincronize para verificar o SGE 3.0",
    val ultimaAtualizacao: String = "--:--",
    val sessaoAtiva: Boolean = false
) {
    val totalPendencias: Int
        get() = conteudos + frequencia + planosAula + planoCurso + peis
}

object SgeRepository {
    private const val PREFS_NAME = "sge_diario_prefs"

    fun salvarResumo(context: Context, summary: SgeSummary) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hora = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        prefs.edit()
            .putInt("conteudos", summary.conteudos)
            .putInt("frequencia", summary.frequencia)
            .putInt("planosAula", summary.planosAula)
            .putInt("planoCurso", summary.planoCurso)
            .putInt("peis", summary.peis)
            .putString("detalhes", summary.detalhes)
            .putString("ultimaAtualizacao", hora)
            .putBoolean("sessaoAtiva", summary.sessaoAtiva)
            .apply()
    }

    fun lerResumo(context: Context): SgeSummary {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SgeSummary(
            conteudos = prefs.getInt("conteudos", 0),
            frequencia = prefs.getInt("frequencia", 0),
            planosAula = prefs.getInt("planosAula", 0),
            planoCurso = prefs.getInt("planoCurso", 0),
            peis = prefs.getString("peis_str", null)?.toIntOrNull() ?: prefs.getInt("peis", 0),
            detalhes = prefs.getString("detalhes", "Toque para autenticar no SGE 3.0") ?: "",
            ultimaAtualizacao = prefs.getString("ultimaAtualizacao", "--:--") ?: "--:--",
            sessaoAtiva = prefs.getBoolean("sessaoAtiva", false)
        )
    }

    fun lerSeletorCss(context: Context, chave: String, padrao: String): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(chave, padrao) ?: padrao
    }
}

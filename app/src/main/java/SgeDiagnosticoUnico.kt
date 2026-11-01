package br.to.seduc.sgediariowidget.data

import android.content.Context
import android.os.Build
import java.time.OffsetDateTime

object SgeDiagnosticoUnico {
    fun gerar(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, 0)
        val p = SgePainelStore.ler(ctx); val c = SgeCoordStore.ler(ctx); val d = SgeDocentesStore.ler(ctx); val e = SgeEconomia.ler(ctx)
        return buildString {
            appendLine("SGE Diário · diagnóstico único v${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}")
            appendLine("${OffsetDateTime.now()} · Android ${Build.VERSION.SDK_INT} · ${ctx.packageName}")
            appendLine("Perfil observado: ${SgeSessaoStore.perfil(ctx).titulo}. Os painéis usam consultas independentes.")
            appendLine("Automático: ${e.horas}h; rede sem franquia=${e.wifi}; carregando=${e.carregando}; bateria suficiente=${e.bateria}")
            appendLine("Noturna=${prefs.getBoolean("coord_noturna", false)}; hora=${e.horaNoturna}h. ${prefs.getString("coord_noturna_status", "")}")
            appendLine("Professores: ${d.professores.size}; vínculos: ${d.vinculos.size}; vínculos locais: ${d.locais.size}. ${d.mensagem}")
            fun painel(nome: String, mensagem: String, rs: List<ResultadoTurma>) {
                appendLine("\n$nome: $mensagem · ${rs.size} turma(s)/componente(s)")
                rs.forEachIndexed { i, r -> r.modulos.forEachIndexed { m, v -> if (v.quantidade == null || !v.coberturaCompleta) appendLine("Consulta ${i+1}, ${r.turma.disciplina}, ${SgeDashboard.nomes.getOrElse(m) { "Módulo" }}: ${v.resumo()} · ${v.detalhe}") } }
            }
            painel("Professor", p.mensagem, p.turmas)
            painel("Coordenação", c.mensagem, c.turmas.flatMap { it.disciplinas })
            c.turmas.filter { it.erro != null }.forEachIndexed { i,t -> appendLine("Turma com erro ${i+1}: ${t.erro}") }
            listOf("diagnostico_pessoal_v2", "diagnostico_coord_v2").forEach { k -> appendLine("\n$k: ${prefs.getString(k, "Sem consulta recente nesta origem.")}") }
            if (!prefs.contains("diagnostico_pessoal_v2") && !prefs.contains("diagnostico_coord_v2")) appendLine(prefs.getString("diagnostico_v1", ""))
        }
    }
}

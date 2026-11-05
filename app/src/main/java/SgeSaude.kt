package br.to.seduc.sgediariowidget.data

import android.content.Context
import androidx.work.WorkManager
import java.time.LocalDateTime

data class LinhaSaude(val titulo: String, val texto: String, val alerta: Boolean = false)

object SgeSaude {
    private val rede = listOf("tempo", "timeout", "timed out", "unable to resolve", "failed to connect", "connect", "network", "rede", "internet", "unexpected end", "ssl", "socket")
    fun falhaDeRede(detalhe: String) = rede.any { detalhe.contains(it, ignoreCase = true) }

    /** Muitas telas não reconhecidas (e não por rede) indicam que o SGE mudou: melhor avisar do que mostrar números duvidosos. */
    fun sgeMudou(rs: List<ResultadoTurma>): Boolean {
        val mods = rs.flatMap { it.modulos }
        if (mods.size < 5) return false
        val estrutura = mods.count { it.quantidade == null && it.detalhe.startsWith("Não verificado") && !falhaDeRede(it.detalhe) }
        return estrutura >= 5 && estrutura * 10 >= mods.size * 6
    }

    private fun ultima(rs: List<ResultadoTurma>, agora: LocalDateTime) = SgeIdade.maisRecente(rs, agora)?.let { SgeIdade.texto(it) } ?: "sem consulta"

    private fun ultimaComContagem(rs: List<ResultadoTurma>, agora: LocalDateTime): String {
        val instantes = rs.flatMap { r ->
            val modulos = r.modulos.filter { it.quantidade != null }
            val individuais = modulos.mapNotNull { it.atualizadoMs.takeIf { ms -> ms > 0L } }
            if (individuais.isNotEmpty()) individuais else if (modulos.isNotEmpty()) {
                listOfNotNull(SgeIdade.parse(r.atualizado, agora)?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli())
            } else emptyList()
        }
        val instante = instantes.maxOrNull() ?: return "sem contagem confirmada"
        val horas = SgeIdade.horas(instante) ?: 0L
        return "${SgeIdade.dataHora(instante)} (${SgeIdade.texto(horas)})"
    }

    fun linhas(ctx: Context): List<LinhaSaude> {
        val agora = LocalDateTime.now()
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        val pessoal = SgePainelStore.ler(ctx).turmas
        val escola = SgeCoordStore.ler(ctx).turmas.flatMap { it.disciplinas }
        val todos = pessoal + escola
        val saida = mutableListOf<LinhaSaude>()
        saida += LinhaSaude("Sessão", SgeSessaoStore.perfil(ctx).titulo, SgeSessaoStore.perfil(ctx) == PerfilSge.EXPIRADO)
        saida += LinhaSaude("Últimos dados recebidos", "Seu diário: ${ultima(pessoal, agora)} · Coordenação: ${ultima(escola, agora)}")
        saida += LinhaSaude("Última leitura com contagem", "Seu diário: ${ultimaComContagem(pessoal, agora)} · Coordenação: ${ultimaComContagem(escola, agora)}")
        SgeDashboard.nomes.forEachIndexed { i, nome ->
            val ms = todos.distinctBy(::chaveResultado).mapNotNull { it.modulos.getOrNull(i) }
            val ok = ms.count { it.quantidade != null && (it.coberturaCompleta || it.naoConfigurado) }
            val falha = ms.count { it.quantidade == null }
            val parcial = ms.size - ok - falha
            saida += LinhaSaude(nome, if (ms.isEmpty()) "sem consulta" else "$ok ok · $parcial parciais · $falha com falha", falha > 0)
        }
        SgeFila.ler(ctx)?.let { f -> saida += LinhaSaude("Fila da coordenação", if (f.pausada) "pausada, restam ${f.ids.size}" else "andando, restam ${f.ids.size}") }
        prefs.getString("coord_noturna_status", "")?.takeIf { it.isNotBlank() }?.let {
            val alerta = falhaDeRede(it) || it.contains("falha", true) || it.contains("esgotado", true) || it.contains("expirada", true) || it.contains("não concluída", true)
            saida += LinhaSaude("Verificação noturna", it, alerta)
        }
        if (sgeMudou(todos)) saida += LinhaSaude("Atenção", "O SGE parece ter mudado: várias telas não foram reconhecidas. Atualize o app.", true)
        SgeDiagnosticoAgrupado.linhas(todos).take(3).forEach { saida += LinhaSaude("Falha recente", it, true) }
        return saida
    }

    /** Limpa o que pode travar (consultas, fila e cache) sem apagar painéis, histórico, vínculos nem preferências. */
    fun reparar(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        listOf("sge_sync_now", "sge_coord", "sge_docentes_lista", "sge_docentes_horarios").forEach { wm.cancelUniqueWork(it) }
        SgeFila.salvar(ctx, null)
        SgeCachePlanos.limpar(ctx)
        SgePainelStore.salvar(ctx, SgePainelStore.ler(ctx).copy(sincronizando = false, inicioConsulta = 0L))
        SgeCoordStore.salvar(ctx, SgeCoordStore.ler(ctx).copy(sincronizando = false, inicioConsulta = 0L))
        SgeDocentesStore.salvar(ctx, SgeDocentesStore.ler(ctx).copy(sincronizando = false))
    }
}

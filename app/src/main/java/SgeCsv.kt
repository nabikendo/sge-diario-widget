package br.to.seduc.sgediariowidget.data

import java.time.LocalDateTime

/** Planilha para reunião pedagógica: separador ";" e BOM, para abrir direto no Excel em português. */
object SgeCsv {
    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"").replace(Regex("[\\r\\n]+"), " ") + "\""
    fun situacao(r: ResultadoTurma): String = when {
        r.modulos.any { (it.quantidade ?: 0) > 0 } -> "Pendências"
        r.modulos.any { it.quantidade == null || (!it.coberturaCompleta && !it.naoConfigurado) } -> "A conferir"
        else -> "Em dia"
    }
    fun gerar(grupos: List<GrupoPendencias>, agora: LocalDateTime): String {
        val cab = listOf("Professor", "Turma", "Componente") + SgeDashboard.nomes + listOf("Situação", "Dados de", "Idade do dado (h)")
        val sb = StringBuilder("\uFEFF").append(cab.joinToString(";") { esc(it) }).append("\r\n")
        grupos.forEach { g ->
            val prof = if (g.titulo == "Docência compartilhada") g.subtitulo else g.titulo
            g.resultados.distinctBy(::chaveResultado).sortedBy { it.turma.nome }.forEach { r ->
                val campos = listOf(prof, r.turma.nome.substringBefore('/'), r.turma.disciplina) +
                    (0 until SgeDashboard.nomes.size).map { r.modulos.getOrNull(it)?.resumo() ?: "—" } +
                    listOf(situacao(r), r.atualizado, SgeIdade.horas(r.atualizado, agora)?.toString() ?: "")
                sb.append(campos.joinToString(";") { esc(it) }).append("\r\n")
            }
        }
        return sb.toString()
    }
}

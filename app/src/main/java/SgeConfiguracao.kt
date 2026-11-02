package br.to.seduc.sgediariowidget.data

/** Configurações que o professor ainda precisa definir no SGE para que os documentos possam ser conferidos. */
object SgeConfiguracao {
    private fun bonito(t: String) = t.lowercase().replaceFirstChar { it.uppercase() }
    fun pendencias(resultados: List<ResultadoTurma>): List<String> {
        val unicos = resultados.distinctBy(::chaveResultado)
        return listOf(2 to "do plano de aula", 4 to "do PEI", 3 to "do plano de curso").mapNotNull { (i, rotulo) ->
            val itens = unicos.filter { it.modulos.getOrNull(i)?.naoConfigurado == true }
                .map { "${bonito(it.turma.disciplina)} ${it.turma.nome.substringBefore('/')}" }
            if (itens.isEmpty()) null
            else "Definir a periodicidade $rotulo em: ${itens.take(8).joinToString(", ")}${if (itens.size > 8) " e mais ${itens.size - 8}" else ""}."
        }
    }
}

/** Diagnóstico enxuto: consultas com o mesmo módulo e o mesmo motivo viram uma única linha com contagem. */
object SgeDiagnosticoAgrupado {
    private fun motivo(m: ResultadoModulo): String = when {
        m.naoConfigurado -> "periodicidade não definida"
        else -> m.detalhe.replace(Regex("\\s+"), " ").replace(Regex("\\d+"), "#").trim().take(110)
    }
    fun linhas(resultados: List<ResultadoTurma>): List<String> {
        data class Chave(val modulo: Int, val motivo: String, val resumo: String)
        val grupos = LinkedHashMap<Chave, MutableList<String>>()
        resultados.forEach { r -> r.modulos.forEachIndexed { i, m ->
            if (m.quantidade == null || !m.coberturaCompleta)
                grupos.getOrPut(Chave(i, motivo(m), if (m.naoConfigurado) "n/c" else m.resumo())) { mutableListOf() }.add("${r.turma.disciplina} ${r.turma.nome.substringBefore('/')}")
        } }
        return grupos.map { (k, itens) ->
            val modulo = SgeDashboard.nomes.getOrElse(k.modulo) { "Módulo" }
            if (itens.size == 1) "$modulo (${itens[0]}): ${k.resumo} · ${k.motivo}"
            else "${itens.size} consultas com ${k.motivo.ifBlank { "cobertura incompleta" }} · $modulo (${k.resumo})"
        }.sortedByDescending { it.substringBefore(' ').toIntOrNull() ?: 1 }
    }
}

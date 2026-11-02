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

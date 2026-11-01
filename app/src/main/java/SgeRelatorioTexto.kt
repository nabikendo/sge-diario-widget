package br.to.seduc.sgediariowidget.data

object SgeRelatorioTexto {
    fun gerar(g: GrupoPendencias): String = buildString {
        appendLine("SGE Diário · ${g.titulo}"); appendLine(g.subtitulo)
        if (g.compartilhado) appendLine("Docência compartilhada: os registros aparecem uma vez, sem atribuição de autoria individual.")
        if (g.vinculoLocal) appendLine("Agrupamento com vínculos informados no aparelho.")
        appendLine()
        g.modulos.forEach { m ->
            append("${SgeDashboard.nomes[m.indice]}: ${m.valor()}")
            if (m.quantidade != null) append(" ${m.unidade()}")
            if (m.indice >= 2) append("; aguardando avaliação: ${m.aguardando}; aprovados: ${m.aprovados}")
            appendLine()
        }
        appendLine(); appendLine("POR TURMA E COMPONENTE")
        g.resultados.distinctBy(::chaveResultado).forEach { r ->
            appendLine("${r.turma.nome} · ${r.turma.disciplina} (consulta: ${r.atualizado})")
            (0..4).forEach { i ->
                val m = r.modulos.getOrNull(i) ?: ResultadoModulo()
                append("${SgeDashboard.nomes[i]}: ${m.resumo()}")
                if (m.quantidade != null) append(if (i < 2) if (m.quantidade == 1) " dia" else " dias" else if (m.quantidade == 1) " documento" else " documentos")
                appendLine()
            }
            appendLine()
        }
        appendLine("* cobertura parcial; — não verificado. Aguardando avaliação não conta como pendência de envio.")
        append("Conteúdos e frequência contam dias por turma/componente; planos e PEI contam documentos. Dados da última consulta. Correções no próprio SGE.")
    }
}

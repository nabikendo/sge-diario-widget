package br.to.seduc.sgediariowidget.data

object SgeRelatorioTexto {
    fun gerar(g: GrupoPendencias, recorte: String = ""): String = buildString {
        appendLine("SGE Diário · ${g.titulo}"); appendLine(g.subtitulo)
        if (recorte.isNotBlank()) appendLine("Filtro aplicado: $recorte")
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

    /** Texto curto para colar no WhatsApp; resultados não verificados nunca são apresentados como pendência confirmada. */
    fun mensagemProfessor(g: GrupoPendencias): String = buildString {
        val nome = if (g.chave.startsWith("professor:")) "Prof. ${g.titulo}" else "Professor(a)"
        appendLine("Olá, $nome. No levantamento mais recente do SGE Diário, veja os itens para conferir:")
        val itens = g.resultados.distinctBy(::chaveResultado).mapNotNull { r ->
            val pendencias = (0..4).mapNotNull { indice ->
                val m = r.modulos.getOrNull(indice) ?: return@mapNotNull "${SgeDashboard.nomes[indice]}: não verificado; conferir no SGE"
                val quantidade = m.quantidade
                when {
                    quantidade != null && quantidade > 0 -> {
                        val unidade = if (indice < 2) if (quantidade == 1) "dia" else "dias" else if (quantidade == 1) "documento" else "documentos"
                        "${SgeDashboard.nomes[indice]}: $quantidade $unidade pendente(s)" + if (m.coberturaCompleta) "" else " (cobertura parcial)"
                    }
                    quantidade == null || (!m.coberturaCompleta && !m.naoConfigurado) -> "${SgeDashboard.nomes[indice]}: cobertura a conferir no SGE"
                    else -> null
                }
            }
            if (pendencias.isEmpty()) null else "${r.turma.nome} · ${r.turma.disciplina}: ${pendencias.joinToString("; ")}"
        }
        if (itens.isEmpty()) appendLine("Não há pendências confirmadas neste recorte. Confira no SGE qualquer item não verificado.")
        else itens.forEach { appendLine("• $it") }
        append("A consulta é um apoio; confirme os registros e faça as correções diretamente no SGE.")
    }
}

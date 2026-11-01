package br.to.seduc.sgediariowidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.to.seduc.sgediariowidget.data.*
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** PDF A4 paginado, sem nomes de alunos nem credenciais e sem dependências de rede. */
object SgeRelatorioPdf {
    fun criar(diretorio: File, grupo: GrupoPendencias, fonte: FontePainel): File {
        diretorio.mkdirs()
        diretorio.listFiles()?.filter { it.name.startsWith("SGE-") && it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val arquivo = File.createTempFile("SGE-relatorio-", ".pdf", diretorio)
        try { arquivo.outputStream().use { escrever(it, grupo, fonte) }; return arquivo }
        catch (e: Exception) { arquivo.delete(); throw e }
    }
    fun escrever(saida: OutputStream, grupo: GrupoPendencias, fonte: FontePainel, agora: LocalDateTime = LocalDateTime.now()) {
        escreverGrupos(saida, listOf(grupo), fonte, agora)
    }
    fun escreverGrupos(saida: OutputStream, grupos: List<GrupoPendencias>, fonte: FontePainel, agora: LocalDateTime = LocalDateTime.now(), recibos: Boolean = false, comRecibo: Boolean = false, escola: String = "", responsavel: String = "", recorte: String = "") {
        require(grupos.isNotEmpty())
        var grupo = grupos.first()
        val pdf = PdfDocument()
        val tinta = Paint(Paint.ANTI_ALIAS_FLAG)
        var numero = 0; var y = 0f; var pagina: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        fun terminar() { pagina?.let { p ->
            tinta.color = Color.rgb(105, 111, 123); tinta.textSize = 9f; tinta.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            p.canvas.drawText("SGE Diário · somente leitura · página $numero", 36f, 811f, tinta)
            pdf.finishPage(p); pagina = null
        } }
        fun novaPagina() {
            terminar(); numero++
            pagina = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, numero).create()); canvas = pagina!!.canvas
            tinta.color = Color.rgb(39, 103, 217); tinta.textSize = 12f; tinta.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            canvas.drawText("SGE DIÁRIO  /  ${if (fonte == FontePainel.COORDENACAO) "COORDENAÇÃO" else "MEU DIÁRIO"}", 36f, 40f, tinta)
            y = 67f
            if (numero > 1) {
                tinta.color = Color.rgb(105, 111, 123); tinta.textSize = 10f; tinta.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                val titulo = grupo.titulo
                val limite = tinta.breakText(titulo, true, 510f, null)
                canvas.drawText(titulo.take(limite) + if (limite < titulo.length) "…" else "", 36f, y, tinta); y += 23f
            }
        }
        fun linha(texto: String, tamanho: Float = 11f, negrito: Boolean = false, cor: Int = Color.rgb(35, 39, 47)) {
            tinta.textSize = tamanho; tinta.typeface = Typeface.create("sans-serif", if (negrito) Typeface.BOLD else Typeface.NORMAL)
            val palavras = texto.replace('\n', ' ').split(Regex("\\s+")); val linhas = mutableListOf<String>(); var atual = ""
            for (palavra in palavras) {
                val candidato = if (atual.isBlank()) palavra else "$atual $palavra"
                if (tinta.measureText(candidato) <= 523f) atual = candidato
                else {
                    if (atual.isNotBlank()) linhas.add(atual)
                    var restante = palavra
                    while (tinta.measureText(restante) > 523f) {
                        val limite = tinta.breakText(restante, true, 523f, null).coerceAtLeast(1)
                        linhas.add(restante.take(limite)); restante = restante.drop(limite)
                    }
                    atual = restante
                }
            }
            if (atual.isNotBlank()) linhas.add(atual)
            for (l in linhas) {
                if (y + tamanho + 5 > 784f) novaPagina()
                tinta.textSize = tamanho; tinta.typeface = Typeface.create("sans-serif", if (negrito) Typeface.BOLD else Typeface.NORMAL); tinta.color = cor
                canvas.drawText(l, 36f, y, tinta); y += tamanho + 6f
            }
        }
        try {
            for (g in grupos) {
            grupo = g
            novaPagina()
            if (!recibos) {
            linha(grupo.titulo, 22f, true); linha(grupo.subtitulo, 12f)
            if (escola.isNotBlank()) linha("Escola: $escola", 11f)
            if (recorte.isNotBlank()) linha("Recorte: $recorte", 10f)
            linha("Gerado em ${agora.format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"))}. Dados da última consulta de cada turma.", 9f)
            y += 8f
            if (grupo.compartilhado) linha("Docência compartilhada: cada turma/componente aparece uma vez. O relatório não atribui autoria individual de registros.", 10f)
            if (grupo.vinculoLocal) linha("Agrupamento com vínculos informados no aparelho.", 10f)
            linha("Resumo por módulo", 14f, true)
            grupo.modulos.forEach { m ->
                linha("${SgeDashboard.nomes[m.indice]}: ${m.valor()}${if (m.quantidade == null) "" else " ${m.unidade()}"}${if (m.indice >= 2) "; aguardando avaliação: ${m.aguardando}; aprovados: ${m.aprovados}" else ""}.")
            }
            y += 8f
            linha("Detalhes por turma e componente", 14f, true)
            grupo.resultados.distinctBy(::chaveResultado).forEach { r ->
                if (y > 645f) novaPagina()
                y += 7f; linha("${r.turma.nome} · ${r.turma.disciplina}", 12f, true)
                linha("Consulta: ${r.atualizado}", 9f)
                (0..4).forEach { i -> val m = r.modulos.getOrNull(i) ?: ResultadoModulo()
                    val unidade = if (m.quantidade == null) "" else if (i < 2) if (m.quantidade == 1) " dia" else " dias" else if (m.quantidade == 1) " documento" else " documentos"
                    linha("${SgeDashboard.nomes[i]}: ${m.resumo()}$unidade${if (i >= 2) "; aguardando: ${m.aguardando}; aprovados: ${m.aprovados}" else ""}.", 10f)
                }
            }
            y += 10f
            linha("Como interpretar", 13f, true)
            linha("— não verificado. * cobertura parcial: o valor conhecido pode aumentar. Zero com cobertura parcial não confirma ausência de pendências. Aguardando avaliação não conta como pendência de envio.", 10f)
            linha("Conteúdos e frequência contam dias por turma e componente. Planos e PEI contam documentos. Lançamentos e correções são feitos no próprio SGE.", 10f)
            }
            if (recibos || comRecibo) {
                if (!recibos) novaPagina()
                linha("RECEBIMENTO E CIÊNCIA", 21f, true)
                y += 10f
                linha("Professor(a): ${grupo.titulo}", 14f, true)
                if (escola.isNotBlank()) linha("Escola: $escola", 12f)
                linha("Relatório gerado em ${agora.format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"))}.", 10f)
                if (recorte.isNotBlank()) linha("Recorte: $recorte", 10f)
                y += 12f
                linha("Turmas e componentes contemplados", 13f, true)
                // O recibo é uma folha de protocolo. A relação completa permanece no relatório anexo.
                val itens = grupo.resultados.map { "${it.turma.nome} · ${it.turma.disciplina}" }.distinct()
                itens.take(8).forEach { linha(it, 10f) }
                if (itens.size > 8) linha("Mais ${itens.size - 8} item(ns), discriminados no relatório anexo.", 10f)
                y += 12f
                linha("Declaro que recebo o relatório de acompanhamento do SGE Diário identificado acima e tomo ciência das informações, pendências e itens a conferir nele indicados.", 12f)
                linha("Os dados refletem a última consulta de cada turma e componente. Itens não verificados ou com cobertura parcial exigem conferência no SGE. O recebimento não confirma a exatidão de itens não verificados.", 10f)
                if (grupo.compartilhado) linha("Inclui docência compartilhada. Os registros são da turma/componente e não indicam autoria individual.", 10f)
                y += 26f
                linha("Data do recebimento: ______ / ______ / __________", 12f)
                y += 32f
                linha("________________________________________________________", 12f)
                linha("Assinatura do(a) professor(a)", 10f)
                y += 25f
                linha("Entregue por: ${responsavel.ifBlank { "____________________________________________" }}", 11f)
                linha("Assinatura de quem entregou: ___________________________________", 11f)
                linha("Anexar o relatório correspondente a esta folha. Não somar relatórios individuais para obter o total da escola.", 9f)
            }
            }
            terminar(); pdf.writeTo(saida)
        } finally { if (pagina != null) terminar(); pdf.close() }
    }
}

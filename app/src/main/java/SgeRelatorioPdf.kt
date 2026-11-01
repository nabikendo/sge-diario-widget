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
        }
        fun linha(texto: String, tamanho: Float = 11f, negrito: Boolean = false, cor: Int = Color.rgb(35, 39, 47)) {
            tinta.textSize = tamanho; tinta.typeface = Typeface.create("sans-serif", if (negrito) Typeface.BOLD else Typeface.NORMAL)
            val palavras = texto.replace('\n', ' ').split(Regex("\\s+")); val linhas = mutableListOf<String>(); var atual = ""
            for (palavra in palavras) {
                val candidato = if (atual.isBlank()) palavra else "$atual $palavra"
                if (tinta.measureText(candidato) <= 523f) atual = candidato
                else { if (atual.isNotBlank()) linhas.add(atual); atual = palavra }
            }
            if (atual.isNotBlank()) linhas.add(atual)
            for (l in linhas) {
                if (y + tamanho + 5 > 784f) novaPagina()
                tinta.textSize = tamanho; tinta.typeface = Typeface.create("sans-serif", if (negrito) Typeface.BOLD else Typeface.NORMAL); tinta.color = cor
                canvas.drawText(l, 36f, y, tinta); y += tamanho + 6f
            }
        }
        try {
            novaPagina()
            linha(grupo.titulo, 22f, true); linha(grupo.subtitulo, 12f)
            linha("Gerado em ${agora.format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"))}. Dados da última consulta de cada turma.", 9f)
            y += 8f
            if (grupo.compartilhado) linha("Docência compartilhada: cada turma/componente aparece uma vez. O relatório não atribui autoria individual de registros.", 10f)
            if (grupo.vinculoLocal) linha("Agrupamento com vínculos informados no aparelho.", 10f)
            linha("Resumo por módulo", 14f, true)
            grupo.modulos.forEach { m ->
                linha("${SgeDashboard.nomes[m.indice]}: ${m.valor()}${if (m.quantidade == null) "" else " ${m.unidade()}"}. Aguardando avaliação: ${m.aguardando}; aprovados: ${m.aprovados}.")
            }
            y += 8f
            linha("Detalhes por turma e componente", 14f, true)
            grupo.resultados.distinctBy(::chaveResultado).forEach { r ->
                if (y > 645f) novaPagina()
                y += 7f; linha("${r.turma.nome} · ${r.turma.disciplina}", 12f, true)
                linha("Consulta: ${r.atualizado}", 9f)
                (0..4).forEach { i -> val m = r.modulos.getOrNull(i) ?: ResultadoModulo()
                    linha("${SgeDashboard.nomes[i]}: ${m.resumo()}${if (m.quantidade == null) "" else if (i < 2) " dia(s)" else " documento(s)"}; aguardando: ${m.aguardando}; aprovados: ${m.aprovados}.", 10f)
                }
            }
            y += 10f
            linha("Como interpretar", 13f, true)
            linha("— não verificado. * cobertura parcial: o valor conhecido pode aumentar. Zero com cobertura parcial não confirma ausência de pendências. Aguardando avaliação não conta como pendência de envio.", 10f)
            linha("Conteúdos e frequência contam dias por turma e componente. Planos e PEI contam documentos. Lançamentos e correções são feitos no próprio SGE.", 10f)
            terminar(); pdf.writeTo(saida)
        } finally { if (pagina != null) terminar(); pdf.close() }
    }
}

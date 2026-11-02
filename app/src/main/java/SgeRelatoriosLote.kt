package br.to.seduc.sgediariowidget

import br.to.seduc.sgediariowidget.data.*
import java.io.File
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ArquivosLote(val zip: File, val impressao: File, val recibos: File, val professores: Int, val planilha: File? = null)
object SgeRelatoriosLote {
    fun criar(pasta: File, grupos: List<GrupoPendencias>, escola: String, responsavel: String, recorte: String): ArquivosLote {
        require(grupos.isNotEmpty())
        pasta.mkdirs()
        pasta.listFiles()?.filter { it.isDirectory && it.name.startsWith("lote-") && it.lastModified() < System.currentTimeMillis()-86_400_000L }?.forEach { it.deleteRecursively() }
        val dir = File(pasta,"lote-${System.currentTimeMillis()}").apply { mkdirs() }
        val agora = LocalDateTime.now()
        try {
            val individuais = grupos.mapIndexed { i,g ->
                val nome = normalizarSge(g.titulo).replace(Regex("[^a-z0-9]+"),"-").take(60).ifBlank { "professor" }
                File(dir,"${i+1}-$nome.pdf").also { f -> f.outputStream().use { SgeRelatorioPdf.escreverGrupos(it,listOf(g),FontePainel.COORDENACAO,agora,comRecibo=true,escola=escola,responsavel=responsavel,recorte=recorte) } }
            }
            val impressao = File(dir,"Relatorios-para-impressao.pdf")
            impressao.outputStream().use { SgeRelatorioPdf.escreverGrupos(it,grupos,FontePainel.COORDENACAO,agora,comRecibo=true,escola=escola,responsavel=responsavel,recorte=recorte) }
            val recibos = File(dir,"Recebimento-e-ciencia.pdf")
            recibos.outputStream().use { SgeRelatorioPdf.escreverGrupos(it,grupos,FontePainel.COORDENACAO,agora,recibos=true,escola=escola,responsavel=responsavel,recorte=recorte) }
            val planilha = File(dir,"SGE-Resumo-para-reuniao.csv").also { it.writeText(SgeCsv.gerar(grupos, agora), Charsets.UTF_8) }
            val zip = File(dir,"SGE-Relatorios-por-professor.zip")
            ZipOutputStream(zip.outputStream()).use { z -> (individuais + impressao + recibos + planilha).forEach { f -> z.putNextEntry(ZipEntry(f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() } }
            return ArquivosLote(zip,impressao,recibos,grupos.size,planilha)
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }
}

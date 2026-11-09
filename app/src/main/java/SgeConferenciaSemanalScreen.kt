package br.to.seduc.sgediariowidget

import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import br.to.seduc.sgediariowidget.data.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TelaConferenciaSemanal(
    coord: CoordPainel, conhecidas: List<Pair<String, String>>, docentes: PainelDocentes,
    semana: LocalDate, aoSemana: (LocalDate) -> Unit
) {
    val ctx = LocalContext.current
    val escopo = SgeEscopo.incluidas(ctx)
    val hoje = LocalDate.now()
    val inicio = SgeConferenciaSemanal.inicio(semana)
    val fim = inicio.plusDays(6)
    val grupos = remember(coord, conhecidas, docentes, inicio, hoje, escopo) {
        fun permitida(nome: String) = categoriaTurma(nome).let { it == "ESCOLARIZACAO" || it in escopo }
        SgeConferenciaSemanal.montar(coord.copy(turmas = coord.turmas.filter { permitida(it.nome) }),
            conhecidas.filter { permitida(it.second) }, docentes, inicio, hoje)
    }
    var profId by rememberSaveable { mutableStateOf<String?>(null) }
    var estado by rememberSaveable { mutableStateOf<SituacaoSemanal?>(null) }
    var menu by remember { mutableStateOf(false) }
    var previa by remember { mutableStateOf<String?>(null) }
    var erro by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    val corrotina = rememberCoroutineScope()
    val grupo = grupos.firstOrNull { it.id == profId } ?: grupos.firstOrNull()
    val visiveis = grupo?.linhas.orEmpty().filter { estado == null || it.situacao == estado }
    fun nomeVisivel(g: GrupoSemanal) = if (g.id.isNotEmpty() && SgeUi.privacidade) SgePrivacidade.iniciais(g.professor) else g.professor

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Conferência semanal", style = MaterialTheme.typography.headlineSmall)
            Text("${inicio.format(SgeConferenciaSemanal.formato)} a ${fim.format(SgeConferenciaSemanal.formato)}")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { aoSemana(inicio.minusWeeks(1)) }) { Text("Semana anterior") }
                TextButton(onClick = { aoSemana(inicio.plusWeeks(1)) }, enabled = !fim.isAfter(hoje) && fim != hoje) { Text("Semana seguinte") }
                TextButton(onClick = { aoSemana(hoje) }, enabled = hoje !in inicio..fim) { Text("Hoje") }
            }
            Text(SgeConferenciaSemanal.nota, style = MaterialTheme.typography.bodySmall)
        }
        if (grupo == null) item { Text("Carregue as turmas e consulte a coordenação para montar o relatório.") }
        else {
            item {
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(nomeVisivel(grupo)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        grupos.forEach { g ->
                            DropdownMenuItem(text = { Text(nomeVisivel(g)) }, onClick = { profId = g.id; menu = false })
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = estado == null, onClick = { estado = null }, label = { Text("Todos") })
                    SituacaoSemanal.entries.forEach { e ->
                        FilterChip(selected = estado == e, onClick = { estado = e }, label = { Text(e.titulo) })
                    }
                }
                Button(enabled = visiveis.isNotEmpty(), onClick = {
                    previa = SgeConferenciaSemanal.texto(grupo.copy(professor = nomeVisivel(grupo), linhas = visiveis), inicio)
                    erro = false
                }) { Text("Prévia do relatório") }
                if (visiveis.isEmpty()) Text("Nenhum item neste filtro.")
            }
            items(visiveis) { linha ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${linha.turma} · ${linha.componente}", style = MaterialTheme.typography.titleSmall)
                        Text("${linha.situacao.titulo} · ${linha.modulo}" + (linha.data?.let { " · ${it.format(SgeConferenciaSemanal.formato)}" } ?: ""),
                            style = MaterialTheme.typography.labelMedium)
                        Text(linha.descricao, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    previa?.let { texto ->
        AlertDialog(
            onDismissRequest = { if (!ocupado) previa = null },
            title = { Text("Prévia do relatório") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (erro) Text("Não foi possível compartilhar o relatório.", color = MaterialTheme.colorScheme.error)
                    Text(texto)
                }
            },
            confirmButton = {
                TextButton(enabled = !ocupado, onClick = {
                    ocupado = true
                    erro = false
                    corrotina.launch {
                        try {
                            val arquivo = withContext(Dispatchers.IO) { SgePdfSemanal.criar(File(ctx.cacheDir, "relatorios"), texto) }
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", arquivo)
                            val enviar = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Relatório semanal", uri) }
                            ctx.startActivity(Intent.createChooser(enviar, "Compartilhar relatório semanal"))
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { erro = true }
                        finally { ocupado = false }
                    }
                }) { Text("PDF") }
            },
            dismissButton = {
                TextButton(enabled = !ocupado, onClick = {
                    erro = runCatching {
                        val enviar = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                        ctx.startActivity(Intent.createChooser(enviar, "Compartilhar texto"))
                    }.isFailure
                }) { Text("Texto") }
            }
        )
    }
}

/** Pagina o mesmo conteúdo da prévia, incluindo as distinções de cobertura. */
private object SgePdfSemanal {
    fun criar(pasta: File, texto: String): File {
        pasta.mkdirs()
        pasta.listFiles()?.filter { it.name.startsWith("SGE-semanal-") && it.lastModified() < System.currentTimeMillis() - 86_400_000L }
            ?.forEach { it.delete() }
        val arquivo = File.createTempFile("SGE-semanal-", ".pdf", pasta)
        val pdf = PdfDocument()
        val tinta = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(26, 45, 37)
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        var pagina: PdfDocument.Page? = null
        var numero = 0
        var y = 796
        fun terminar() {
            pagina?.let {
                it.canvas.drawText("SGE Diário · página $numero", 34f, 816f, tinta)
                pdf.finishPage(it)
                pagina = null
            }
        }
        try {
            texto.lineSequence().forEach { linha ->
                val layout = StaticLayout.Builder.obtain(linha, 0, linha.length, tinta, 527)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
                if (y + layout.height > 786 || pagina == null) {
                    terminar()
                    pagina = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++numero).create())
                    y = 36
                }
                val canvas = checkNotNull(pagina).canvas
                canvas.save()
                canvas.translate(34f, y.toFloat())
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + 4
            }
            terminar()
            arquivo.outputStream().use(pdf::writeTo)
            return arquivo
        } catch (e: Exception) {
            arquivo.delete()
            throw e
        } finally {
            terminar()
            pdf.close()
        }
    }
}

package br.to.seduc.sgediariowidget

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.to.seduc.sgediariowidget.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SgeUiSmokeTest {
    @get:Rule val compose = createComposeRule()
    private fun resultado(id: String, disciplina: String, pendente: Int, parcial: Boolean = false) = ResultadoTurma(
        TurmaMonitorada(id, "$id/EM", "Escola de demonstração", disciplina),
        listOf(ResultadoModulo(pendente, "Conteúdos pendentes: 24/10 e 27/10. Dados ilustrativos.", coberturaCompleta = !parcial),
            ResultadoModulo(1, "Frequência não concluída: 28/10. Dados ilustrativos.", coberturaCompleta = true),
            ResultadoModulo(0, "Documentos consultados nos períodos iniciados.", aguardando = 3, coberturaCompleta = true),
            ResultadoModulo(0, "Plano de curso conferido.", coberturaCompleta = true),
            ResultadoModulo(0, "Nenhuma pendência verificada.", coberturaCompleta = true)), "01/11 10:30")
    private val rs get() = listOf(resultado("13.01", "Química", 2), resultado("23.01", "Física", 0, true))
    private val coord get() = CoordPainel(rs.map { CoordTurma(it.turma.id, it.turma.nome, listOf(it), atualizado = it.atualizado) }, "Duas turmas consultadas. Dados ilustrativos.")
    private val docentes get() = PainelDocentes(listOf(DocenteRef("exemplo", "Docente de exemplo")), rs.map {
        VinculoDocente(it.turma.id, it.turma.disciplina, DocenteRef("exemplo", "Docente de exemplo")) })
    private fun content(body: @Composable () -> Unit) {
        compose.setContent { SgeTema(1) { Column(Modifier.fillMaxSize()) {
            Text("PRÉVIA · DADOS ILUSTRATIVOS", Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
            Box(Modifier.weight(1f)) { body() }
        } } }
    }
    private fun foto(nome: String) {
        compose.waitForIdle()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$nome.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SGEPreview")
        }))
        checkNotNull(resolver.openOutputStream(uri)).use {
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    @Test fun cartaoAbreDetalheDoModulo() {
        content {
            val context = LocalContext.current
            val web = remember { WebView(context).apply { loadData("<html><body>SGE de demonstração</body></html>", "text/html", "utf-8") } }
            DisposableEffect(web) { onDispose { web.destroy() } }
            AppDiario(web, PainelSge(rs, "Duas turmas consultadas. Dados ilustrativos."), coord, docentes,
                rs.map { it.turma.id to it.turma.nome }, false, null, 1, 0, "prévia ilustrativa", {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
        }
        foto("01-painel")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("13.01"))
        foto("01b-cartoes")
        compose.onNodeWithText("13.01").performClick()
        compose.onNodeWithText("PENDÊNCIAS POR MÓDULO").assertExists()
        foto("02-detalhes")
    }
    @Test fun coordenacaoPermiteAgruparPorProfessor() {
        content {
            var por by remember { mutableStateOf(Agrupamento.TURMA) }
            TelaCartoes(FontePainel.COORDENACAO, PainelSge(), coord, docentes, rs.map { it.turma.id to it.turma.nome },
                por, { por = it }, {}, {}, {}, {}, {}, {})
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Professores"))
        compose.onNodeWithText("Professores").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Docente de exemplo"))
        compose.onNodeWithText("Docente de exemplo").assertIsDisplayed()
        foto("03-coordenacao-professor")
    }
    @Test fun introducaoExplicaEstadosEConclui() {
        var terminou = false
        content { TelaIntroducao { terminou = true } }
        foto("04-introducao")
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Conectar ao SGE").assertIsDisplayed().performClick()
        compose.runOnIdle { check(terminou) }
    }
    @Test fun galeriaTemOsSeisFormatos() {
        content { TelaWidgets {} }
        compose.onNodeWithText("Resumo do diário").assertExists()
        compose.onNodeWithText("Por professor").assertExists()
        compose.onNodeWithText("Cartão em foco").assertExists()
        foto("05-galeria-widgets")
    }
}

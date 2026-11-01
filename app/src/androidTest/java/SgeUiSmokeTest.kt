package br.to.seduc.sgediariowidget

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import android.content.ContentValues
import android.provider.MediaStore
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import br.to.seduc.sgediariowidget.widget.*
import kotlinx.coroutines.runBlocking
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
    @OptIn(ExperimentalGlanceApi::class)
    @Test fun widgetsCompactosRenderizamDadosConhecidos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SgePainelStore.salvar(context, PainelSge(rs, "Dados ilustrativos."))
        val resumo = runBlocking { ResumoGlanceWidget().compose(context, size = DpSize(170.dp, 170.dp)) }
        val foco = runBlocking { FocoGlanceWidget().compose(context, size = DpSize(340.dp, 180.dp)) }
        content {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Widgets compactos", style = MaterialTheme.typography.headlineMedium)
                Text("Resumo do diário · 2 × 2", style = MaterialTheme.typography.labelLarge)
                AndroidView(factory = { c -> FrameLayout(c).apply { addView(resumo.apply(c, this)) } }, modifier = Modifier.size(170.dp))
                Text("Cartão em foco · 4 × 2", style = MaterialTheme.typography.labelLarge)
                AndroidView(factory = { c -> FrameLayout(c).apply { addView(foco.apply(c, this)) } }, modifier = Modifier.width(340.dp).height(180.dp))
            }
        }
        compose.onNodeWithText("Widgets compactos").assertIsDisplayed()
        foto("06-widgets-compactos")
    }

    @Composable private fun appExemplo(bloqueio: Boolean = false, liberada: Boolean = false, desbloquear: () -> Unit = {}, papel: Int = 0) {
        val context = LocalContext.current
        val web = remember { WebView(context).apply { loadData("<html><body>SGE de demonstração</body></html>", "text/html", "utf-8") } }
        DisposableEffect(web) { onDispose { web.destroy() } }
        AppDiario(web, PainelSge(rs, "Consulta ilustrativa."), coord, docentes, rs.map { it.turma.id to it.turma.nome },
            false, null, 1, 0, "prévia ilustrativa", {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {},
            papel = papel, perfil = PerfilSge.COORDENACAO, bloqueioCoord = bloqueio, coordDesbloqueada = liberada, aoDesbloquear = desbloquear)
    }
    @Test fun coordenacaoProtegidaNaoExibeDadosAntesDeAutenticar() {
        var liberada by mutableStateOf(false)
        content { appExemplo(bloqueio = true, liberada = liberada, desbloquear = { liberada = true }, papel = 2) }
        compose.onNodeWithText("Coordenação protegida").assertIsDisplayed()
        compose.onNodeWithText("13.01").assertDoesNotExist()
        foto("07-coordenacao-protegida")
        compose.onNodeWithText("Desbloquear").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("13.01"))
        compose.onNodeWithText("13.01").assertExists()
        compose.runOnIdle { liberada = false }
        compose.onNodeWithText("13.01").assertDoesNotExist()
    }
    @Test fun tabletMantemListaAoTrocarDetalhes() {
        assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.screenWidthDp >= 840)
        content { appExemplo() }
        compose.onNodeWithText("Escolha um cartão").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("13.01"))
        compose.onNodeWithText("13.01").performClick()
        compose.onNodeWithText("PENDÊNCIAS POR MÓDULO").assertExists()
        compose.onNodeWithText("Seu diário").assertExists()
        foto("08-tablet-lista-detalhe")
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("23.01"))
        compose.onNodeWithText("23.01").performClick()
        compose.onAllNodesWithText("23.01").assertCountEquals(2)
    }
    @Test fun historicoPreservaCoberturaNaoVerificada() {
        val hoje = LocalDate.of(2026, 11, 1)
        val r = rs.first().copy(modulos = rs.first().modulos.mapIndexed { i, m -> if (i == 2) ResultadoModulo() else m })
        val dias = SgeHistorico.registrar(emptyList(), FontePainel.PESSOAL, listOf(r), hoje)
        content { TelaHistorico(dias, FontePainel.PESSOAL, {}, false, {}, hoje) }
        compose.onNodeWithText("Plano de aula: —", substring = true).assertExists()
        foto("09-historico")
    }
    @Test fun relatorioPdfPaginaEAcessoPeloFileProvider() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val registros = (1..36).map { resultado(it.toString(), "Química", 2, it == 2) }
        val g = SgeDashboard.agrupar(registros, Agrupamento.COMPONENTE).single().copy(titulo = "Docente de exemplo · relatório de demonstração")
        val arquivo = SgeRelatorioPdf.criar(File(ctx.cacheDir, "relatorios"), g, FontePainel.COORDENACAO)
        PdfRenderer(ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY)).use { check(it.pageCount >= 3) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", arquivo)
        ctx.contentResolver.openInputStream(uri)!!.use { val inicio = ByteArray(4); check(it.read(inicio) == 4 && String(inicio) == "%PDF") }
        val resolver = ctx.contentResolver
        val destino = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "relatorio-professor-exemplo.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/SGEPreview")
        })!!
        resolver.openOutputStream(destino)!!.use { out -> arquivo.inputStream().use { it.copyTo(out) } }
    }

    @OptIn(ExperimentalGlanceApi::class)
    @Test fun widgetDaCoordenacaoOcultaDadosQuandoProtegido() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        SgeCoordStore.salvar(ctx, coord); SgeDocentesStore.salvar(ctx, docentes)
        prefs.edit().putBoolean("coord_biometria", true).commit()
        try {
            val rv = runBlocking { ProfessorGlanceWidget().compose(ctx, size = DpSize(340.dp, 240.dp)) }
            lateinit var painel: FrameLayout
            content { AndroidView(factory = { c -> FrameLayout(c).apply { painel = this; addView(rv.apply(c, this)) } }, modifier = Modifier.width(340.dp).height(240.dp)) }
            compose.runOnIdle {
                fun textos(v: android.view.View): List<String> = when(v) {
                    is android.widget.TextView -> listOf(v.text.toString())
                    is android.view.ViewGroup -> (0 until v.childCount).flatMap { textos(v.getChildAt(it)) }
                    else -> emptyList()
                }
                val texto = textos(painel).joinToString(" ")
                check(texto.contains("Coordenação protegida")); check(!texto.contains("Docente de exemplo")); check(!texto.contains("13.01"))
            }
            foto("10-widget-protegido")
        } finally { prefs.edit().remove("coord_biometria").commit() }
    }

    @Test fun mapaDaEscolaMostraModulosEAbreCelula() {
        var selecionado = -1
        content { TelaMapa(rs,rs.map { it.turma.id to it.turma.nome },docentes,FiltroAvancado(),emptyMap(),{},{}) { _,m->selecionado=m } }
        compose.onNodeWithText("Mapa da escola").assertIsDisplayed()
        compose.onNode(hasScrollAction() and hasTestTag("mapa-lista")).performScrollToNode(hasText("13.01/EM"))
        foto("11-mapa-escola")
        compose.onAllNodesWithText("2")[0].performClick()
        compose.runOnIdle { check(selecionado==0) }
    }
    @Test fun loteMostraRecorteEProfessoresAntesDeGerar() {
        var quantidade=0
        content { TelaLote(rs,docentes,FiltroAvancado(),emptyMap(),{}) { gs,_,_,_->quantidade=gs.size } }
        foto("12-relatorios-lote")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Gerar 1 relatório(s) e recibos"))
        compose.onNodeWithText("Gerar 1 relatório(s) e recibos").performClick()
        compose.runOnIdle { check(quantidade==1) }
    }
    @Test fun filtrosAvancadosAplicamModuloESituacao() {
        var aplicado=FiltroAvancado()
        content { FiltrosDialog(FiltroAvancado(modulo=0,estado=2),rs,emptyMap(),docentes,{}) { aplicado=it } }
        compose.onNodeWithText("Aplicar filtros").performClick()
        compose.runOnIdle { check(aplicado.modulo==0 && aplicado.estado==2) }
    }
    @Test fun loteGeraZipPDFsIndividuaisERecibosPaginados() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val grupos=SgeOrganizacao.porProfessor(rs,docentes)
        val lote=SgeRelatoriosLote.criar(File(ctx.cacheDir,"relatorios"),grupos,"Escola de demonstração","Coordenação de exemplo","Todos · dados ilustrativos")
        java.util.zip.ZipFile(lote.zip).use { z->check(z.size()==grupos.size+2);check(z.getEntry("Recebimento-e-ciencia.pdf")!=null) }
        PdfRenderer(ParcelFileDescriptor.open(lote.recibos,ParcelFileDescriptor.MODE_READ_ONLY)).use { check(it.pageCount==grupos.size) }
        PdfRenderer(ParcelFileDescriptor.open(lote.impressao,ParcelFileDescriptor.MODE_READ_ONLY)).use { check(it.pageCount>grupos.size) }
        listOf(lote.impressao,lote.recibos).forEach { arquivo->
            val uri=ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME,arquivo.name);put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,"Download/SGEPreview")
            })!!
            ctx.contentResolver.openOutputStream(uri)!!.use { out->arquivo.inputStream().use { it.copyTo(out) } }
        }
    }
    @Test fun backupRestauraAtomicamenteSemTrocarProtecaoOuSessao() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=ctx.getSharedPreferences(SgePainelStore.PREFS_NAME,0)
        val original=prefs.all
        try {
            SgePainelStore.salvar(ctx,PainelSge(rs));prefs.edit().putBoolean("coord_biometria",true).putString("web_user_agent","agente-local").commit()
            val b=SgeBackup.validar(SgeBackup.criar(prefs.all,"1.8"))
            prefs.edit().remove("painel_v2").commit();SgeBackup.restaurar(ctx,b)
            check(SgePainelStore.ler(ctx).turmas.size==2);check(prefs.getBoolean("coord_biometria",false));check(prefs.getString("web_user_agent","")=="agente-local")
            val invalido=org.json.JSONObject(SgeBackup.criar(prefs.all,"1.8"));invalido.getJSONObject("dados").put("sge_cookies","proibido")
            val antes=prefs.getString("painel_v2","");check(runCatching{ SgeBackup.validar(invalido.toString()) }.isFailure);check(antes==prefs.getString("painel_v2",""))
        } finally { val e=prefs.edit().clear(); original.forEach { (k,v)->when(v) {is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v)} };e.commit() }
    }
    @Test fun atualizadorValidaAPKSignedRealERejeitaDowngradeOuHashDiferente() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val apk=File(ctx.applicationInfo.sourceDir)
        val info=ctx.packageManager.getPackageInfo(ctx.packageName,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
        val o=OfertaAtualizacao(ctx.packageName,info.versionName.orEmpty(),info.longVersionCode,29,"teste.apk",apk.length(),hash,"https://github.com/nabikendo/sge-diario-widget/releases/download/latest/teste.apk")
        check(runCatching{ SgeValidadorApk.validar(ctx,apk,o) }.isFailure) // mesma versão: recusa reinstalar/downgrade
        val anterior=ctx.packageManager.getPackageInfo(ctx.packageName,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).apply { setLongVersionCode(info.longVersionCode-1) }
        SgeValidadorApk.validar(ctx,apk,o,anterior)
        check(runCatching{ SgeValidadorApk.validar(ctx,apk,o.copy(sha256="0".repeat(64)),anterior) }.isFailure)
        check(runCatching{ SgeValidadorApk.validar(ctx,apk,o.copy(pacote="outro.pacote"),anterior) }.isFailure)
    }
    @Test fun mapaBloqueiaNovamenteAposPerderAutorizacao() {
        var liberada by mutableStateOf(true)
        content { appExemplo(bloqueio=true,liberada=liberada,papel=2) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Mapa da escola"))
        compose.onNodeWithText("Mapa da escola").performClick()
        compose.onNodeWithText("Organizar escola, série e turno").assertExists()
        compose.runOnIdle { liberada=false }
        compose.onNodeWithText("Coordenação protegida").assertIsDisplayed()
        compose.onNodeWithText("Organizar escola, série e turno").assertDoesNotExist()
    }

}

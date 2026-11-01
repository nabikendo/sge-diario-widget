package br.to.seduc.sgediariowidget

import android.view.View
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.TipoWidget

data class PedidoWidget(val fonte: FontePainel, val por: Agrupamento, val grupo: String, val evento: Long = System.nanoTime())
private data class RotaSge(val aba: Int, val pagina: String?, val grupo: GrupoPendencias?, val fonte: FontePainel)

@Composable
fun AppDiario(
    web: WebView, painel: PainelSge, coord: CoordPainel, docentes: PainelDocentes, conhecidas: List<Pair<String, String>>,
    introduzir: Boolean, pedido: PedidoWidget?, tema: Int, cor: Int, versao: String,
    aoTema: (Int) -> Unit, aoCor: (Int) -> Unit, aoIntroduzido: () -> Unit,
    aoSincronizarPessoal: () -> Unit, aoVerificarTurmas: (List<String>) -> Unit, aoCarregarTurmas: () -> Unit,
    aoAtualizarDocentes: () -> Unit, aoCopiarDiagnostico: (Boolean) -> Unit, aoCopiarPagina: () -> Unit,
    aoSalvarVinculo: (ResultadoTurma, List<DocenteRef>?) -> Unit,
    aoAdicionarWidget: (TipoWidget) -> Unit, aoCompartilhar: (GrupoPendencias) -> Unit,
    aoWebVisivel: (Boolean) -> Unit
) {
    var aba by rememberSaveable { mutableStateOf(0) }
    var pagina by rememberSaveable { mutableStateOf<String?>(null) }
    var intro by rememberSaveable { mutableStateOf(introduzir) }
    var porPessoal by rememberSaveable { mutableStateOf(Agrupamento.TURMA) }
    var porCoord by rememberSaveable { mutableStateOf(Agrupamento.TURMA) }
    var detalhe by remember { mutableStateOf<GrupoPendencias?>(null) }
    var fonteDetalhe by remember { mutableStateOf(FontePainel.PESSOAL) }
    var vincular by remember { mutableStateOf<ResultadoTurma?>(null) }
    val focus = LocalFocusManager.current
    val saveable = rememberSaveableStateHolder()
    fun grupos(fonte: FontePainel, por: Agrupamento): List<GrupoPendencias> {
        val rs = if (fonte == FontePainel.PESSOAL) painel.turmas else coord.turmas.flatMap { it.disciplinas }
        val gs = SgeDashboard.agrupar(rs, por, docentes.vinculos, docentes.locais)
        return if (fonte == FontePainel.COORDENACAO && por == Agrupamento.TURMA) gs + conhecidas.filter { (id, _) -> gs.none { id in it.turmaIds } }
            .map { (id, nome) -> GrupoPendencias("turma:$id", nome.substringBefore('/'), nome, emptyList(), setOf(id)) } else gs
    }
    LaunchedEffect(pedido) {
        pedido?.let { p ->
            aba = if (p.fonte == FontePainel.COORDENACAO) 1 else 0
            if (aba == 1) porCoord = p.por else porPessoal = if (p.por == Agrupamento.PROFESSOR) Agrupamento.TURMA else p.por
            pagina = null; fonteDetalhe = p.fonte
            detalhe = grupos(p.fonte, p.por).firstOrNull { it.chave == p.grupo }
        }
    }
    LaunchedEffect(painel, coord, docentes) {
        detalhe?.let { atual ->
            grupos(fonteDetalhe, if (fonteDetalhe == FontePainel.PESSOAL) porPessoal else porCoord)
                .firstOrNull { it.chave == atual.chave }?.let { detalhe = it }
        }
    }
    val webVisivel = aba == 2 && pagina == null && detalhe == null && !intro
    LaunchedEffect(webVisivel) { aoWebVisivel(webVisivel) }
    BackHandler(enabled = intro || pagina != null || detalhe != null || aba != 0) {
        when { intro -> { intro = false; aoIntroduzido() }
            pagina != null -> pagina = null
            detalhe != null -> detalhe = null
            aba == 2 && web.canGoBack() -> web.goBack()
            else -> aba = 0 }
    }
    val titulo = when {
        pagina == "ajustes" -> "Ajustes"; pagina == "ajuda" -> "Como interpretar"
        pagina == "widgets" -> "Widgets"; detalhe != null -> "Detalhes"; aba == 2 -> "SGE Web"; else -> "Diário"
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pagina != null || detalhe != null) IconButton(onClick = { if (pagina != null) pagina = null else detalhe = null }) {
                    Icon(SgeIcons.Back, "Voltar", Modifier.size(21.dp))
                } else Icon(SgeIcons.Book, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (pagina == null && detalhe == null && aba != 2) IconButton(
                    onClick = { if (aba == 1) aoVerificarTurmas(conhecidas.map { it.first }) else aoSincronizarPessoal() },
                    enabled = !(if (aba == 1) coord.sincronizando else painel.sincronizando) && (aba != 1 || conhecidas.isNotEmpty())) {
                    if (if (aba == 1) coord.sincronizando else painel.sincronizando) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else Icon(SgeIcons.Refresh, if (aba == 1) "Verificar turmas" else "Atualizar diário", Modifier.size(21.dp))
                }
                if (webVisivel) IconButton(onClick = aoCopiarPagina) { Icon(SgeIcons.Export, "Copiar estrutura da página", Modifier.size(20.dp)) }
                IconButton(onClick = { focus.clearFocus(); pagina = "ajuda" }) { Icon(SgeIcons.Info, "Legendas e ajuda", Modifier.size(21.dp)) }
                IconButton(onClick = { focus.clearFocus(); pagina = "ajustes" }) { Icon(SgeIcons.Settings, "Ajustes", Modifier.size(21.dp)) }
            }
            Box(Modifier.weight(1f)) {
                // O documento do SGE permanece anexado para preservar sessão, rolagem e navegação.
                AndroidView(factory = { web }, modifier = Modifier.fillMaxSize(), update = {
                    it.visibility = if (webVisivel) View.VISIBLE else View.INVISIBLE
                    it.importantForAccessibility = if (webVisivel) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                })
                AnimatedContent(targetState = RotaSge(aba, pagina, detalhe, fonteDetalhe),
                    contentKey = { it.pagina ?: it.grupo?.let { g -> "detalhe:${g.chave}" } ?: "aba:${it.aba}" },
                    transitionSpec = { (fadeIn(tween(170)) + slideInHorizontally(tween(200)) { it / 14 }) togetherWith fadeOut(tween(100)) },
                    label = "navegacao") { rota ->
                    when {
                        rota.pagina == "ajustes" -> TelaAjustes(tema, cor, versao, aoTema, aoCor, { pagina = "widgets" }, { pagina = "ajuda" },
                            { intro = true }, { aoCopiarDiagnostico(aba == 1) })
                        rota.pagina == "ajuda" -> TelaAjuda()
                        rota.pagina == "widgets" -> TelaWidgets(aoAdicionarWidget)
                        rota.grupo != null -> TelaDetalhes(rota.grupo, rota.fonte, docentes,
                            { if (rota.fonte == FontePainel.COORDENACAO) aoVerificarTurmas(rota.grupo.turmaIds.toList()) else aoSincronizarPessoal() },
                            { detalhe = null; pagina = null; aba = 2 }, { vincular = it }, { aoCompartilhar(rota.grupo) })
                        rota.aba != 2 -> saveable.SaveableStateProvider("painel:${rota.aba}") {
                            val fonte = if (rota.aba == 1) FontePainel.COORDENACAO else FontePainel.PESSOAL
                            TelaCartoes(fonte, painel, coord, docentes, conhecidas, if (rota.aba == 1) porCoord else porPessoal,
                                { if (rota.aba == 1) porCoord = it else porPessoal = it },
                                { fonteDetalhe = fonte; detalhe = it; focus.clearFocus() },
                                { if (rota.aba == 1) aoVerificarTurmas(conhecidas.map { it.first }) else aoSincronizarPessoal() },
                                aoCarregarTurmas, aoAtualizarDocentes, { aba = 2 }, { pagina = "ajuda" })
                        }
                        else -> Spacer(Modifier.fillMaxSize())
                    }
                }
            }
            if (pagina == null && detalhe == null) DockDiario(aba) { focus.clearFocus(); aba = it }
        }
        if (intro) TelaIntroducao { abrirSge ->
            intro = false; aoIntroduzido(); pagina = null
            if (abrirSge) { detalhe = null; aba = 2 }
        }
        vincular?.let { resultado -> VinculoDialog(resultado, docentes, { vincular = null }) { ps ->
            aoSalvarVinculo(resultado, ps); vincular = null; detalhe = null
        } }
    }
}

@Composable private fun DockDiario(aba: Int, escolher: (Int) -> Unit) {
    Surface(Modifier.padding(horizontal = 18.dp, vertical = 9.dp).fillMaxWidth(),
        shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp, border = BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Meu diário", "Coordenação", "SGE Web").forEachIndexed { i, label ->
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                    color = if (i == aba) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Column(Modifier.selectable(i == aba, role = Role.Tab, onClick = { escolher(i) }).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val cor = if (i == aba) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(listOf(SgeIcons.Cards, SgeIcons.People, SgeIcons.Globe)[i], null, Modifier.size(21.dp), tint = cor)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = cor, maxLines = 1)
                    }
                }
            }
        }
    }
}

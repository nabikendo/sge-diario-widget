package br.to.seduc.sgediariowidget

import java.time.LocalDate
import android.os.Build
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.TipoWidget

data class PedidoWidget(val fonte: FontePainel, val por: Agrupamento, val grupo: String, val evento: Long = System.nanoTime(), val modulo: Int = -1)
private data class RotaSge(val aba: Int, val pagina: String?, val grupo: GrupoPendencias?, val fonte: FontePainel)

/** Turma → disciplinas em que o professor do grupo leciona (vínculos já lidos do horário). */
fun paresDoProfessor(g: GrupoPendencias, docentes: PainelDocentes): Map<String, Set<String>> =
    docentes.vinculos.filter { it.docente.id in g.professorIds && it.turmaId in g.turmaIds }
        .groupBy({ it.turmaId }, { it.componente }).mapValues { it.value.toSet() }

@Composable
fun AppDiario(
    web: WebView, painel: PainelSge, coord: CoordPainel, docentes: PainelDocentes, conhecidas: List<Pair<String, String>>,
    introduzir: Boolean, pedido: PedidoWidget?, tema: Int, cor: Int, versao: String,
    aoTema: (Int) -> Unit, aoCor: (Int) -> Unit, aoIntroduzido: () -> Unit,
    aoSincronizarPessoal: () -> Unit, aoVerificarTurmas: (List<String>) -> Unit, aoCarregarTurmas: () -> Unit,
    aoAtualizarDocentes: () -> Unit, aoCopiarDiagnostico: (Boolean) -> Unit, aoCopiarPagina: () -> Unit,
    aoSalvarVinculo: (ResultadoTurma, List<DocenteRef>?) -> Unit,
    aoAdicionarWidget: (TipoWidget) -> Unit, aoCompartilhar: (GrupoPendencias,String) -> Unit,
    aoWebVisivel: (Boolean) -> Unit,
    papel: Int = 0, lembrete: Boolean = false, aoPapel: (Int) -> Unit = {}, aoLembrete: (Boolean) -> Unit = {},
    aoSair: () -> Unit = {}, aoLimpar: () -> Unit = {}, aoUrl: (String) -> Unit = {},
    historico: List<DiaHistorico> = emptyList(), perfil: PerfilSge = PerfilSge.DESCONHECIDO,
    bloqueioCoord: Boolean = false, coordDesbloqueada: Boolean = false, aoDesbloquear: () -> Unit = {},
    aoBloqueio: (Boolean) -> Unit = {}, noturna: Boolean = false, aoNoturna: (Boolean) -> Unit = {}, statusNoturno: String = "",
    aoPdf: (GrupoPendencias, FontePainel, String) -> Unit = { _, _, _ -> },
    aoPdfContato: (GrupoPendencias, FontePainel, String) -> Unit = { _, _, _ -> }, recursos: RecursosSge = RecursosSge(),
    horarioLembrete: String = "17:00", aoEscolherHorarioLembrete: () -> Unit = {},
    modoComputador: Boolean = true, aoModoComputador: (Boolean) -> Unit = {},
    aoAtalhoSge: (String, String, String, Int, List<String>, String) -> Unit = { _, _, _, _, _, _ -> },
    pedidoHandoff: PedidoHandoff? = null, aoContextoHandoff: (ContextoHandoff?) -> Unit = {},
    semana: LocalDate = LocalDate.now(), aoSemana: (LocalDate) -> Unit = {}
) {
    val visiveis = remember(papel) { when (papel) { 1 -> setOf(0, 2); 2 -> setOf(1, 2); else -> setOf(0, 1, 2) } }
    var aba by rememberSaveable { mutableStateOf(if (papel == 2) 1 else 0) }
    LaunchedEffect(papel, aba) { if (aba !in visiveis) aba = visiveis.first() }
    var pagina by rememberSaveable { mutableStateOf<String?>(null) }
    var intro by rememberSaveable { mutableStateOf(introduzir) }
    var porPessoal by rememberSaveable { mutableStateOf(Agrupamento.TURMA) }
    var porCoord by rememberSaveable { mutableStateOf(Agrupamento.TURMA) }
    var detalhe by remember { mutableStateOf<GrupoPendencias?>(null) }
    var origemDetalhe by remember { mutableStateOf<String?>(null) }
    var fonteDetalhe by remember { mutableStateOf(FontePainel.PESSOAL) }
    var fonteHistorico by rememberSaveable { mutableStateOf(FontePainel.PESSOAL) }
    var vincular by remember { mutableStateOf<ResultadoTurma?>(null) }
    var filtroPessoal by remember { mutableStateOf(FiltroAvancado()) }
    var filtroCoord by remember { mutableStateOf(FiltroAvancado()) }
    LaunchedEffect(recursos.filtros) {
        recursos.filtros[FontePainel.PESSOAL]?.let { filtroPessoal = it }
        recursos.filtros[FontePainel.COORDENACAO]?.let { filtroCoord = it }
    }
    var filtrosAbertos by remember { mutableStateOf(false) }
    var moduloDetalhe by remember { mutableStateOf(-1) }
    val rsCoord = coord.turmas.flatMap { it.disciplinas }
    val focus = LocalFocusManager.current
    val largo = LocalConfiguration.current.screenWidthDp >= 840
    val ctxApp = LocalContext.current
    // Com o teclado aberto (login no SGE Web) a pílula sai de cena para não espremer a página.
    val tecladoAberto = WindowInsets.ime.getBottom(LocalDensity.current) > 0
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
            pagina = null
            fonteDetalhe = p.fonte
            origemDetalhe = null
            moduloDetalhe = p.modulo
            detalhe = grupos(p.fonte, p.por).firstOrNull { it.chave == p.grupo }
            if (p.modulo >= 0 && detalhe == null) {
                val recorte = FiltroAvancado(modulo = p.modulo)
                if (p.fonte == FontePainel.COORDENACAO) filtroCoord = recorte else filtroPessoal = recorte
            }
        }
    }
    LaunchedEffect(painel, coord, docentes) {
        detalhe?.let { atual ->
            grupos(fonteDetalhe, if (fonteDetalhe == FontePainel.PESSOAL) porPessoal else porCoord)
                .firstOrNull { it.chave == atual.chave }?.let { detalhe = it }
        }
    }
    val autorizado = !bloqueioCoord || coordDesbloqueada
    val protegida = !autorizado && ((pagina == null && (aba == 1 || aba == 2 || (detalhe != null && fonteDetalhe == FontePainel.COORDENACAO))) || pagina in setOf("mapa", "lote", "organizacao", "backup", "semana"))
    LaunchedEffect(pedidoHandoff) {
        val contexto = pedidoHandoff?.contexto ?: return@LaunchedEffect
        if (contexto.aba !in visiveis) return@LaunchedEffect
        aba = contexto.aba
        pagina = if (contexto.semana != null) "semana" else null
        detalhe = null
        origemDetalhe = null
        moduloDetalhe = -1
        if (aba == 1) filtroCoord = filtroCoord.copy(modulo = contexto.modulo)
        else filtroPessoal = filtroPessoal.copy(modulo = contexto.modulo)
    }
    val webVisivel = aba == 2 && pagina == null && detalhe == null && !intro && autorizado
    LaunchedEffect(webVisivel) { aoWebVisivel(webVisivel) }
    SideEffect {
        val contexto = when {
            !autorizado || intro || aba !in 0..1 -> null
            pagina == "semana" -> ContextoHandoff(1, semana = semana)
            pagina == null && detalhe == null ->
                ContextoHandoff(aba, if (aba == 1) filtroCoord.modulo else filtroPessoal.modulo)
            else -> null
        }
        aoContextoHandoff(contexto)
    }
    BackHandler(enabled = intro || pagina != null || detalhe != null || aba != 0) {
        when { intro -> { intro = false; aoIntroduzido() }
            pagina != null -> pagina = null
            detalhe != null -> { detalhe = null; pagina = origemDetalhe; origemDetalhe = null }
            aba == 2 && web.canGoBack() -> web.goBack()
            else -> aba = 0 }
    }
    val titulo = when {
        pagina == "mapa" -> "Mapa da escola"; pagina == "lote" -> "Relatórios"; pagina == "organizacao" -> "Organizar turmas"; pagina == "economia" -> "Sincronização"; pagina == "backup" -> "Backup"; pagina == "ajustes" -> "Ajustes"; pagina == "ajuda" -> "Como interpretar"
        pagina == "semana" -> "Conferência semanal"; pagina == "historico" -> "Histórico"; pagina == "widgets" -> "Widgets"; pagina == "sobre" -> "Sobre"; detalhe != null && !largo -> "Pendências por módulo"; aba == 2 -> "SGE Web"; else -> "Diário"
    }
    val cabecalhoNaTela = pagina == null && detalhe == null && aba != 2
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Com a introdução aberta, o app por trás fica desfocado (Android 12+).
        Row(Modifier.fillMaxSize().then(if (intro && Build.VERSION.SDK_INT >= 31) Modifier.blur(22.dp) else Modifier)) {
        if (largo) RailDiario(aba, visiveis) { focus.clearFocus(); pagina = null; detalhe = null; aba = it }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pagina != null || detalhe != null) IconButton(onClick = { if (pagina != null) pagina = null else { detalhe = null; pagina = origemDetalhe; origemDetalhe = null } }) {
                    Icon(SgeIcons.Back, "Voltar", Modifier.size(21.dp))
                } else if (!cabecalhoNaTela) Icon(SgeIcons.Book, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
                val emDetalhe = detalhe != null && pagina == null && !largo
                if (cabecalhoNaTela) Spacer(Modifier.weight(1f)) else Column(Modifier.weight(1f), horizontalAlignment = if (emDetalhe) Alignment.CenterHorizontally else Alignment.Start) {
                    Text(if (emDetalhe) titulo.uppercase() else titulo,
                        style = if (emDetalhe) MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp) else MaterialTheme.typography.titleMedium)
                    if (!emDetalhe) SeloPerfil(perfil)
                }
                if (webVisivel) {
                    TextButton(onClick = { aoModoComputador(!modoComputador) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (modoComputador) "Celular" else "Computador", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = aoCopiarPagina, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Copiar página", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (emDetalhe) {
                    var menuTopo by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuTopo = true }, enabled = detalhe?.resultados?.isNotEmpty() == true) { Icon(SgeIcons.Export, "Compartilhar", Modifier.size(20.dp)) }
                        DropdownMenu(expanded = menuTopo, onDismissRequest = { menuTopo = false }) {
                            val filtroTopo = if (fonteDetalhe == FontePainel.COORDENACAO) filtroCoord else filtroPessoal
                            val recorteTopo = if (filtroTopo.ativo) descreverFiltro(filtroTopo, docentes) else ""
                            DropdownMenuItem(text = { Text("Texto / WhatsApp") }, onClick = { menuTopo = false; detalhe?.let { aoCompartilhar(it, recorteTopo) } })
                            DropdownMenuItem(text = { Text("Relatório em PDF") }, onClick = { menuTopo = false; detalhe?.let { aoPdf(it, fonteDetalhe, recorteTopo) } })
                            DropdownMenuItem(text = { Text("PDF para contato") }, onClick = { menuTopo = false; detalhe?.let { aoPdfContato(it, fonteDetalhe, recorteTopo) } })
                        }
                    }
                } else {
                IconButton(onClick = { focus.clearFocus(); pagina = "ajuda" }) { Icon(SgeIcons.Info, "Legendas e ajuda", Modifier.size(21.dp)) }
                IconButton(onClick = { focus.clearFocus(); pagina = "ajustes" }) { Icon(SgeIcons.Settings, "Ajustes", Modifier.size(21.dp)) }
                }
            }
            val avisoPerfil = when {
                aba == 0 && perfil == PerfilSge.COORDENACAO -> "O SGE está no perfil Unidade escolar. Para atualizar o seu diário, troque para Servidor no SGE Web."
                aba == 1 && perfil == PerfilSge.PROFESSOR -> "O SGE está no perfil Servidor. Para verificar turmas, troque para Unidade escolar no SGE Web."
                else -> null
            }
            if (avisoPerfil != null && pagina == null && detalhe == null) Surface(color = Color(0xFFFF9500).copy(alpha = .14f), shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth()) {
                Text(avisoPerfil, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
            Box(Modifier.weight(1f)) {
                // O documento do SGE permanece anexado para preservar sessão, rolagem e navegação.
                AndroidView(factory = { web }, modifier = Modifier.fillMaxSize(), update = {
                    it.visibility = if (webVisivel) View.VISIBLE else View.INVISIBLE
                    it.importantForAccessibility = if (webVisivel) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                })
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = if (pagina != null || (detalhe != null && !largo)) 820.dp else 1440.dp).fillMaxSize()) {
                if (protegida) TelaCoordenacaoProtegida(aoDesbloquear) else AnimatedContent(targetState = RotaSge(aba, pagina, detalhe, fonteDetalhe),
                    contentKey = { it.pagina ?: if (largo && it.aba != 2) "aba:${it.aba}" else it.grupo?.let { g -> "detalhe:${g.chave}" } ?: "aba:${it.aba}" },
                    transitionSpec = { (fadeIn(tween(SgeMotion.duration(170))) + slideInHorizontally(tween(SgeMotion.duration(200))) { it / 14 }) togetherWith fadeOut(tween(SgeMotion.duration(100))) },
                    label = "navegacao") { rota ->
                    when {
                        rota.pagina == "ajustes" -> TelaAjustes(tema, cor, versao, aoTema, aoCor, { pagina = "widgets" }, { pagina = "ajuda" },
                            { intro = true }, { aoCopiarDiagnostico(aba == 1) },
                            papel, aoPapel, lembrete, aoLembrete, { pagina = "sobre" }, aoSair, aoLimpar,
                            { fonteHistorico = if (aba == 1) FontePainel.COORDENACAO else FontePainel.PESSOAL; pagina = "historico" },
                            bloqueioCoord, aoBloqueio, noturna, aoNoturna, statusNoturno, { pagina = "economia" }, { pagina = "backup" }, recursos.aoAtualizacoes,
                            horarioLembrete, aoEscolherHorarioLembrete,
                            bimestre = SgeBimestre.ler(ctxApp), aoBimestre = { SgeBimestre.salvar(ctxApp, it) })
                        rota.pagina == "mapa" -> TelaMapa(rsCoord, conhecidas, docentes, filtroCoord, recursos.organizacao,
                            { filtrosAbertos = true }, { pagina = "organizacao" }) { g, m -> fonteDetalhe = FontePainel.COORDENACAO; detalhe = g; moduloDetalhe = m; aba = 1; origemDetalhe = "mapa"; pagina = null }
                        rota.pagina == "lote" -> TelaLote(rsCoord, docentes, filtroCoord, recursos.organizacao, { filtrosAbertos = true }, recursos.aoLote)
                        rota.pagina == "organizacao" -> TelaOrganizacao(conhecidas, rsCoord, recursos.organizacao, recursos.aoOrganizacao)
                        rota.pagina == "economia" -> TelaEconomia(recursos.economia, recursos.aoEconomia)
                        rota.pagina == "backup" -> TelaBackup(recursos.aoBackup, recursos.aoRestaurar)
                        rota.pagina == "semana" -> TelaConferenciaSemanal(coord, conhecidas, docentes, semana, aoSemana)
                        rota.pagina == "historico" -> TelaHistorico(historico, fonteHistorico, { fonteHistorico = it }, !autorizado, aoDesbloquear, docentes = docentes)
                        rota.pagina == "sobre" -> TelaSobre(versao, aoUrl)
                        rota.pagina == "ajuda" -> TelaAjuda()
                        rota.pagina == "widgets" -> TelaWidgets(aoAdicionarWidget)
                        rota.aba != 2 -> {
                            val fonte = if (rota.aba == 1) FontePainel.COORDENACAO else FontePainel.PESSOAL
                            @Composable fun lista() {
                                saveable.SaveableStateProvider("painel:${rota.aba}") {
                                    TelaCartoes(fonte, painel, coord, docentes, conhecidas, if (rota.aba == 1) porCoord else porPessoal,
                                        { if (rota.aba == 1) porCoord = it else porPessoal = it; detalhe = null },
                                        { fonteDetalhe = fonte; detalhe = it; moduloDetalhe = (if (fonte == FontePainel.COORDENACAO) filtroCoord else filtroPessoal).modulo; origemDetalhe = null; focus.clearFocus() },
                                        { if (rota.aba == 1) aoVerificarTurmas(conhecidas.map { it.first }) else aoSincronizarPessoal() },
                                        aoCarregarTurmas, aoAtualizarDocentes, { aba = 2 }, { pagina = "ajuda" },
                                        selecionada = if (largo) rota.grupo?.chave else null,
                                        filtroAvancado = if (rota.aba == 1) filtroCoord else filtroPessoal, organizacao = recursos.organizacao,
                                        aoFiltros = { filtrosAbertos = true }, aoMapa = { pagina = "mapa" }, aoLote = { pagina = "lote" }, aoSemana = { pagina = "semana" },
                                        filtrosSalvos = recursos.filtrosSalvos[fonte].orEmpty(),
                                        aoAplicarFiltro = { f -> if (rota.aba == 1) filtroCoord = f else filtroPessoal = f; recursos.aoFiltro(fonte, f) },
                                        aoSalvarFiltro = { recursos.aoSalvarFiltro(fonte, it) },
                                        aoExcluirFiltro = { recursos.aoExcluirFiltro(fonte, it) },
                                        filaCoord = recursos.filaCoord, aoPausarFila = recursos.aoPausarFila,
                                        aoRetomarFila = recursos.aoRetomarFila, aoTentarFalhas = recursos.aoTentarFalhas, perfil = perfil,
                                        pausaDiario = recursos.pausaDiario, aoPausarDiario = recursos.aoPausarDiario, aoRetomarDiario = recursos.aoRetomarDiario)
                                }
                            }
                            @Composable fun detalhes(g: GrupoPendencias) {
                                val filtroAtual = if (rota.fonte == FontePainel.COORDENACAO) filtroCoord else filtroPessoal
                                val recorte = if (filtroAtual.ativo) descreverFiltro(filtroAtual, docentes) else ""
                                TelaDetalhes(g, rota.fonte, docentes,
                                    { if (rota.fonte == FontePainel.COORDENACAO) {
                                        // Grupo de um professor: sincroniza só as disciplinas dele.
                                        val pares = if (g.professorIds.size == 1) paresDoProfessor(g, docentes) else emptyMap()
                                        if (pares.isNotEmpty()) br.to.seduc.sgediariowidget.worker.SgeSyncWorker.verificarComponentes(ctxApp, pares)
                                        else aoVerificarTurmas(g.turmaIds.toList())
                                    } else aoSincronizarPessoal() },
                                    { detalhe = null; pagina = null; aba = 2 }, { vincular = it }, { aoCompartilhar(g, recorte) }, { aoPdf(g, rota.fonte, recorte) }, { aoPdfContato(g, rota.fonte, recorte) }, moduloDetalhe,
                                    mudancas = if (rota.fonte == FontePainel.COORDENACAO) coord.mudancas else painel.mudancas,
                                    aoAtalhoSge = { r, modulo ->
                                        detalhe = null; pagina = null; aba = 2
                                        val mod = r.modulos.getOrNull(modulo)
                                        val datas = SgeAtalhoWeb.datasPendentes(mod?.detalhe.orEmpty())
                                        val horario = if (modulo==1) SgeAtalhoWeb.horarioPendente(mod?.detalhe.orEmpty()) else ""
                                        aoAtalhoSge(r.turma.id, r.turma.nome, r.turma.disciplina, modulo, datas, horario) })
                            }
                            if (largo) Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(.43f).fillMaxHeight()) { lista() }
                                VerticalDivider(Modifier.fillMaxHeight())
                                Box(Modifier.weight(.57f).fillMaxHeight()) {
                                    AnimatedContent(targetState = rota.grupo, contentKey = { it?.chave },
                                        transitionSpec = {
                                            (fadeIn(tween(SgeMotion.duration(150))) + slideInHorizontally(tween(SgeMotion.duration(180))) { it / 20 }) togetherWith
                                                (fadeOut(tween(SgeMotion.duration(110))) + slideOutHorizontally(tween(SgeMotion.duration(150))) { -it / 24 })
                                        }, label = "detalheLargo") { grupo ->
                                        if (grupo != null) detalhes(grupo) else Column(Modifier.fillMaxSize().padding(30.dp), verticalArrangement = Arrangement.Center) {
                                            Icon(SgeIcons.Cards, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(16.dp)); Text("Escolha um cartão", style = MaterialTheme.typography.headlineSmall)
                                            Text("Os detalhes aparecem aqui. A lista permanece ao lado para você trocar de turma, componente ou professor.", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            } else if (rota.grupo != null) detalhes(rota.grupo) else lista()
                        }
                        else -> Spacer(Modifier.fillMaxSize())
                    }
                }
                } }
            }
            if (!largo && !tecladoAberto && pagina == null && detalhe == null) DockDiario(aba, visiveis) { focus.clearFocus(); aba = it }
        }
        }
        if (intro) TelaIntroducao { abrirSge ->
            intro = false; aoIntroduzido(); pagina = null
            if (abrirSge) { detalhe = null; aba = 2 }
        }
        if (filtrosAbertos && autorizado) FiltrosDialog(if (aba == 1) filtroCoord else filtroPessoal,
            if (aba == 1) rsCoord else painel.turmas, recursos.organizacao, docentes, { filtrosAbertos = false }) {
            if (aba == 1) filtroCoord = it else filtroPessoal = it
            recursos.aoFiltro(if (aba == 1) FontePainel.COORDENACAO else FontePainel.PESSOAL, it)
            detalhe = null
        }
        vincular?.takeIf { autorizado }?.let { resultado -> VinculoDialog(resultado, docentes, { vincular = null }) { ps ->
            aoSalvarVinculo(resultado, ps); vincular = null; detalhe = null
        } }
    }
}

@Composable fun SeloPerfil(perfil: PerfilSge) {
    val cor = when (perfil) {
        PerfilSge.PROFESSOR -> MaterialTheme.colorScheme.primary
        PerfilSge.COORDENACAO -> MaterialTheme.colorScheme.tertiary
        PerfilSge.EXPIRADO -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = cor.copy(alpha = .15f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).background(cor, CircleShape))
            Text("Perfil: ${perfil.titulo}", style = MaterialTheme.typography.labelSmall, color = cor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun RailDiario(aba: Int, visiveis: Set<Int>, escolher: (Int) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.height(12.dp))
        listOf("Meu diário", "Coordenação", "SGE Web").forEachIndexed { i, label ->
            if (i !in visiveis) return@forEachIndexed
            NavigationRailItem(selected = i == aba, onClick = { escolher(i) }, alwaysShowLabel = true,
                icon = { Icon(listOf(SgeIcons.Cards, SgeIcons.People, SgeIcons.Globe)[i], null, Modifier.size(22.dp)) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) })
        }
    }
}

/** Navegação inferior flutuante em pílula: só ícones (rótulos ficam na acessibilidade) e indicador circular no item ativo. */
@Composable private fun DockDiario(aba: Int, visiveis: Set<Int>, escolher: (Int) -> Unit) {
    val escuro = MaterialTheme.colorScheme.background.luminance() < .5f
    val baixa = LocalConfiguration.current.screenHeightDp < 480
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = if (baixa) 6.dp else 18.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), color = (if (escuro) PilulaNavOled else MaterialTheme.colorScheme.surface).copy(alpha = .92f),
            border = BorderStroke(1.dp, if (escuro) PilulaNavBorda else MaterialTheme.colorScheme.outlineVariant), shadowElevation = 4.dp) {
            Row(Modifier.padding(if (baixa) 4.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Meu diário", "Coordenação", "SGE Web").forEachIndexed { i, label ->
                    if (i !in visiveis) return@forEachIndexed
                    val ativa = i == aba
                    val fundo by animateColorAsState(if (ativa) (if (escuro) PilulaNavBorda else MaterialTheme.colorScheme.primaryContainer) else Color.Transparent,
                        tween(SgeMotion.duration(160)), label = "fundoAba$i")
                    val corIcone by animateColorAsState(if (ativa) (if (escuro) Color.White else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(SgeMotion.duration(160)), label = "iconeAba$i")
                    Box(Modifier.size(if (baixa) 44.dp else 52.dp).clip(CircleShape).background(fundo)
                        .selectable(ativa, role = Role.Tab, onClick = { escolher(i) }), contentAlignment = Alignment.Center) {
                        Icon(listOf(SgeIcons.Cards, SgeIcons.People, SgeIcons.Globe)[i], label, Modifier.size(24.dp), tint = corIcone)
                    }
                }
            }
        }
    }
}

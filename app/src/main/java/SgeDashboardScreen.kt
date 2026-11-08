@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package br.to.seduc.sgediariowidget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import br.to.seduc.sgediariowidget.data.*
import java.time.LocalDateTime
import java.util.UUID

@Composable fun corEstado(estado: EstadoGrupo): Color = when (estado) {
    EstadoGrupo.PENDENTE -> MaterialTheme.colorScheme.error
    EstadoGrupo.CONFERIR, EstadoGrupo.SEM_DADOS -> corAlerta()
    EstadoGrupo.EM_DIA -> MaterialTheme.colorScheme.tertiary
}
fun nomeEstado(estado: EstadoGrupo) = when (estado) {
    EstadoGrupo.PENDENTE -> "Pendências"; EstadoGrupo.CONFERIR -> "A conferir"
    EstadoGrupo.EM_DIA -> "Em dia"; EstadoGrupo.SEM_DADOS -> "Sem consulta"
}
@Composable fun iconeModulo(i: Int) = when (i) {
    0 -> SgeIcons.Book; 1 -> SgeIcons.Calendar; 4 -> SgeIcons.User; else -> SgeIcons.Document
}

@Composable
fun EmblemaEstado(estado: EstadoGrupo) {
    val cor = corEstado(estado)
    Surface(color = cor.copy(alpha = .15f), shape = RoundedCornerShape(40.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(if (estado == EstadoGrupo.EM_DIA) SgeIcons.Check else if (estado == EstadoGrupo.PENDENTE) SgeIcons.Clock else SgeIcons.Info,
                null, Modifier.size(13.dp), tint = cor)
            Text(nomeEstado(estado), color = cor, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun CartaoGrupo(grupo: GrupoPendencias, por: Agrupamento, selecionado: Boolean = false,
    mudancas: List<MudancaModulo> = emptyList(), abrir: () -> Unit) {
    val horasDado = remember(grupo) { SgeIdade.maisAntigaPorModulo(grupo.resultados, LocalDateTime.now()) }
    val tituloV = tituloVisivel(grupo)
    val descricao = buildString {
        append(tituloV).append(". ").append(nomeEstado(grupo.estado))
        horasDado?.let { append(". Dados ").append(SgeIdade.texto(it)) }
        append(". Toque para ver detalhes.")
    }
    Surface(onClick = abrir, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = descricao },
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(RaioCartao),
        border = BorderStroke(if (selecionado) 1.5.dp else 1.dp, if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), shadowElevation = .5.dp) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(22.dp)) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        if (por == Agrupamento.TURMA) Text(tituloV.substringBefore(' ').take(6), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        else Icon(if (por == Agrupamento.COMPONENTE) SgeIcons.Book else SgeIcons.User, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tituloV, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtituloVisivel(grupo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                EmblemaEstado(grupo.estado)
            }
            if (horasDado != null) {
                val parcial = grupo.modulos.any { !it.completa && !it.naoConfigurado }
                val corIdade = when { horasDado >= 24 -> MaterialTheme.colorScheme.onSurfaceVariant; parcial -> corAlerta();
                    SgeIdade.faixa(horasDado) == SgeIdade.Faixa.RECENTE -> MaterialTheme.colorScheme.tertiary; else -> corAlerta() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(SgeIcons.Clock, null, Modifier.size(13.dp), tint = corIdade)
                    Text(when { horasDado >= 24 -> "Desatualizado · ${SgeIdade.texto(horasDado)}"; parcial -> "Cobertura parcial · ${SgeIdade.texto(horasDado)}"; else -> "Dados ${SgeIdade.texto(horasDado)}" },
                        style = MaterialTheme.typography.labelSmall, color = corIdade)
                }
            }
            val mudancasVisiveis = if (por == Agrupamento.TURMA) mudancas.filter { it.turmaId in grupo.turmaIds && it.delta != 0 } else emptyList()
            val modulosVisiveis = (grupo.modulos.filter { (it.quantidade ?: 0) > 0 } +
                mudancasVisiveis.mapNotNull { mudanca -> grupo.modulos.firstOrNull { it.indice == mudanca.indice } })
                .distinctBy { it.indice }
            if (modulosVisiveis.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        modulosVisiveis.take(2).forEach { m ->
                            val variacao = mudancasVisiveis.firstOrNull { it.indice == m.indice }
                            val corModulo = if ((m.quantidade ?: 0) <= 0) MaterialTheme.colorScheme.tertiary else if (m.indice == 1) MaterialTheme.colorScheme.error else corAlerta()
                            Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(22.dp)) {
                                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.size(8.dp).background(corModulo, CircleShape))
                                        Text(SgeDashboard.nomes[m.indice], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${m.valor()} ${m.unidade()}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    variacao?.let {
                                        val melhorou = it.delta < 0
                                        val corVariacao = if (melhorou) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                        Surface(color = corVariacao.copy(alpha = .15f), shape = CircleShape) {
                                            Text("${if (melhorou) "↓" else "↑"}${kotlin.math.abs(it.delta)}${if (it.indice < 2) " d" else " doc"}",
                                                Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = corVariacao)
                                        }
                                    }
                                }
                            }
                        }
                        if (modulosVisiveis.size == 1) Spacer(Modifier.weight(1f))
                    }
                    if (modulosVisiveis.size > 2) Text("+ ${contarSge(modulosVisiveis.size - 2, "módulo", "módulos")} com pendência ou mudança", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Text(when(grupo.estado) {
                EstadoGrupo.EM_DIA -> "Tudo conferido nesta consulta."
                EstadoGrupo.SEM_DADOS -> "Toque para consultar esta turma."
                else -> "Existem dados que precisam de conferência."
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (grupo.estado == EstadoGrupo.PENDENTE && grupo.exigeConferencia) Text("Inclui dados a conferir", style = MaterialTheme.typography.labelSmall, color = corAlerta())
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ver detalhes →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                Icon(SgeIcons.Chevron, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun MensagemVazia(titulo: String, texto: String, acao: String? = null, aoAgir: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(RaioCartao)) {
        Column(Modifier.fillMaxWidth().padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(SgeIcons.Cards, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (acao != null) Button(onClick = aoAgir) { Text(acao) }
        }
    }
}

@Composable
fun TelaCartoes(
    fonte: FontePainel, painel: PainelSge, coord: CoordPainel, docentes: PainelDocentes,
    conhecidas: List<Pair<String, String>>, por: Agrupamento, aoAgrupar: (Agrupamento) -> Unit,
    aoAbrir: (GrupoPendencias) -> Unit, aoSincronizar: () -> Unit, aoCarregarTurmas: () -> Unit,
    aoAtualizarDocentes: () -> Unit, aoAbrirSge: () -> Unit, aoAjuda: () -> Unit, selecionada: String? = null, filtroAvancado: FiltroAvancado = FiltroAvancado(), organizacao: Map<String,DadosTurma> = emptyMap(), aoFiltros: ()->Unit = {}, aoMapa: ()->Unit = {}, aoLote: ()->Unit = {},
    filtrosSalvos: List<FiltroSalvo> = emptyList(), aoAplicarFiltro: (FiltroAvancado)->Unit = {}, aoSalvarFiltro: (FiltroSalvo)->Unit = {}, aoExcluirFiltro: (String)->Unit = {},
    filaCoord: FilaCoord? = null, aoPausarFila: ()->Unit = {}, aoRetomarFila: ()->Unit = {}, aoTentarFalhas: ()->Unit = {}, pausaDiario: PausaPessoal? = null, aoPausarDiario: ()->Unit = {}, aoRetomarDiario: ()->Unit = {}, perfil: PerfilSge? = null
) {
    var busca by rememberSaveable(fonte.name) { mutableStateOf("") }
    val filtro = filtroAvancado.estado
    var mostrarBusca by rememberSaveable(fonte.name) { mutableStateOf(false) }
    var menuFiltro by remember { mutableStateOf(false) }
    var mostrarMensagem by remember { mutableStateOf(false) }
    var mostrarComparacao by rememberSaveable(fonte.name) { mutableStateOf(false) }
    var mostrarSalvarFiltro by remember { mutableStateOf(false) }
    var nomeFiltro by remember { mutableStateOf("") }
    var filaExpandida by rememberSaveable(fonte.name) { mutableStateOf(true) }
    val alphaCartoes = remember { Animatable(1f) }
    var agrupamentoAnterior by remember { mutableStateOf(por) }
    LaunchedEffect(por) {
        if (agrupamentoAnterior != por) {
            agrupamentoAnterior = por
            val duracao = SgeMotion.duration(160)
            if (duracao == 0) alphaCartoes.snapTo(1f)
            else {
                alphaCartoes.snapTo(.9f)
                alphaCartoes.animateTo(1f, tween(duracao))
            }
        }
    }
    val focoBusca = remember { FocusRequester() }
    LaunchedEffect(SgeUi.buscaPedida) { if (SgeUi.buscaPedida > 0) mostrarBusca = true }
    LaunchedEffect(mostrarBusca) { if (mostrarBusca) runCatching { focoBusca.requestFocus() } }
    val escola = fonte == FontePainel.COORDENACAO
    val mudancas = if (escola) coord.mudancas else painel.mudancas
    val resultados = remember(escola, coord.turmas, painel.turmas, filtroAvancado, organizacao, docentes) { SgeOrganizacao.filtrar(if (escola) coord.turmas.flatMap { it.disciplinas } else painel.turmas, filtroAvancado, organizacao, docentes) }
    val grupos = remember(resultados, docentes, conhecidas, por, escola, filtroAvancado) {
        val list = SgeDashboard.agrupar(resultados, por, docentes.vinculos, docentes.locais)
        if (escola && por == Agrupamento.TURMA && !filtroAvancado.ativo) list + conhecidas.filter { (id, _) -> list.none { id in it.turmaIds } }
            .map { (id, nome) -> GrupoPendencias("turma:$id", nome.substringBefore('/'), nome.substringAfter('/', "Ainda não consultada"), emptyList(), setOf(id)) }
        else list
    }
    val porTurma = remember(resultados) { SgeDashboard.agrupar(resultados, Agrupamento.TURMA) }
    val pendentes = porTurma.count { it.estado == EstadoGrupo.PENDENTE }
    val coberturaParcial = remember(resultados) { resultados.any { r -> r.modulos.any { !it.coberturaCompleta && !it.naoConfigurado } } }
    val conferir = porTurma.count { it.exigeConferencia } + if (escola) conhecidas.count { (id, _) -> porTurma.none { id in it.turmaIds } } else 0
    val totalPendencias = remember(porTurma) { porTurma.sumOf { g -> g.modulos.sumOf { (it.quantidade ?: 0).coerceAtLeast(0) } } }
    val emDia = porTurma.count { it.estado == EstadoGrupo.EM_DIA }
    val naoConsultadas = if (escola) conhecidas.count { (id, _) -> porTurma.none { id in it.turmaIds } } else 0
    val nomesProf = remember(docentes) { SgeBusca.indiceProfessores(docentes.vinculos, docentes.locais) }
    val sgeMudou = remember(resultados) { SgeSaude.sgeMudou(resultados) }
    val semVinculo = remember(coord.turmas, docentes) { coord.turmas.flatMap { it.disciplinas }.filter { SgeOrganizacao.professores(it, docentes).isEmpty() } }
    val diagnosticosRevisao = remember(docentes.diagnosticos) { docentes.diagnosticos }
    val visiveis = grupos.filter { g ->
        (busca.isBlank() || SgeBusca.combina(busca, g.titulo + " " + g.subtitulo + " " + g.resultados.joinToString { it.turma.nome + " " + it.turma.disciplina + " " + nomesProf(it) })) &&
            when(filtro) { 1 -> g.estado == EstadoGrupo.PENDENTE; 2 -> g.exigeConferencia
                3 -> g.estado == EstadoGrupo.EM_DIA; else -> true }
    }
    val carregando = if (escola) coord.sincronizando else painel.sincronizando
    val mensagemStatus = if (escola) coord.mensagem else painel.mensagem
    val falhaRecente = listOf("Não foi possível", "Sessão expirada", "O perfil ativo", "A consulta demorou", "Turma não encontrada")
        .any { mensagemStatus.startsWith(it, ignoreCase = true) } || mensagemStatus.startsWith("Fila concluída:") ||
        mensagemStatus.contains("não verificada", ignoreCase = true) || mensagemStatus.contains("cobertura parcial", ignoreCase = true)
    val mostrarResumo = mudancas.isNotEmpty() && !falhaRecente
    // Esqueleto só na primeira consulta (sem nenhum dado para mostrar); nas atualizações os cartões permanecem.
    val mostrarEsqueleto = carregando && resultados.isEmpty() && (!escola || conhecidas.isEmpty())
    Crossfade(targetState = mostrarEsqueleto, animationSpec = tween(SgeMotion.duration(220)), label = "esqueletoPainel") { esqueleto ->
    if (esqueleto) DashboardSkeleton() else LazyVerticalGrid(columns = GridCells.Adaptive(285.dp), contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    perfil?.let { SeloPerfil(it) }
                    Text(if (escola) "Coordenação" else "Seu diário", style = MaterialTheme.typography.displaySmall)
                    Text(if (escola) "Uma escola inteira, em recortes mais claros." else "O que precisa de atenção, em um só lugar",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeletorBimestre(fonte, conhecidas.map { it.first })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            HeroResumo(totalPendencias, pendentes, conferir, emDia, porTurma.size + naoConsultadas, porTurma.size, coberturaParcial, escola, carregando,
                podeAtualizar = if (escola) conhecidas.isNotEmpty() else !carregando,
                descricaoAcao = if (carregando) (if (escola) "Verificação em andamento" else "Atualizando o diário") else if (escola) "Verificar turmas" else "Atualizar diário",
                aoAtualizar = aoSincronizar)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AnimatedVisibility(visible = carregando, enter = fadeIn(tween(SgeMotion.duration(130))),
                    exit = fadeOut(tween(SgeMotion.duration(110)))) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(targetState = if (mostrarResumo) {
                        val melhoras = mudancas.count { it.delta < 0 }; val aumentos = mudancas.count { it.delta > 0 }
                        val comparados = mudancas.map { it.turmaId }.distinct().size
                        if (melhoras == 0 && aumentos == 0) "$comparados turma(s) comparada(s) · sem mudanças"
                        else "$comparados turma(s) comparada(s) · ↓ $melhoras módulo(s) melhoraram · ↑ $aumentos módulo(s) aumentaram"
                    } else if (escola) coord.mensagem else painel.mensagem,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(
                            onClickLabel = if (mostrarResumo) "Abrir comparação com a consulta anterior" else "Ver detalhes da última atualização",
                            role = Role.Button
                        ) { if (mostrarResumo) mostrarComparacao = true else mostrarMensagem = true }.padding(vertical = 6.dp),
                        transitionSpec = { fadeIn(tween(SgeMotion.duration(140))) togetherWith fadeOut(tween(SgeMotion.duration(100))) },
                        label = "mensagemAtualizacao") { mensagem ->
                        Text(mensagem, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(SgeIcons.Chevron, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    val diarioPausando = !escola && carregando && pausaDiario?.pausada == true
                    val podePausar = carregando && !diarioPausando && (!escola || filaCoord?.ids?.isNotEmpty() == true)
                    val podeContinuar = !carregando && if (escola) filaCoord?.let { it.pausada && it.ids.isNotEmpty() } == true else pausaDiario?.pausada == true
                    if (podePausar) TextButton(onClick = if (escola) aoPausarFila else aoPausarDiario) { Text("Pausar") }
                    else if (diarioPausando) TextButton(onClick = {}, enabled = false) { Text("Pausando…") }
                    else if (podeContinuar) Button(onClick = if (escola) aoRetomarFila else aoRetomarDiario) { Text("Continuar") }
                    if (escola) TextButton(onClick = aoCarregarTurmas) { Text(if (conhecidas.isEmpty()) "Carregar turmas" else "Reler turmas") }
                }
            }
        }
        if (escola && filaCoord != null) item(span = { GridItemSpan(maxLineSpan) }) {
            val fila = filaCoord!!
            val nomesFalhas = fila.falhas.mapNotNull { id -> coord.turmas.firstOrNull { it.id == id }?.nome }.distinct()
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(
                        onClickLabel = if (filaExpandida) "Recolher detalhes da verificação" else "Expandir detalhes da verificação",
                        role = Role.Button
                    ) { filaExpandida = !filaExpandida }.semantics {
                        stateDescription = if (filaExpandida) "Expandida" else "Recolhida"
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (fila.pausada) "Verificação pausada" else if (fila.ids.isNotEmpty()) "Verificação da escola em andamento" else "Verificação da escola concluída",
                            Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Icon(SgeIcons.Chevron, null, Modifier.size(18.dp).graphicsLayer {
                            rotationZ = if (filaExpandida) 270f else 90f
                        })
                    }
                    LinearProgressIndicator(progress = fila.progresso, modifier = Modifier.fillMaxWidth())
                    Text("Consultadas: ${fila.concluidas.size} · Ignoradas (recentes): ${fila.puladas} · Falhas: ${fila.falhas.size} · Restantes: ${fila.ids.size}",
                        style = MaterialTheme.typography.bodySmall)
                    AnimatedVisibility(visible = filaExpandida) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (nomesFalhas.isNotEmpty()) Text("Falharam: ${nomesFalhas.joinToString(" · ")}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (fila.ids.isNotEmpty() && fila.pausada) Button(onClick = aoRetomarFila) { Text("Retomar") }
                                if (fila.ids.isNotEmpty() && !fila.pausada) OutlinedButton(onClick = aoPausarFila) { Text("Pausar") }
                                if (fila.falhas.isNotEmpty() && fila.ids.isEmpty()) Button(onClick = aoTentarFalhas) { Text("Tentar falhas (${fila.falhas.size})") }
                            }
                        }
                    }
                }
            }
        }
        if (sgeMudou) item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                Text("O SGE parece ter mudado: várias telas não foram reconhecidas. Atualize o app (Ajustes → Atualizações do app) antes de confiar nesta consulta.",
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (escola) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = aoMapa, modifier = Modifier.weight(1f)) { Text("Mapa da escola") }
                    OutlinedButton(onClick = aoLote, modifier = Modifier.weight(1f)) { Text("Relatórios em lote") }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                PilulaFiltro(filtroAvancado.qualificador.isBlank(), { aoAplicarFiltro(filtroAvancado.copy(qualificador = "", estado = 0)) }) { Text("Tudo") }
                PilulaFiltro(filtroAvancado.qualificador == "conferir", { aoAplicarFiltro(filtroAvancado.copy(qualificador = "conferir", estado = 0)) }) { Text("A conferir") }
                PilulaFiltro(filtroAvancado.qualificador == "sem_vinculo", { aoAplicarFiltro(filtroAvancado.copy(qualificador = "sem_vinculo", estado = 0)) }) { Text("Sem vínculo docente") }
                PilulaFiltro(filtroAvancado.qualificador == "dados_antigos", { aoAplicarFiltro(filtroAvancado.copy(qualificador = "dados_antigos", estado = 0)) }) { Text("Dados antigos") }
                filtrosSalvos.forEach { salvo ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        PilulaFiltro(salvo.filtro == filtroAvancado, { aoAplicarFiltro(salvo.filtro) }) { Text(salvo.nome) }
                        IconButton(onClick = { aoExcluirFiltro(salvo.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(SgeIcons.Close, "Remover filtro ${salvo.nome}", Modifier.size(15.dp))
                        }
                    }
                }
                AssistChip(onClick = { nomeFiltro = ""; mostrarSalvarFiltro = true }, enabled = filtroAvancado.ativo, label = { Text("+ Salvar atual") })
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val opcoes = if (escola) Agrupamento.values().toList() else listOf(Agrupamento.TURMA, Agrupamento.COMPONENTE)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Segmentado(opcoes.map { it.titulo }, opcoes.indexOf(por).coerceAtLeast(0)) { aoAgrupar(opcoes[it]) } }
                BotaoQuadrado("Buscar", { mostrarBusca = !mostrarBusca }, marcado = mostrarBusca || busca.isNotBlank()) {
                    Icon(SgeIcons.Search, null, Modifier.size(20.dp))
                }
                Box {
                    BotaoQuadrado("Filtrar: " + listOf("todas as situações", "pendências", "a conferir", "em dia")[filtro], { menuFiltro = true }, marcado = filtro != 0) {
                        Icon(SgeIcons.Settings, null, Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menuFiltro, onDismissRequest = { menuFiltro = false }) {
                        listOf("Todas as situações", "Pendências", "A conferir", "Em dia").forEachIndexed { i, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { aoAplicarFiltro(filtroAvancado.copy(estado = i, qualificador = "")); menuFiltro = false },
                                trailingIcon = if (filtro == i) ({ Icon(SgeIcons.Check, null, Modifier.size(17.dp)) }) else null)
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Filtros detalhados…") }, onClick = { menuFiltro = false; aoFiltros() })
                    }
                }
            }
        }
        if (escola && (semVinculo.isNotEmpty() || diagnosticosRevisao.isNotEmpty())) item(span = { GridItemSpan(maxLineSpan) }) {
            val ambiguos = diagnosticosRevisao.count { it.tipo == "ambiguo" }
            val semCorrespondencia = diagnosticosRevisao.count { it.tipo == "sem_correspondencia" }
            val componenteAusente = diagnosticosRevisao.count { it.tipo == "componente_ausente" }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${semVinculo.size} turma(s)/componente(s) sem vínculo · ${diagnosticosRevisao.size} linha(s) de horário para revisar", style = MaterialTheme.typography.titleSmall)
                        Text(if (diagnosticosRevisao.isNotEmpty()) "$ambiguos ambígua(s), $semCorrespondencia sem correspondência e $componenteAusente sem componente. Nada foi atribuído automaticamente." else "Os vínculos podem ser ajustados neste aparelho, sem alterar o SGE.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    TextButton(onClick = {
                        aoAgrupar(Agrupamento.TURMA)
                        aoAplicarFiltro(filtroAvancado.copy(qualificador = "sem_vinculo", estado = 0))
                    }) { Text("Revisar") }
                }
            }
        }
        if (escola && por == Agrupamento.PROFESSOR) item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Professores do horário", style = MaterialTheme.typography.titleSmall)
                        Text(docentes.mensagem, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = aoAtualizarDocentes, enabled = conhecidas.isNotEmpty() && !docentes.sincronizando) {
                        if (docentes.sincronizando) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        else Icon(SgeIcons.Refresh, "Atualizar vínculos docentes", Modifier.size(21.dp))
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "busca:${fonte.name}") {
            AnimatedVisibility(visible = mostrarBusca || busca.isNotBlank(),
                enter = expandVertically(tween(SgeMotion.duration(180))) + fadeIn(tween(SgeMotion.duration(130))),
                exit = shrinkVertically(tween(SgeMotion.duration(150))) + fadeOut(tween(SgeMotion.duration(100)))) {
                OutlinedTextField(value = busca, onValueChange = { busca = it }, modifier = Modifier.fillMaxWidth().focusRequester(focoBusca), singleLine = true,
                    placeholder = { Text("Buscar turma, disciplina ou professor") }, leadingIcon = { Icon(SgeIcons.Search, null, Modifier.size(19.dp)) },
                    trailingIcon = ({ IconButton(onClick = { busca = ""; mostrarBusca = false }) { Icon(SgeIcons.Close, "Fechar busca", Modifier.size(17.dp)) } }),
                    shape = MaterialTheme.shapes.medium)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "resumoResultados:${fonte.name}") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (filtroAvancado.ativo) Text("Critérios ativos · ${descreverFiltro(filtroAvancado, docentes)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (busca.isNotBlank()) Text("Busca · $busca", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Resultados", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${visiveis.size} ${if (visiveis.size == 1) "cartão" else "cartões"}",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (grupos.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            MensagemVazia(if (escola) "Conecte sua escola" else "Seu diário começa aqui",
                if (escola) "No SGE Web, ative Unidade escolar e abra Escola → Conteúdo Aplicado. Depois, carregue e verifique as turmas."
                else "Entre com o perfil Servidor no SGE Web e toque em Atualizar diário para consultar suas turmas.", "Abrir SGE Web", aoAbrirSge)
        } else if (visiveis.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            MensagemVazia("Nenhum cartão neste filtro", "Mude a busca ou o estado selecionado para visualizar outros resultados.")
        }
        items(visiveis, key = { it.chave }) { grupo ->
            Box(Modifier.animateItemPlacement(tween(SgeMotion.duration(190))).graphicsLayer { alpha = alphaCartoes.value }) {
                CartaoGrupo(grupo, por, selecionado = grupo.chave == selecionada, mudancas = mudancas) { aoAbrir(grupo) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("As contagens se referem à última consulta. Aguardando avaliação aparece nos detalhes e não conta como pendência do professor.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
    if (mostrarMensagem) AlertDialog(onDismissRequest = { mostrarMensagem = false }, title = { Text("Última atualização") },
        text = { Text(if (escola) coord.mensagem else painel.mensagem) },
        confirmButton = { TextButton(onClick = { mostrarMensagem = false }) { Text("Entendi") } })
    if (mostrarComparacao) ModalBottomSheet(onDismissRequest = { mostrarComparacao = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mudanças desde a consulta anterior", style = MaterialTheme.typography.headlineSmall)
            Text("Só comparamos a mesma turma, os mesmos componentes e módulos com cobertura completa nos dois momentos. Dados ausentes, parciais ou não configurados ficam fora.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mudancas.groupBy { it.turmaId }.toSortedMap().forEach { (_, lista) ->
                Text(lista.first().turma, style = MaterialTheme.typography.titleMedium)
                lista.sortedBy { it.indice }.forEach { m ->
                    val corMudanca = when { m.delta < 0 -> MaterialTheme.colorScheme.tertiary; m.delta > 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                    Text("${SgeDashboard.nomes[m.indice]} · ${m.anterior} → ${m.atual}" + when {
                        m.delta < 0 -> " · melhorou ${kotlin.math.abs(m.delta)}"
                        m.delta > 0 -> " · aumentou ${m.delta}"
                        else -> " · sem alteração"
                    }, style = MaterialTheme.typography.bodyMedium, color = corMudanca)
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(18.dp))
        }
    }
    if (mostrarSalvarFiltro) AlertDialog(onDismissRequest = { mostrarSalvarFiltro = false }, title = { Text("Salvar filtro atual") },
        text = { OutlinedTextField(value = nomeFiltro, onValueChange = { nomeFiltro = it.take(40) }, singleLine = true, label = { Text("Nome do filtro") }) },
        confirmButton = { Button(onClick = {
            nomeFiltro.trim().takeIf { it.isNotBlank() }?.let { aoSalvarFiltro(FiltroSalvo(UUID.randomUUID().toString(), it, filtroAvancado)) }
            mostrarSalvarFiltro = false
        }, enabled = nomeFiltro.isNotBlank()) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = { mostrarSalvarFiltro = false }) { Text("Cancelar") } })
}

/**
 * Seletor de bimestre na tela inicial (coordenação e diário). Ao trocar, grava a escolha e
 * sincroniza sozinho: a coordenação verifica as turmas conhecidas; o diário, a sincronização pessoal.
 */
@Composable
fun SeletorBimestre(fonte: FontePainel, turmasIds: List<String>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var escolhido by remember { mutableStateOf(br.to.seduc.sgediariowidget.data.SgeBimestre.ler(ctx)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0 to "Atual", 1 to "1º", 2 to "2º", 3 to "3º", 4 to "4º").forEach { (valor, rotulo) ->
            FilterChip(selected = escolhido == valor, onClick = {
                if (escolhido != valor) {
                    escolhido = valor
                    br.to.seduc.sgediariowidget.data.SgeBimestre.salvar(ctx, valor)
                    if (fonte == FontePainel.COORDENACAO) br.to.seduc.sgediariowidget.worker.SgeSyncWorker.verificarTurmas(ctx, turmasIds)
                    else br.to.seduc.sgediariowidget.worker.SgeSyncWorker.sincronizarAgora(ctx)
                }
            }, label = { Text(rotulo) })
        }
    }
}

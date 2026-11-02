@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package br.to.seduc.sgediariowidget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.*
import java.time.LocalDateTime

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
    Surface(color = cor.copy(alpha = .09f), shape = RoundedCornerShape(40.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(if (estado == EstadoGrupo.EM_DIA) SgeIcons.Check else if (estado == EstadoGrupo.PENDENTE) SgeIcons.Clock else SgeIcons.Info,
                null, Modifier.size(13.dp), tint = cor)
            Text(nomeEstado(estado), color = cor, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun CartaoGrupo(grupo: GrupoPendencias, por: Agrupamento, selecionado: Boolean = false, abrir: () -> Unit) {
    val horasDado = remember(grupo) { SgeIdade.maisAntiga(grupo.resultados, LocalDateTime.now()) }
    val tituloV = tituloVisivel(grupo)
    val descricao = buildString {
        append(tituloV).append(". ").append(nomeEstado(grupo.estado))
        horasDado?.let { append(". Dados ").append(SgeIdade.texto(it)) }
        append(". Toque para ver detalhes.")
    }
    Surface(onClick = abrir, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = descricao },
        color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = BorderStroke(if (selecionado) 1.5.dp else .6.dp, if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), shadowElevation = .5.dp) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Icon(when(por) { Agrupamento.TURMA -> SgeIcons.Cards; Agrupamento.COMPONENTE -> SgeIcons.Book; else -> SgeIcons.User },
                        null, Modifier.padding(11.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                EmblemaEstado(grupo.estado)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tituloV, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtituloVisivel(grupo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (horasDado != null) {
                val corIdade = when (SgeIdade.faixa(horasDado)) {
                    SgeIdade.Faixa.RECENTE -> MaterialTheme.colorScheme.tertiary
                    SgeIdade.Faixa.MEDIA -> corAlerta()
                    SgeIdade.Faixa.ANTIGA -> MaterialTheme.colorScheme.error
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(SgeIcons.Clock, null, Modifier.size(13.dp), tint = corIdade)
                    Text("Dados ${SgeIdade.texto(horasDado)}", style = MaterialTheme.typography.labelSmall, color = corIdade)
                }
            }
            val pendentes = grupo.modulos.filter { (it.quantidade ?: 0) > 0 }
            if (pendentes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pendentes.take(2).forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(iconeModulo(m.indice), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Text("${SgeDashboard.nomes[m.indice]} · ${m.valor()} ${m.unidade()}",
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pendentes.size > 2) Text("+ ${contarSge(pendentes.size - 2, "módulo", "módulos")} com pendências", style = MaterialTheme.typography.labelSmall,
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
                Text("Ver detalhes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                Icon(SgeIcons.Chevron, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun MensagemVazia(titulo: String, texto: String, acao: String? = null, aoAgir: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(SgeIcons.Cards, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (acao != null) Button(onClick = aoAgir) { Text(acao) }
        }
    }
}

@Composable private fun NumeroResumo(numero: Int, titulo: String, cor: Color, modifier: Modifier) {
    Surface(modifier, color = cor.copy(alpha = .065f), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(numero.toString(), style = MaterialTheme.typography.headlineMedium, color = cor)
            Text(titulo, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TelaCartoes(
    fonte: FontePainel, painel: PainelSge, coord: CoordPainel, docentes: PainelDocentes,
    conhecidas: List<Pair<String, String>>, por: Agrupamento, aoAgrupar: (Agrupamento) -> Unit,
    aoAbrir: (GrupoPendencias) -> Unit, aoSincronizar: () -> Unit, aoCarregarTurmas: () -> Unit,
    aoAtualizarDocentes: () -> Unit, aoAbrirSge: () -> Unit, aoAjuda: () -> Unit, selecionada: String? = null, filtroAvancado: FiltroAvancado = FiltroAvancado(), organizacao: Map<String,DadosTurma> = emptyMap(), aoFiltros: ()->Unit = {}, aoMapa: ()->Unit = {}, aoLote: ()->Unit = {}
) {
    var busca by rememberSaveable(fonte.name) { mutableStateOf("") }
    var filtro by rememberSaveable(fonte.name) { mutableStateOf(0) }
    var mostrarBusca by rememberSaveable(fonte.name) { mutableStateOf(false) }
    var menuFiltro by remember { mutableStateOf(false) }
    var mostrarMensagem by remember { mutableStateOf(false) }
    val focoBusca = remember { FocusRequester() }
    LaunchedEffect(SgeUi.buscaPedida) { if (SgeUi.buscaPedida > 0) mostrarBusca = true }
    LaunchedEffect(mostrarBusca) { if (mostrarBusca) runCatching { focoBusca.requestFocus() } }
    val escola = fonte == FontePainel.COORDENACAO
    val resultados = remember(escola, coord.turmas, painel.turmas, filtroAvancado, organizacao, docentes) { SgeOrganizacao.filtrar(if (escola) coord.turmas.flatMap { it.disciplinas } else painel.turmas, filtroAvancado, organizacao, docentes) }
    val grupos = remember(resultados, docentes, conhecidas, por, escola, filtroAvancado) {
        val list = SgeDashboard.agrupar(resultados, por, docentes.vinculos, docentes.locais)
        if (escola && por == Agrupamento.TURMA && !filtroAvancado.ativo) list + conhecidas.filter { (id, _) -> list.none { id in it.turmaIds } }
            .map { (id, nome) -> GrupoPendencias("turma:$id", nome.substringBefore('/'), nome.substringAfter('/', "Ainda não consultada"), emptyList(), setOf(id)) }
        else list
    }
    val porTurma = remember(resultados) { SgeDashboard.agrupar(resultados, Agrupamento.TURMA) }
    val pendentes = porTurma.count { it.estado == EstadoGrupo.PENDENTE }
    val conferir = porTurma.count { it.exigeConferencia } + if (escola) conhecidas.count { (id, _) -> porTurma.none { id in it.turmaIds } } else 0
    val nomesProf = remember(docentes) { SgeBusca.indiceProfessores(docentes.vinculos, docentes.locais) }
    val sgeMudou = remember(resultados) { SgeSaude.sgeMudou(resultados) }
    val visiveis = grupos.filter { g ->
        (busca.isBlank() || SgeBusca.combina(busca, g.titulo + " " + g.subtitulo + " " + g.resultados.joinToString { it.turma.nome + " " + it.turma.disciplina + " " + nomesProf(it) })) &&
            when(filtro) { 1 -> g.estado == EstadoGrupo.PENDENTE; 2 -> g.exigeConferencia
                3 -> g.estado == EstadoGrupo.EM_DIA; else -> true }
    }
    val carregando = if (escola) coord.sincronizando else painel.sincronizando
    LazyVerticalGrid(columns = GridCells.Adaptive(285.dp), contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (escola) "Coordenação" else "Seu diário", style = MaterialTheme.typography.displaySmall)
                Text(if (escola) "Uma escola inteira, em recortes mais claros." else "O que precisa de atenção, em um só lugar.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumeroResumo(pendentes, if (pendentes == 1) "turma com pendências" else "turmas com pendências", MaterialTheme.colorScheme.error, Modifier.weight(1f))
                NumeroResumo(conferir, if (conferir == 1) "turma a conferir" else "turmas a conferir", corAlerta(), Modifier.weight(1f))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (carregando) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (escola) coord.mensagem else painel.mensagem,
                        modifier = Modifier.weight(1f).clickable { mostrarMensagem = true }.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (escola) TextButton(onClick = aoCarregarTurmas) { Text(if (conhecidas.isEmpty()) "Carregar turmas" else "Reler turmas") }
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
                BarraRecorte(filtroAvancado, aoFiltros, if (escola) "Toda a escola" else "Todas as suas turmas")
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val opcoes = if (escola) Agrupamento.values().toList() else listOf(Agrupamento.TURMA, Agrupamento.COMPONENTE)
            Segmentado(opcoes.map { it.titulo }, opcoes.indexOf(por).coerceAtLeast(0)) { aoAgrupar(opcoes[it]) }
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${visiveis.size} ${if (visiveis.size == 1) "cartão" else "cartões"}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { mostrarBusca = !mostrarBusca }) {
                    Icon(SgeIcons.Search, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Buscar")
                }
                Box {
                    TextButton(onClick = { menuFiltro = true }) {
                        Icon(SgeIcons.Settings, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp))
                        Text(listOf("Filtrar", "Pendências", "A conferir", "Em dia")[filtro])
                    }
                    DropdownMenu(expanded = menuFiltro, onDismissRequest = { menuFiltro = false }) {
                        listOf("Todas as situações", "Pendências", "A conferir", "Em dia").forEachIndexed { i, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { filtro = i; menuFiltro = false },
                                trailingIcon = if (filtro == i) ({ Icon(SgeIcons.Check, null, Modifier.size(17.dp)) }) else null)
                        }
                    }
                }
            }
        }
        if (mostrarBusca || busca.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(value = busca, onValueChange = { busca = it }, modifier = Modifier.fillMaxWidth().focusRequester(focoBusca), singleLine = true,
                placeholder = { Text("Buscar turma, disciplina ou professor") }, leadingIcon = { Icon(SgeIcons.Search, null, Modifier.size(19.dp)) },
                trailingIcon = ({ IconButton(onClick = { busca = ""; mostrarBusca = false }) { Icon(SgeIcons.Close, "Fechar busca", Modifier.size(17.dp)) } }),
                shape = MaterialTheme.shapes.medium)
        }
        if (grupos.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            MensagemVazia(if (escola) "Conecte sua escola" else "Seu diário começa aqui",
                if (escola) "No SGE Web, ative Unidade escolar e abra Escola → Conteúdo Aplicado. Depois, carregue e verifique as turmas."
                else "Entre com o perfil Servidor no SGE Web e toque em Atualizar diário para consultar suas turmas.", "Abrir SGE Web", aoAbrirSge)
        } else if (visiveis.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            MensagemVazia("Nenhum cartão neste filtro", "Mude a busca ou o estado selecionado para visualizar outros resultados.")
        }
        items(visiveis, key = { it.chave }) { grupo -> CartaoGrupo(grupo, por, selecionado = grupo.chave == selecionada) { aoAbrir(grupo) } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("As contagens se referem à última consulta. Aguardando avaliação aparece nos detalhes e não conta como pendência do professor.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (mostrarMensagem) AlertDialog(onDismissRequest = { mostrarMensagem = false }, title = { Text("Última atualização") },
        text = { Text(if (escola) coord.mensagem else painel.mensagem) },
        confirmButton = { TextButton(onClick = { mostrarMensagem = false }) { Text("Entendi") } })
}

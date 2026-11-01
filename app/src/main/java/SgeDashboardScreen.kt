@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package br.to.seduc.sgediariowidget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.*

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
fun CartaoGrupo(grupo: GrupoPendencias, por: Agrupamento, abrir: () -> Unit) {
    Surface(onClick = abrir, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = .5.dp) {
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
                Text(grupo.titulo, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(grupo.subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    if (pendentes.size > 2) Text("+ ${pendentes.size - 2} módulo(s) com pendências", style = MaterialTheme.typography.labelSmall,
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

@Composable private fun NumeroResumo(numero: Int, titulo: String, icone: ImageVector, cor: Color, modifier: Modifier) {
    Surface(modifier, color = cor.copy(alpha = .065f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icone, null, Modifier.size(20.dp), tint = cor)
            Text(numero.toString(), style = MaterialTheme.typography.headlineMedium, color = cor)
            Text(titulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TelaCartoes(
    fonte: FontePainel, painel: PainelSge, coord: CoordPainel, docentes: PainelDocentes,
    conhecidas: List<Pair<String, String>>, por: Agrupamento, aoAgrupar: (Agrupamento) -> Unit,
    aoAbrir: (GrupoPendencias) -> Unit, aoSincronizar: () -> Unit, aoCarregarTurmas: () -> Unit,
    aoAtualizarDocentes: () -> Unit, aoAbrirSge: () -> Unit, aoAjuda: () -> Unit
) {
    var busca by rememberSaveable(fonte.name) { mutableStateOf("") }
    var filtro by rememberSaveable(fonte.name) { mutableStateOf(0) }
    val escola = fonte == FontePainel.COORDENACAO
    val resultados = remember(escola, coord.turmas, painel.turmas) { if (escola) coord.turmas.flatMap { it.disciplinas } else painel.turmas }
    val grupos = remember(resultados, docentes, conhecidas, por, escola) {
        val list = SgeDashboard.agrupar(resultados, por, docentes.vinculos, docentes.locais)
        if (escola && por == Agrupamento.TURMA) list + conhecidas.filter { (id, _) -> list.none { id in it.turmaIds } }
            .map { (id, nome) -> GrupoPendencias("turma:$id", nome.substringBefore('/'), nome.substringAfter('/', "Ainda não consultada"), emptyList(), setOf(id)) }
        else list
    }
    val porTurma = remember(resultados) { SgeDashboard.agrupar(resultados, Agrupamento.TURMA) }
    val pendentes = porTurma.count { it.estado == EstadoGrupo.PENDENTE }
    val conferir = porTurma.count { it.exigeConferencia } + if (escola) conhecidas.count { (id, _) -> porTurma.none { id in it.turmaIds } } else 0
    val visiveis = grupos.filter { g ->
        (busca.isBlank() || normalizarSge(g.titulo + " " + g.subtitulo + " " + g.resultados.joinToString { it.turma.nome + " " + it.turma.disciplina }).contains(normalizarSge(busca))) &&
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
                NumeroResumo(pendentes, "turmas com pendências", SgeIcons.Clock, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                NumeroResumo(conferir, "turmas a conferir", SgeIcons.Info, corAlerta(), Modifier.weight(1f))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Button(onClick = aoSincronizar, enabled = !carregando && (!escola || conhecidas.isNotEmpty()), shape = RoundedCornerShape(50.dp)) {
                        if (carregando) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        else Icon(SgeIcons.Refresh, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp)); Text(if (carregando) "Consultando…" else if (escola) "Verificar turmas" else "Atualizar diário")
                    }
                    if (escola) OutlinedButton(onClick = aoCarregarTurmas, shape = RoundedCornerShape(50.dp)) { Text("Carregar turmas") }
                    TextButton(onClick = aoAjuda) { Icon(SgeIcons.Info, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Legendas") }
                }
                if (carregando) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (escola) coord.mensagem else painel.mensagem, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val opcoes = if (escola) Agrupamento.values().toList() else listOf(Agrupamento.TURMA, Agrupamento.COMPONENTE)
            Segmentado(opcoes.map { it.titulo }, opcoes.indexOf(por).coerceAtLeast(0)) { aoAgrupar(opcoes[it]) }
        }
        if (escola && por == Agrupamento.PROFESSOR) item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Professores do horário", style = MaterialTheme.typography.titleSmall)
                    Text("Os registros são da turma e do componente. Vários docentes no mesmo vínculo aparecem juntos, sem duplicar as pendências.",
                        style = MaterialTheme.typography.bodySmall)
                    Text(docentes.mensagem, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = aoAtualizarDocentes, enabled = conhecidas.isNotEmpty() && !docentes.sincronizando) {
                        Text(if (docentes.sincronizando) "Lendo vínculos…" else "Atualizar vínculos docentes")
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(value = busca, onValueChange = { busca = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Buscar ${por.titulo.lowercase()}") }, leadingIcon = { Icon(SgeIcons.Search, null, Modifier.size(19.dp)) },
                trailingIcon = if (busca.isNotEmpty()) ({ IconButton(onClick = { busca = "" }) { Icon(SgeIcons.Close, "Limpar busca", Modifier.size(17.dp)) } }) else null,
                shape = MaterialTheme.shapes.medium)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Todas", "Pendências", "A conferir", "Em dia").forEachIndexed { i, label ->
                    FilterChip(selected = filtro == i, onClick = { filtro = i }, label = { Text(label) }, shape = RoundedCornerShape(50.dp))
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
        items(visiveis, key = { it.chave }) { grupo -> CartaoGrupo(grupo, por) { aoAbrir(grupo) } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("As contagens se referem à última consulta. Aguardando avaliação aparece nos detalhes e não conta como pendência do professor.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package br.to.seduc.sgediariowidget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.*
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun TelaDetalhes(grupo: GrupoPendencias, fonte: FontePainel, docentes: PainelDocentes,
    aoAtualizar: () -> Unit, aoAbrirSge: () -> Unit, aoVincular: (ResultadoTurma) -> Unit, aoCompartilhar: () -> Unit, aoPdf: () -> Unit = {}, moduloInicial: Int = -1,
    mudancas: List<MudancaModulo> = emptyList()) {
    var menuCompartilhar by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EmblemaEstado(grupo.estado)
                Text(tituloVisivel(grupo), style = MaterialTheme.typography.displaySmall)
                Text(subtituloVisivel(grupo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                grupo.resultados.map { it.atualizado }.filter { it.isNotBlank() }.lastOrNull()?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(SgeIcons.Clock, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Consulta: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SgeIdade.maisAntigaPorModulo(grupo.resultados, LocalDateTime.now())?.let { horas ->
                    val corIdade = corAtualidade(horas)
                    Surface(color = corIdade.copy(alpha = .11f), shape = MaterialTheme.shapes.small) {
                        Text("Dados mais antigos: ${SgeIdade.texto(horas)}${if (horas >= 24) " · confira no SGE antes de agir" else ""}",
                            Modifier.padding(horizontal = 11.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = corIdade)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = aoAbrirSge) { Text("Abrir SGE") }
                    Box {
                        OutlinedButton(onClick = { menuCompartilhar = true }, enabled = grupo.resultados.isNotEmpty()) {
                            Icon(SgeIcons.Export, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Compartilhar")
                        }
                        DropdownMenu(expanded = menuCompartilhar, onDismissRequest = { menuCompartilhar = false }) {
                            DropdownMenuItem(text = { Text("Texto / WhatsApp") }, onClick = { menuCompartilhar = false; aoCompartilhar() })
                            DropdownMenuItem(text = { Text("Relatório em PDF") }, onClick = { menuCompartilhar = false; aoPdf() })
                        }
                    }
                }
            }
        }
        if (grupo.compartilhado || grupo.vinculoLocal) item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(if (grupo.compartilhado) "Docência compartilhada: os mesmos registros da turma e do componente são exibidos uma vez. O app não identifica qual docente é responsável por cada documento."
                    else "Este agrupamento inclui vínculos informados localmente por você. A alteração organiza o app e não modifica o SGE.",
                    Modifier.padding(15.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (grupo.resultados.isEmpty()) item {
            MensagemVazia("Turma ainda sem consulta", "Verifique esta turma para mostrar conteúdos, frequência e planejamentos.", "Verificar turma", aoAtualizar)
        } else {
            item { Text("PENDÊNCIAS POR MÓDULO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(grupo.modulos.sortedBy { if (it.indice == moduloInicial) -1 else it.indice }, key = { it.indice }) { modulo -> CartaoModulo(grupo, modulo, moduloInicial, mudancas) }
        }
        if (fonte == FontePainel.COORDENACAO && grupo.resultados.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Vínculos docentes", style = MaterialTheme.typography.titleMedium)
                    Text("O horário do SGE fornece os professores. Você pode ajustar os vínculos apenas neste aparelho.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(grupo.resultados, key = { "vinculo:${chaveResultado(it)}" }) { r ->
                val ps = docentes.locais[chaveResultado(r)] ?: docentes.vinculos.filter {
                    chaveVinculo(it.turmaId, it.componente) == chaveResultado(r)
                }.map { it.docente }.distinctBy { it.id }
                val local = docentes.locais.containsKey(chaveResultado(r))
                val diagnosticos = docentes.diagnosticos.filter { d ->
                    (d.componente.isBlank() || normalizarSge(d.componente) == normalizarSge(r.turma.disciplina)) &&
                        (d.candidatos.any { it.first == r.turma.id } || (d.candidatos.isEmpty() &&
                            normalizarSge(d.turmaHorario) == normalizarSge(r.turma.nome.substringBefore('/'))))
                }
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${r.turma.nome.substringBefore('/')} · ${r.turma.disciplina}", style = MaterialTheme.typography.titleSmall)
                        Text(ps.joinToString(" · ") { it.nome }.ifBlank { "Professor não identificado" }, style = MaterialTheme.typography.bodyMedium)
                        Text(when { local -> "Origem: ajuste local"; ps.isNotEmpty() -> "Origem: horário do SGE"; else -> "Origem: sem vínculo identificado" },
                            style = MaterialTheme.typography.labelSmall, color = if (local) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!local) diagnosticos.forEach { d ->
                            val candidatos = d.candidatos.joinToString(" · ") { it.second }.ifBlank { "nenhuma turma reconhecida" }
                            Text("Revisão manual (${when (d.tipo) { "ambiguo" -> "nome ambíguo"; "componente_ausente" -> "componente ausente"; else -> "sem correspondência" }}): $candidatos. O app não escolheu automaticamente.",
                                style = MaterialTheme.typography.bodySmall, color = corAlerta())
                        }
                        TextButton(onClick = { aoVincular(r) }) { Icon(SgeIcons.Link, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Ajustar vínculo") }
                    }
                }
            }
        }
        item {
            Text("Em dia exige cobertura completa. Aguardando avaliação não é pendência de envio. Para corrigir registros, use o próprio SGE.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun corAtualidade(horas: Long): Color = when (SgeIdade.faixa(horas)) {
    SgeIdade.Faixa.RECENTE -> MaterialTheme.colorScheme.tertiary
    SgeIdade.Faixa.MEDIA -> corAlerta()
    SgeIdade.Faixa.ANTIGA -> MaterialTheme.colorScheme.error
}

@Composable private fun CartaoModulo(grupo: GrupoPendencias, resumo: ResumoModulo, moduloInicial: Int = -1, mudancas: List<MudancaModulo> = emptyList()) {
    var aberto by rememberSaveable(grupo.chave, resumo.indice, moduloInicial) { mutableStateOf(resumo.indice == moduloInicial || (resumo.quantidade ?: 0) > 0) }
    val estado = when { (resumo.quantidade ?: 0) > 0 -> EstadoGrupo.PENDENTE
        !resumo.completa -> EstadoGrupo.CONFERIR; else -> EstadoGrupo.EM_DIA }
    val cor = corEstado(estado)
    Surface(modifier = Modifier.animateContentSize(tween(SgeMotion.duration(180))),
        color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Column(Modifier.fillMaxWidth().clickable { aberto = !aberto }.padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = cor.copy(alpha = .09f), shape = MaterialTheme.shapes.small) {
                        Icon(iconeModulo(resumo.indice), null, Modifier.padding(11.dp).size(23.dp), tint = cor)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(SgeDashboard.nomes[resumo.indice], style = MaterialTheme.typography.titleMedium)
                        Text(if (resumo.naoConfigurado) "Não configurado" else if (resumo.quantidade == null) "Não verificado" else "${resumo.valor()} ${resumo.unidade()} ${if (resumo.quantidade == 1) "pendente" else "pendentes"}",
                            style = MaterialTheme.typography.bodyMedium, color = if (resumo.naoConfigurado) MaterialTheme.colorScheme.onSurfaceVariant else cor)
                    }
                    Icon(if (aberto) SgeIcons.Close else SgeIcons.Chevron, if (aberto) "Recolher" else "Ver detalhes",
                        Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(SgeDashboard.descricoes[resumo.indice], style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (resumo.naoConfigurado) Text("O professor ainda não definiu a periodicidade no SGE; não há como saber quantos documentos deveriam existir.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (!resumo.completa) Text(if (resumo.quantidade == null) "A consulta não confirma uma contagem para este módulo."
                    else "* Parte dos dados não está conferida. O valor conhecido pode aumentar.",
                    style = MaterialTheme.typography.bodySmall, color = corAlerta())
                if (resumo.aguardando > 0) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("${resumo.aguardando} aguardando avaliação · fora das pendências", Modifier.padding(9.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (resumo.aprovados > 0) Text("${resumo.aprovados} documento(s) aprovado(s)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
            AnimatedVisibility(visible = aberto, enter = fadeIn(tween(SgeMotion.duration(130))),
                exit = fadeOut(tween(SgeMotion.duration(100)))) {
                Column(Modifier.padding(start = 19.dp, end = 19.dp, bottom = 19.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    grupo.resultados.forEach { r ->
                        val m = r.modulos.getOrNull(resumo.indice) ?: ResultadoModulo()
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${r.turma.nome.substringBefore('/')} · ${r.turma.disciplina}", style = MaterialTheme.typography.titleSmall)
                            val rotuloConsulta = when {
                                m.naoConfigurado -> "Não configurado"
                                m.quantidade == null -> "Não verificado"
                                !m.coberturaCompleta -> "Parcial (*)"
                                else -> "Verificado"
                            }
                            val corConsulta = when {
                                m.naoConfigurado -> MaterialTheme.colorScheme.onSurfaceVariant
                                m.quantidade == null -> MaterialTheme.colorScheme.error
                                !m.coberturaCompleta -> corAlerta()
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            Surface(color = corConsulta.copy(alpha = .10f), shape = MaterialTheme.shapes.small) {
                                Text(rotuloConsulta, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = corConsulta)
                            }
                            val idade = m.atualizadoMs.takeIf { it > 0L }?.let { SgeIdade.horas(it) }
                                ?: SgeIdade.horas(r.atualizado, LocalDateTime.now())
                            if (m.atualizadoMs > 0L && idade != null) {
                                Text("Módulo consultado em ${SgeIdade.dataHora(m.atualizadoMs)} · ${SgeIdade.texto(idade)}",
                                    style = MaterialTheme.typography.labelSmall, color = corAtualidade(idade))
                            } else if (r.atualizado.isNotBlank() && idade != null) {
                                Text("Consulta da turma: ${r.atualizado} · horário aproximado do módulo · ${SgeIdade.texto(idade)}",
                                    style = MaterialTheme.typography.labelSmall, color = corAtualidade(idade))
                            }
                            if ((m.quantidade ?: 0) > 0 && idade != null && idade >= 24) Text("Pendência registrada em dado antigo; confirme no SGE antes de agir.",
                                style = MaterialTheme.typography.bodySmall, color = corAtualidade(idade))
                            mudancas.firstOrNull { it.turmaId == r.turma.id && it.indice == resumo.indice && it.delta != 0 }?.let { variacao ->
                                val melhorou = variacao.delta < 0
                                Text("Desde a consulta anterior: ${variacao.anterior} → ${variacao.atual} · ${if (melhorou) "melhorou ${kotlin.math.abs(variacao.delta)}" else "aumentou ${variacao.delta}"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (melhorou) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                            }
                            SelectionContainer { Text(m.detalhe, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VinculoDialog(resultado: ResultadoTurma, dados: PainelDocentes, cancelar: () -> Unit,
    salvar: (List<DocenteRef>?) -> Unit) {
    val iniciais = dados.locais[chaveResultado(resultado)] ?: dados.vinculos.filter {
        chaveVinculo(it.turmaId, it.componente) == chaveResultado(resultado)
    }.map { it.docente }.distinctBy { it.id }
    var opcoes by remember { mutableStateOf((dados.opcoesDocentes() + iniciais).distinctBy { it.id }) }
    var ids by remember { mutableStateOf(iniciais.map { it.id }.toSet()) }
    var busca by remember { mutableStateOf("") }
    var nomeNovo by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = cancelar, title = { Text("Vincular professor") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${resultado.turma.nome.substringBefore('/')} · ${resultado.turma.disciplina}", style = MaterialTheme.typography.bodyMedium)
                Text("Escolha um ou mais docentes. Este ajuste só organiza os dados neste aparelho.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(busca, { busca = it }, singleLine = true, label = { Text("Buscar professor") })
                LazyColumn(Modifier.heightIn(min = 40.dp, max = 220.dp)) {
                    items(opcoes.filter { normalizarSge(it.nome).contains(normalizarSge(busca)) }, key = { it.id }) { p ->
                        Row(Modifier.fillMaxWidth().clickable { ids = if (p.id in ids) ids - p.id else ids + p.id }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(p.id in ids, { checked -> ids = if (checked) ids + p.id else ids - p.id })
                            Column {
                                Text(p.nome, style = MaterialTheme.typography.bodyMedium)
                                if (opcoes.count { normalizarSge(it.nome) == normalizarSge(p.nome) } > 1)
                                    Text("Identificação ${p.id.takeLast(6)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedTextField(nomeNovo, { nomeNovo = it }, singleLine = true, label = { Text("Adicionar nome local") })
                TextButton(enabled = nomeNovo.isNotBlank() && opcoes.none { normalizarSge(it.nome) == normalizarSge(nomeNovo) }, onClick = {
                    val p = DocenteRef("local:${UUID.randomUUID()}", nomeNovo.trim())
                    opcoes = opcoes + p; ids = ids + p.id; nomeNovo = ""
                }) { Icon(SgeIcons.Plus, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Adicionar à seleção") }
                if (nomeNovo.isNotBlank() && opcoes.any { normalizarSge(it.nome) == normalizarSge(nomeNovo) }) Text("Este nome já está na lista. Marque o professor acima.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { salvar(null) }) { Text("Usar vínculos do SGE") }
            }
        }, confirmButton = { Button(onClick = { salvar(opcoes.filter { it.id in ids }) }) { Text("Salvar vínculo") } },
        dismissButton = { TextButton(onClick = cancelar) { Text("Cancelar") } })
}

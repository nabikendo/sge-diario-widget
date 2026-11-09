package br.to.seduc.sgediariowidget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun TelaHistorico(
    dias: List<DiaHistorico>, fonte: FontePainel, aoFonte: (FontePainel) -> Unit,
    protegido: Boolean, desbloquear: () -> Unit, hoje: LocalDate = LocalDate.now(), docentes: PainelDocentes = PainelDocentes()
) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val categorias = SgeEscopo.incluidas(contexto)
    val diasComparaveis = remember(dias, fonte, categorias) {
        dias.filter { it.fonte == fonte }.map { d ->
            d.copy(medicoes = d.medicoes.filter { m -> categoriaTurma(m.turmaNome).let { it == "ESCOLARIZACAO" || it in categorias } })
        }.filter { it.medicoes.isNotEmpty() }
    }
    var comparar by remember(fonte) { mutableStateOf(false) }
    var agrupamento by remember { mutableStateOf(Agrupamento.TURMA) }
    var moduloRanking by remember { mutableIntStateOf(0) }
    val ranking = SgeHistorico.rankingSemanal(dias, fonte, moduloRanking, hoje, agrupamento, docentes.vinculos, docentes.locais)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sua semana", style = MaterialTheme.typography.displaySmall)
            Segmentado(listOf("Meu diário", "Coordenação"), if (fonte == FontePainel.PESSOAL) 0 else 1) { aoFonte(FontePainel.values()[it]) }
        }
        if (protegido && fonte == FontePainel.COORDENACAO) { TelaCoordenacaoProtegida(desbloquear); return@Column }
        if (comparar) { ComparacaoHistorico(diasComparaveis, voltar = { comparar = false }); return@Column }
        LazyColumn(contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedButton(onClick = { comparar = true }) { Text("Comparar consultas") } }
            item { Text("Consultas registradas neste aparelho. Cada dia inclui somente as turmas consultadas naquele dia; não é uma consulta retroativa ao SGE.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ranking da semana", style = MaterialTheme.typography.titleLarge)
                    Text("Escolha um módulo; os números não somam dias e documentos.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = agrupamento == Agrupamento.TURMA, onClick = { agrupamento = Agrupamento.TURMA }, label = { Text("Por turma") })
                        FilterChip(selected = agrupamento == Agrupamento.PROFESSOR, onClick = { agrupamento = Agrupamento.PROFESSOR }, label = { Text("Por professor") })
                    }
                    if (agrupamento == Agrupamento.PROFESSOR) Text("Usa os vínculos atuais deste aparelho; grupos sem vínculo ficam como professor não identificado.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                        items(SgeDashboard.nomes.indices.toList(), key = { it }) { indice ->
                            FilterChip(selected = moduloRanking == indice, onClick = { moduloRanking = indice }, label = { Text(SgeDashboard.nomes[indice]) })
                        }
                    }
                    Text("Ordenado pelas pendências da última consulta completa em cada janela de 7 dias. Sem cobertura completa nos dois períodos, a variação não é calculada.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (ranking.isEmpty()) item { Text("Ainda não há consultas suficientes para montar este ranking.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(ranking, key = { "ranking-${it.chave}" }) { item ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            val quantidade = item.quantidade
                            val unidade = if (moduloRanking < 2) if (quantidade == 1) "dia" else "dias" else if (quantidade == 1) "documento" else "documentos"
                            Text(if (quantidade == null) "A conferir" else "$quantidade $unidade",
                                style = MaterialTheme.typography.titleSmall, color = if (quantidade == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            val delta = item.variacao
                            Text(when {
                                delta == null -> "sem base comparável"
                                delta == 0 -> "sem variação vs. semana anterior"
                                delta < 0 -> "${-delta} a menos vs. semana anterior"
                                else -> "+$delta vs. semana anterior"
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items((0L..6L).map { hoje.minusDays(it) }, key = { it.toString() }) { data ->
                val dia = dias.firstOrNull { it.data == data && it.fonte == fonte }
                val anterior = dias.firstOrNull { it.data == data.minusDays(1) && it.fonte == fonte }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(data.format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale("pt", "BR"))), style = MaterialTheme.typography.titleMedium)
                        if (dia == null) Text("Sem consulta registrada", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else {
                            Text("${contarSge(dia.turmas, "turma", "turmas")} · ${contarSge(dia.medicoes.size, "componente consultado", "componentes consultados")}", style = MaterialTheme.typography.bodySmall)
                            dia.modulos.forEach { m ->
                                val delta = anterior?.let { dia.variacao(it, m.indice) }
                                val variacao = if (delta == null) "" else if (delta == 0) " · igual ao dia anterior" else " · ${if (delta > 0) "+" else ""}$delta desde ontem"
                                Text("${SgeDashboard.nomes[m.indice]}: ${m.valor()} ${if (m.quantidade == null) "" else m.unidade()}$variacao", style = MaterialTheme.typography.bodySmall,
                                    color = if (!m.completa) corAlerta() else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { Text("— não verificado · * cobertura parcial. Variações diárias aparecem somente com as mesmas turmas/componentes e cobertura completa nos dois dias. Nenhum valor reúne dias e documentos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ComparacaoHistorico(dias: List<DiaHistorico>, voltar: () -> Unit) {
    val datas = dias.map { it.data }.distinct().sortedDescending()
    var dataAnterior by remember(dias) { mutableStateOf(datas.getOrNull(1)) }
    var dataAtual by remember(dias) { mutableStateOf(datas.firstOrNull()) }
    var filtro by remember { mutableStateOf<TipoVariacaoConsulta?>(null) }
    val a = dias.firstOrNull { it.data == dataAnterior }
    val b = dias.firstOrNull { it.data == dataAtual }
    val variacoes = remember(a, b) {
        if (a != null && b != null && a.data < b.data) compararConsultas(a, b) else emptyList()
    }
    val visiveis = variacoes.filter { filtro == null || it.tipo == filtro }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onClick = voltar) { Text("Voltar ao histórico") }
            Text("Comparar consultas", style = MaterialTheme.typography.titleLarge)
            Text("Escolha dois dias com consultas salvas. O histórico guarda a última consulta do dia para cada turma e componente.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (datas.size < 2) item { Text("São necessárias consultas em dois dias diferentes.") }
        else {
            item {
                SeletorDataConsulta("Anterior", dataAnterior, datas.filter { dataAtual == null || it < dataAtual!! }) { dataAnterior = it }
                SeletorDataConsulta("Mais recente", dataAtual, datas.filter { dataAnterior == null || it > dataAnterior!! }) { dataAtual = it }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = filtro == null, onClick = { filtro = null }, label = { Text("Todas") }) }
                    items(TipoVariacaoConsulta.entries.toList()) { tipo ->
                        FilterChip(selected = filtro == tipo, onClick = { filtro = tipo }, label = { Text(tipo.titulo) })
                    }
                }
                Text("A comparação de planos e PEI mostra variações de contagem. Confira quais documentos mudaram no SGE.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (visiveis.isEmpty()) item { Text("Nenhuma variação neste filtro.") }
            items(visiveis) { linha ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${linha.turma} · ${linha.componente}", style = MaterialTheme.typography.titleSmall)
                        Text("${linha.tipo.titulo} · ${SgeDashboard.nomes[linha.indice]}", style = MaterialTheme.typography.labelMedium)
                        Text(linha.descricao, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeletorDataConsulta(rotulo: String, data: LocalDate?, opcoes: List<LocalDate>, escolher: (LocalDate) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { aberto = true }, enabled = opcoes.isNotEmpty()) {
            Text("$rotulo · ${data?.format(SgeConferenciaSemanal.formato) ?: "Escolher"}")
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { dia ->
                DropdownMenuItem(text = { Text(dia.format(SgeConferenciaSemanal.formato)) }, onClick = { escolher(dia); aberto = false })
            }
        }
    }
}

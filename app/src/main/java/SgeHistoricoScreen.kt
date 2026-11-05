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
    var agrupamento by remember { mutableStateOf(Agrupamento.TURMA) }
    var moduloRanking by remember { mutableIntStateOf(0) }
    val ranking = SgeHistorico.rankingSemanal(dias, fonte, moduloRanking, hoje, agrupamento, docentes.vinculos, docentes.locais)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sua semana", style = MaterialTheme.typography.displaySmall)
            Segmentado(listOf("Meu diário", "Coordenação"), if (fonte == FontePainel.PESSOAL) 0 else 1) { aoFonte(FontePainel.values()[it]) }
        }
        if (protegido && fonte == FontePainel.COORDENACAO) { TelaCoordenacaoProtegida(desbloquear); return@Column }
        LazyColumn(contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

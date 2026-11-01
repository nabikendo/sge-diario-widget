package br.to.seduc.sgediariowidget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun TelaHistorico(dias: List<DiaHistorico>, fonte: FontePainel, aoFonte: (FontePainel) -> Unit,
    protegido: Boolean, desbloquear: () -> Unit, hoje: LocalDate = LocalDate.now()) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sua semana", style = MaterialTheme.typography.displaySmall)
            Segmentado(listOf("Meu diário", "Coordenação"), if (fonte == FontePainel.PESSOAL) 0 else 1) { aoFonte(FontePainel.values()[it]) }
        }
        if (protegido && fonte == FontePainel.COORDENACAO) { TelaCoordenacaoProtegida(desbloquear); return@Column }
        LazyColumn(contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Consultas registradas neste aparelho a partir desta atualização. Cada dia inclui somente as turmas consultadas naquele dia; não é uma consulta retroativa ao SGE.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            item { Text("— não verificado · * cobertura parcial. Variações aparecem somente com as mesmas turmas/componentes e cobertura completa no módulo nos dois dias. Nenhum valor reúne dias e documentos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

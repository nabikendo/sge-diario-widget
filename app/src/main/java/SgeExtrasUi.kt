package br.to.seduc.sgediariowidget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.worker.SgeExtrasWorker
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker

@Composable
private fun LinhaSwitch(titulo: String, descricao: String, ligado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = ligado, onCheckedChange = aoMudar)
    }
}

/** Ajustes → Alertas e extras. Cada chave grava na hora; os avisos periódicos são reagendados ao mudar. */
@Composable
fun SecaoExtras(aoRascunhos: () -> Unit) {
    val ctx = LocalContext.current
    var e by remember { mutableStateOf(SgeExtras.ler(ctx)) }
    fun mudar(novo: ExtrasSge) { e = novo; SgeExtras.salvar(ctx, novo); SgeExtrasWorker.agendar(ctx) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Alertas e extras", style = MaterialTheme.typography.titleMedium)
            LinhaSwitch("Alerta de pendência antiga", "Avisa, no máximo uma vez por dia, quando Conteúdos ou Frequência estão pendentes há ${e.diasAntiga} dias ou mais.",
                e.alertaAntiga) { mudar(e.copy(alertaAntiga = it)) }
            if (e.alertaAntiga) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SgeExtras.diasPossiveis.forEach { d -> PilulaFiltro(e.diasAntiga == d, { mudar(e.copy(diasAntiga = d)) }) { Text("$d dias") } }
            }
            LinhaSwitch("Avisar quando uma turma zerar", "Notificação quando uma turma ou componente sai de pendente para em dia, com cobertura completa.",
                e.avisoZerou) { mudar(e.copy(avisoZerou = it)) }
            LinhaSwitch("Resumo semanal", "Toda segunda-feira, um resumo com os dados da última consulta.",
                e.resumoSemanal) { mudar(e.copy(resumoSemanal = it)) }
            LinhaSwitch("Lembrete de planejamento", "Uma semana antes de cada bimestre (datas aproximadas; confira o calendário da sua escola).",
                e.lembreteBimestre) { mudar(e.copy(lembreteBimestre = it)) }
            LinhaSwitch("Coordenação: só o que é meu", "Mostra na Coordenação apenas as turmas e componentes que também estão no seu diário.",
                e.soMeu) { mudar(e.copy(soMeu = it)) }
            LinhaSwitch("Economia de dados", "Consultas automáticas só no Wi‑Fi e sem imagens no SGE Web.",
                e.economiaDados) { mudar(e.copy(economiaDados = it)) }
            OutlinedButton(onClick = aoRascunhos, modifier = Modifier.fillMaxWidth()) { Text("Lançamentos offline (rascunhos)") }
        }
    }
}

/** Mostra por que consultas falharam, em linguagem simples, e oferece repetir só o diário. */
@Composable
fun BannerFalhas(rs: List<ResultadoTurma>, carregando: Boolean, aoTentar: () -> Unit) {
    val falhas = remember(rs) { SgeFalhas.resumo(rs) }
    if (falhas.isEmpty()) return
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Algumas consultas não foram verificadas", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            falhas.forEach { (motivo, n) ->
                Text("• $motivo ($n)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Button(onClick = aoTentar, enabled = !carregando) { Text(if (carregando) "Consultando…" else "Tentar de novo") }
        }
    }
}

/** Rascunhos de Conteúdo/Frequência feitos sem internet. O lançamento no SGE é manual: copie o texto e cole no SGE Web. */
@Composable
fun TelaRascunhos(aoAbrirSge: () -> Unit, aoMatriz: () -> Unit = {}) {
    val ctx = LocalContext.current
    var lista by remember { mutableStateOf(SgeRascunhos.ler(ctx)) }
    var tipo by remember { mutableStateOf(SgeRascunhos.tipos.first()) }
    var turma by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var texto by remember { mutableStateOf("") }
    val pendentes = lista.count { !it.lancado }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Lançamentos offline", style = MaterialTheme.typography.displaySmall)
                Text("Escreva agora, sem internet, e lance depois no SGE. O app guarda o rascunho no aparelho; ele não envia nada sozinho.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = aoMatriz, modifier = Modifier.fillMaxWidth()) { Text("Conteúdo com a Matriz (preenche o SGE)") }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SgeRascunhos.tipos.forEach { t -> PilulaFiltro(tipo == t, { tipo = t }) { Text(t) } }
                    }
                    OutlinedTextField(turma, { turma = it }, label = { Text("Turma e componente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(data, { data = it }, label = { Text("Data (aaaa-mm-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(texto, { texto = it }, label = { Text(if (tipo == "Frequência") "Faltas e observações" else "Conteúdo da aula") },
                        minLines = 3, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        lista = SgeRascunhos.adicionar(ctx, tipo, turma, data, texto)
                        turma = ""; texto = ""
                    }, enabled = turma.isNotBlank() && texto.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Guardar rascunho") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$pendentes por lançar", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Button(onClick = aoAbrirSge) { Text("Sincronizar: abrir SGE") }
            }
        }
        items(lista.sortedBy { it.lancado }, key = { it.id }) { r ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${r.tipo} · ${r.turma}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(r.data + if (r.lancado) " · lançado" else "", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.texto, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("rascunho", r.texto))
                            Toast.makeText(ctx, "Texto copiado. Cole no SGE.", Toast.LENGTH_SHORT).show()
                        }) { Text("Copiar") }
                        TextButton(onClick = { lista = SgeRascunhos.marcarLancado(ctx, r.id, !r.lancado) }) { Text(if (r.lancado) "Desfazer" else "Marcar lançado") }
                        TextButton(onClick = { lista = SgeRascunhos.remover(ctx, r.id) }) { Text("Excluir") }
                    }
                }
            }
        }
        if (lista.any { it.lancado }) item { TextButton(onClick = { lista = SgeRascunhos.limparLancados(ctx) }) { Text("Limpar lançados") } }
    }
}

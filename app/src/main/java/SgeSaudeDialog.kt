package br.to.seduc.sgediariowidget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.SgeSaude

@Composable
fun SaudeDialog(aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    var versao by remember { mutableStateOf(0) }
    var reparado by remember { mutableStateOf(false) }
    val linhas = remember(versao) { SgeSaude.linhas(ctx) }
    AlertDialog(onDismissRequest = aoFechar, title = { Text("Saúde do app") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                linhas.forEach { l ->
                    Column {
                        Text(if (l.alerta) "Atenção · ${l.titulo}" else l.titulo, style = MaterialTheme.typography.labelLarge,
                            color = if (l.alerta) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text(l.texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (reparado) Text("Consultas e fila destravadas. Painéis e histórico foram mantidos.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { SgeSaude.reparar(ctx); reparado = true; versao++ }) { Text("Reparar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } })
}

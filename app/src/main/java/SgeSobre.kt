package br.to.seduc.sgediariowidget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Dados de autoria. Edite aqui para trocar o nome exibido em Ajustes → Sobre. */
object Creditos {
    const val AUTOR = "nabikendo"
    const val REPOSITORIO = "https://github.com/nabikendo/sge-diario-widget"
    const val VERSOES = "https://github.com/nabikendo/sge-diario-widget/releases"
    const val PROBLEMAS = "https://github.com/nabikendo/sge-diario-widget/issues"
    const val LICENCA = "MIT"
}

@Composable
fun TelaSobre(versao: String, abrir: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(SgeIcons.Cards, null, Modifier.padding(16.dp).size(34.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("SGE Diário", style = MaterialTheme.typography.headlineMedium)
                Text("Versão $versao", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AUTORIA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Criado por ${Creditos.AUTOR}", style = MaterialTheme.typography.titleMedium)
                Text("Ideia, testes e manutenção por ${Creditos.AUTOR}. Código desenvolvido com apoio do Claude, da Anthropic.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PRIVACIDADE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("O app só lê dados do SGE e nunca grava nada nele. A senha fica na página de login do SGE; o app não a lê nem a guarda. Os painéis ficam apenas neste aparelho, sem anúncios nem estatísticas de uso.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Aplicativo independente e não oficial: não tem vínculo com a Seduc-TO nem com a Ponto iD. SGE e os demais nomes pertencem aos seus titulares.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CÓDIGO ABERTO E LICENÇA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Licença ${Creditos.LICENCA}. Usa Jetpack Compose, Glance e WorkManager (Apache 2.0) e Jsoup (MIT).",
                    style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { abrir(Creditos.REPOSITORIO) }) { Text("Código-fonte") }
                    OutlinedButton(onClick = { abrir(Creditos.VERSOES) }) { Text("Versões") }
                }
                TextButton(onClick = { abrir(Creditos.PROBLEMAS) }) { Text("Relatar um problema") }
            }
        }
    }
}

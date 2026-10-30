package br.to.seduc.sgediariowidget.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import br.to.seduc.sgediariowidget.MainActivity
import br.to.seduc.sgediariowidget.data.SgePainelStore
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker

class DiarioGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DiarioGlanceWidget()
}
class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SgeSyncWorker.sincronizarAgora(context)
    }
}
class DiarioGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val painelInicial = SgePainelStore.ler(context)
        val painel = if (painelInicial.sincronizando && painelInicial.inicioConsulta == 0L) {
            painelInicial.copy(
                mensagem = "A sincronização anterior foi interrompida. Abra o app e toque em Sincronizar agora.",
                sincronizando = false, inicioConsulta = 0L).also { SgePainelStore.salvar(context, it) }
        } else painelInicial
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Color.Black).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("SGE • TURMAS", GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                        style = TextStyle(color = ColorProvider(Color(0xFF38BDF8)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text("↻", GlanceModifier.padding(8.dp).clickable(actionRunCallback<RefreshActionCallback>()),
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 18.sp))
                }
                Text(painel.mensagem, style = TextStyle(color = ColorProvider(Color.LightGray), fontSize = 10.sp), maxLines = 3)
                Text("C: conteúdos • F: frequência • A: aula • P: curso • I: PEI",
                    style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 9.sp))
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    painel.turmas.forEach { r ->
                        item {
                            Column(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(actionStartActivity<MainActivity>())) {
                                Text("${r.turma.nome.substringBefore('/')} • ${r.turma.disciplina}",
                                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp))
                                Text(listOf("C", "F", "A", "P", "I").mapIndexed { i, label ->
                                    "$label ${r.modulos.getOrNull(i)?.resumo() ?: "—"}"
                                }.joinToString("  "), style = TextStyle(color = ColorProvider(Color(0xFF38BDF8)), fontSize = 11.sp))
                            }
                        }
                    }
                    item { Text("— Não verificado • * Cobertura incompleta. Toque para detalhes.",
                        style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 9.sp)) }
                }
            }
        }
    }
}

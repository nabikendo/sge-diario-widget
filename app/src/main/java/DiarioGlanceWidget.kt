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
                sincronizando = false,
                inicioConsulta = 0L
            ).also { SgePainelStore.salvar(context, it) }
        } else painelInicial

        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Color(0xFFF7F7F8)).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                        Text("SGE • DIÁRIO", style = TextStyle(
                            color = ColorProvider(Color(0xFF77777D)), fontSize = 10.sp, fontWeight = FontWeight.Medium
                        ))
                        Text("Suas turmas", style = TextStyle(
                            color = ColorProvider(Color(0xFF1C1C1E)), fontSize = 16.sp, fontWeight = FontWeight.Bold
                        ))
                    }
                    Text("↻", GlanceModifier.padding(8.dp).clickable(actionRunCallback<RefreshActionCallback>()),
                        style = TextStyle(color = ColorProvider(Color(0xFF3478F6)), fontSize = 20.sp))
                }
                Text("Deslize para cima ou para baixo para trocar de turma",
                    style = TextStyle(color = ColorProvider(Color(0xFF77777D)), fontSize = 10.sp))

                if (painel.turmas.isEmpty()) {
                    Text(painel.mensagem, GlanceModifier.padding(top = 12.dp),
                        style = TextStyle(color = ColorProvider(Color(0xFF55555A)), fontSize = 12.sp), maxLines = 3)
                }

                LazyColumn(GlanceModifier.fillMaxSize().padding(top = 8.dp)) {
                    painel.turmas.forEachIndexed { indice, r ->
                        item {
                            Column(
                                GlanceModifier.fillMaxWidth().padding(vertical = 5.dp)
                                    .background(Color(0xFFFFFFFF)).cornerRadius(18.dp)
                                    .clickable(actionStartActivity<MainActivity>()).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${r.turma.nome.substringBefore('/')} • ${r.turma.disciplina}",
                                        GlanceModifier.defaultWeight(), style = TextStyle(
                                            color = ColorProvider(Color(0xFF1C1C1E)), fontSize = 14.sp, fontWeight = FontWeight.Bold
                                        ), maxLines = 1)
                                    Text("${indice + 1}/${painel.turmas.size}", style = TextStyle(
                                        color = ColorProvider(Color(0xFF8A8A90)), fontSize = 10.sp
                                    ))
                                }
                                Text("C  ${r.modulos.getOrNull(0)?.resumo() ?: "—"}     F  ${r.modulos.getOrNull(1)?.resumo() ?: "—"}",
                                    GlanceModifier.padding(top = 8.dp),
                                    style = TextStyle(color = ColorProvider(Color(0xFF4C4C52)), fontSize = 12.sp))
                                Text("A  ${r.modulos.getOrNull(2)?.resumo() ?: "—"}     P  ${r.modulos.getOrNull(3)?.resumo() ?: "—"}     I  ${r.modulos.getOrNull(4)?.resumo() ?: "—"}",
                                    GlanceModifier.padding(top = 4.dp),
                                    style = TextStyle(color = ColorProvider(Color(0xFF77777D)), fontSize = 11.sp))
                            }
                        }
                    }
                    item {
                        Text("C conteúdos • F frequência • A plano de aula • P plano de curso • I PEI. * cobertura parcial; — não verificado.",
                            GlanceModifier.padding(vertical = 8.dp),
                            style = TextStyle(color = ColorProvider(Color(0xFF77777D)), fontSize = 9.sp))
                    }
                }
            }
        }
    }
}

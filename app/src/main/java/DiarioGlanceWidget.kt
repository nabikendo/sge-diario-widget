package br.to.seduc.sgediariowidget.widget

import android.content.Context
import android.content.res.Configuration
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
        val escuro = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val fundo = if (escuro) Color(0xFF101012) else Color(0xFFF4F4F6)
        val cartao = if (escuro) Color(0xFF202024) else Color.White
        val texto = if (escuro) Color(0xFFF5F5F7) else Color(0xFF1C1C1E)
        val secundario = if (escuro) Color(0xFFAAAAB0) else Color(0xFF77777D)
        val acento = if (escuro) Color(0xFFA6E69A) else Color(0xFF3478F6)
        val chip = if (escuro) Color(0xFF303035) else Color(0xFFF1F1F3)

        provideContent {
            Column(GlanceModifier.fillMaxSize().background(fundo).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                        Text("SGE • DIÁRIO", style = TextStyle(
                            color = ColorProvider(secundario), fontSize = 10.sp, fontWeight = FontWeight.Medium
                        ))
                        Text("Turmas", style = TextStyle(
                            color = ColorProvider(texto), fontSize = 20.sp, fontWeight = FontWeight.Bold
                        ))
                    }
                    Text("↻", GlanceModifier.padding(8.dp).clickable(actionRunCallback<RefreshActionCallback>()),
                        style = TextStyle(color = ColorProvider(acento), fontSize = 20.sp))
                }
                Text("Deslize para alternar entre as turmas",
                    style = TextStyle(color = ColorProvider(secundario), fontSize = 10.sp))

                if (painel.turmas.isEmpty()) {
                    Text(painel.mensagem, GlanceModifier.padding(top = 12.dp),
                        style = TextStyle(color = ColorProvider(secundario), fontSize = 12.sp), maxLines = 3)
                }

                LazyColumn(GlanceModifier.fillMaxSize().padding(top = 8.dp)) {
                    painel.turmas.forEachIndexed { indice, r ->
                        item {
                            Column(
                                GlanceModifier.fillMaxWidth().padding(vertical = 5.dp)
                                    .background(cartao).cornerRadius(22.dp)
                                    .clickable(actionStartActivity<MainActivity>()).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(GlanceModifier.defaultWeight()) {
                                        Text("${r.turma.disciplina.uppercase()}  ·  ${indice + 1} / ${painel.turmas.size}", style = TextStyle(
                                            color = ColorProvider(acento), fontSize = 9.sp, fontWeight = FontWeight.Bold
                                        ))
                                        Text(r.turma.nome.substringBefore('/'), style = TextStyle(
                                            color = ColorProvider(texto), fontSize = 17.sp, fontWeight = FontWeight.Bold
                                        ), maxLines = 1)
                                    }
                                    Text("›", style = TextStyle(
                                        color = ColorProvider(secundario), fontSize = 22.sp
                                    ))
                                }
                                Row(GlanceModifier.fillMaxWidth().padding(top = 12.dp)) {
                                    r.modulos.take(5).forEachIndexed { i, modulo ->
                                        Column(
                                            GlanceModifier.defaultWeight().padding(end = 5.dp)
                                                .background(chip).cornerRadius(12.dp).padding(vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(listOf("C", "F", "A", "P", "I").getOrElse(i) { "•" }, style = TextStyle(
                                                color = ColorProvider(secundario), fontSize = 9.sp, fontWeight = FontWeight.Bold
                                            ))
                                            Text(modulo.resumo(), style = TextStyle(
                                                color = ColorProvider(texto), fontSize = 11.sp, fontWeight = FontWeight.Bold
                                            ), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("C conteúdos • F frequência • A plano de aula • P plano de curso • I PEI. * cobertura parcial; — não verificado.",
                            GlanceModifier.padding(vertical = 8.dp),
                            style = TextStyle(color = ColorProvider(secundario), fontSize = 9.sp))
                    }
                }
            }
        }
    }
}

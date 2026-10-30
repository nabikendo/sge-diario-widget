package br.to.seduc.sgediariowidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import br.to.seduc.sgediariowidget.MainActivity
import br.to.seduc.sgediariowidget.data.SgeRepository
import br.to.seduc.sgediariowidget.data.SgeSummary
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker

class DiarioGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DiarioGlanceWidget()
}

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        SgeSyncWorker.sincronizarAgora(context)
    }
}

class DiarioGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summary = SgeRepository.lerResumo(context)
        provideContent {
            DiarioWidgetContent(summary)
        }
    }

    @Composable
    private fun DiarioWidgetContent(summary: SgeSummary) {
        val amoledBlack = ColorProvider(Color(0xFF000000))
        val surfaceGlass = ColorProvider(Color(0xFF111318))
        val textPrimary = ColorProvider(Color(0xFFF3F4F6))
        val textMuted = ColorProvider(Color(0xFF9CA3AF))
        val accentCyan = ColorProvider(Color(0xFF38BDF8))
        val statusOk = ColorProvider(Color(0xFF34D399))
        val statusAlert = ColorProvider(Color(0xFFF87171))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(amoledBlack)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "SGE 3.0 • DIÁRIO",
                        style = TextStyle(
                            color = accentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (summary.sessaoAtiva) "Atualizado ${summary.ultimaAtualizacao}" else "Sessão desconectada",
                        style = TextStyle(
                            color = textMuted,
                            fontSize = 10.sp
                        )
                    )
                }

                Box(
                    modifier = GlanceModifier
                        .background(surfaceGlass)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable(actionRunCallback<RefreshActionCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻ Sync",
                        style = TextStyle(
                            color = textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    ModuloChip("Conteúdos", summary.conteudos, surfaceGlass, textMuted, statusOk, statusAlert)
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    ModuloChip("Frequência", summary.frequencia, surfaceGlass, textMuted, statusOk, statusAlert)
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    ModuloChip("Plano Aula", summary.planosAula, surfaceGlass, textMuted, statusOk, statusAlert)
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    ModuloChip("Plano Curso", summary.planoCurso, surfaceGlass, textMuted, statusOk, statusAlert)
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    ModuloChip("PEIs", summary.peis, surfaceGlass, textMuted, statusOk, statusAlert)
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    ModuloChip("Total", summary.totalPendencias, surfaceGlass, textMuted, statusOk, statusAlert)
                }
            }
        }
    }

    @Composable
    private fun ModuloChip(
        titulo: String,
        valor: Int,
        surface: ColorProvider,
        textMuted: ColorProvider,
        statusOk: ColorProvider,
        statusAlert: ColorProvider
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(surface)
                .cornerRadius(12.dp)
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = valor.toString(),
                style = TextStyle(
                    color = if (valor == 0) statusOk else statusAlert,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = titulo,
                style = TextStyle(
                    color = textMuted,
                    fontSize = 10.sp
                )
            )
        }
    }
}

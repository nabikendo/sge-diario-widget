package br.to.seduc.sgediariowidget

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Barra de progresso em segmentos ("day loading"): pílulas verticais que acendem da esquerda para a direita.
 * Com [progresso] nulo, uma onda de segmentos percorre a barra (carregamento sem total conhecido).
 * Com "Remover animações" ligado, a barra fica estática.
 */
@Composable
fun BarraSegmentada(
    progresso: Float?,
    modifier: Modifier = Modifier,
    segmentos: Int = 24,
    altura: Dp = 14.dp,
    cor: Color = MaterialTheme.colorScheme.primary,
    trilho: Color = cor.copy(alpha = .18f),
    rotuloInicio: String? = null,
    rotuloFim: String? = null,
    mostrarPercentual: Boolean = false,
) {
    val alvo = progresso?.coerceIn(0f, 1f)
    // Cada segmento acende com a curva padrão: o avanço parece contínuo mesmo quando o valor pula.
    val atual by animateFloatAsState(alvo ?: 0f, SgeMotion.padrao(SgeMotion.PAGINA), label = "barraSegmentada")
    val onda: State<Float>? = if (alvo == null && SgeMotion.animacoesAtivas) {
        rememberInfiniteTransition(label = "ondaSegmentos").animateFloat(-0.25f, 1.25f,
            infiniteRepeatable(tween(1400, easing = SgeMotion.Padrao), RepeatMode.Restart), label = "ondaX")
    } else null
    val desc = if (alvo == null) "Carregando" else "${(alvo * 100).roundToInt()}%"
    Column(modifier.semantics {
        progressBarRangeInfo = if (alvo == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(alvo, 0f..1f)
    }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(altura)) {
            val n = segmentos.coerceAtLeast(2)
            val vao = (size.width / n) * .32f
            val largura = (size.width - vao * (n - 1)) / n
            val raio = CornerRadius(largura / 2f, largura / 2f)
            for (i in 0 until n) {
                val inicio = i / n.toFloat()
                val preenchido = if (alvo == null) {
                    val centro = onda?.value ?: -1f
                    // Onda de ~1/4 da barra, mais forte no centro.
                    (1f - abs((inicio + .5f / n) - centro) / .14f).coerceIn(0f, 1f)
                } else ((atual - inicio) * n).coerceIn(0f, 1f)
                drawRoundRect(lerp(trilho, cor, preenchido), Offset(i * (largura + vao), 0f), Size(largura, size.height), raio)
            }
        }
        if (rotuloInicio != null || rotuloFim != null || (mostrarPercentual && alvo != null)) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(rotuloInicio.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mostrarPercentual && alvo != null) Text(desc, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = cor)
            Text(rotuloFim.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

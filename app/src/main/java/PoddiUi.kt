package br.to.seduc.sgediariowidget

import androidx.compose.animation.core.LinearEasing
import br.to.seduc.sgediariowidget.data.contarSge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens OLED + menta (estética "Poddi"): fundo preto puro, cartões cinza profundo, rótulos #8E8E93.
internal val PretoOled = Color(0xFF000000)
internal val CartaoOled = Color(0xFF141414)
internal val CartaoOledAlto = Color(0xFF1C1C1E)
internal val BordaOled = Color(0xFF262629)
internal val PilulaNavOled = Color(0xFF161618)
internal val PilulaNavBorda = Color(0xFF2C2C2E)
internal val RotuloCinza = Color(0xFF8E8E93)

internal val RaioCartao = 32.dp
internal val RaioHero = 38.dp

/**
 * Brilho de carregamento (shimmer): faixa diagonal #141414 → #262629 → #141414 (tons claros no tema claro).
 * Aplique depois de `clip(forma)`. Com animações desativadas no sistema, fica um bloco estático.
 */
fun Modifier.shimmerLoading(): Modifier = composed {
    val escuro = MaterialTheme.colorScheme.background.luminance() < .5f
    val base = if (escuro) CartaoOled else Color(0xFFEDF2ED)
    val brilho = if (escuro) BordaOled else Color(0xFFF9FBF9)
    val progresso: State<Float>? = if (SgeMotion.duration(1) > 0) {
        rememberInfiniteTransition(label = "shimmerLoading").animateFloat(0f, 1f,
            infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "shimmerX")
    } else null
    drawBehind {
        val faixa = size.width.coerceAtLeast(1f)
        val inicio = -faixa + (size.width + faixa) * (progresso?.value ?: .5f)
        drawRect(Brush.linearGradient(listOf(base, brilho, base), start = Offset(inicio, 0f), end = Offset(inicio + faixa, faixa * .35f)))
    }
}

@Composable
private fun BlocoShimmer(modifier: Modifier, raio: Dp) = Box(modifier.clip(RoundedCornerShape(raio)).shimmerLoading())

/** Esqueleto com a mesma geometria da tela de cartões: cabeçalho, Hero (38 dp), filtros e cartões (32 dp). */
@Composable
fun DashboardSkeleton() {
    LazyVerticalGrid(columns = GridCells.Adaptive(285.dp), contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false, modifier = Modifier.semantics { contentDescription = "Carregando dados do SGE" }) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BlocoShimmer(Modifier.width(190.dp).height(34.dp), 12.dp)
                BlocoShimmer(Modifier.fillMaxWidth(.7f).height(14.dp), 7.dp)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { BlocoShimmer(Modifier.fillMaxWidth().height(300.dp), RaioHero) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { BlocoShimmer(Modifier.width(88.dp).height(38.dp), 50.dp) }
            }
        }
        items(4) { BlocoShimmer(Modifier.fillMaxWidth().height(190.dp), RaioCartao) }
    }
}

/**
 * Hero Card (menta): total de pendências em destaque, tag em pílula, barra de progresso estilo player
 * (turmas em dia) e botão circular de sincronizar. Zero parcial nunca é apresentado como confirmação.
 */
@Composable
fun HeroResumo(
    totalPendencias: Int, turmasComPendencia: Int, conferir: Int, emDia: Int, totalTurmas: Int, consultadas: Int,
    coberturaParcial: Boolean, escola: Boolean, carregando: Boolean, podeAtualizar: Boolean,
    descricaoAcao: String, aoAtualizar: () -> Unit
) {
    val semDados = consultadas == 0
    val progresso = if (totalTurmas > 0) emDia.toFloat() / totalTurmas else 0f
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(RaioHero))
        .background(Brush.verticalGradient(listOf(MentaClaro, MentaMedio)))) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = Color.White.copy(alpha = .6f), shape = CircleShape) {
                Text(if (escola) "STATUS DA ESCOLA" else "STATUS DO DIÁRIO", Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp), color = TintaMenta)
            }
            Column {
                Text(if (semDados) "—" else "$totalPendencias${if (coberturaParcial) "*" else ""}",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 64.sp, lineHeight = 66.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp),
                    color = TintaMenta)
                Text("PENDÊNCIAS NO SGE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp),
                    color = TintaMentaSuave)
            }
            Text(when {
                semDados -> "Nenhuma turma consultada ainda."
                else -> "${contarSge(turmasComPendencia, "turma com pendências", "turmas com pendências")} · $conferir a conferir"
            }, style = MaterialTheme.typography.bodyMedium, color = TintaMentaSuave)
            if (coberturaParcial && !semDados) Text("* Há consultas com cobertura parcial: um total parcial não confirma ausência de pendências.",
                style = MaterialTheme.typography.bodySmall, color = TintaMentaSuave)
            Surface(color = TintaMenta.copy(alpha = .1f), shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$emDia de ${contarSge(totalTurmas, "turma", "turmas")} em dia", style = MaterialTheme.typography.labelLarge, color = TintaMenta)
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(TintaMenta.copy(alpha = .15f))) {
                            Box(Modifier.fillMaxWidth(progresso.coerceIn(0f, 1f)).fillMaxHeight().background(TintaMenta, CircleShape))
                        }
                        Text("${contarSge(consultadas, "consultada", "consultadas")}${if (coberturaParcial) " (*)" else ""}",
                            style = MaterialTheme.typography.labelSmall, color = TintaMentaSuave)
                    }
                    Surface(onClick = aoAtualizar, enabled = podeAtualizar, shape = CircleShape, color = Color.White,
                        modifier = Modifier.semantics { contentDescription = descricaoAcao }) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            if (carregando) CircularProgressIndicator(Modifier.size(22.dp), color = TintaMenta, strokeWidth = 2.5.dp)
                            else Icon(SgeIcons.Refresh, null, Modifier.size(22.dp), tint = TintaMenta)
                        }
                    }
                }
            }
        }
    }
}

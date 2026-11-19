package br.to.seduc.sgediariowidget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Vidro líquido" (linguagem do iOS 26/27) em Compose: superfície translúcida em degradê, filete de luz no topo,
 * sombra suave e cantos contínuos. Não depende de desfoque de fundo, então funciona em qualquer Android.
 */
internal fun Modifier.vidro(forma: Shape, escuro: Boolean, elevacao: Dp = 16.dp): Modifier = this
    .shadow(elevacao, forma, clip = false, ambientColor = Color.Black.copy(alpha = if (escuro) .55f else .14f),
        spotColor = Color.Black.copy(alpha = if (escuro) .65f else .22f))
    .clip(forma)
    .background(Brush.verticalGradient(
        if (escuro) listOf(Color(0xFF303034).copy(alpha = .88f), Color(0xFF151517).copy(alpha = .94f))
        else listOf(Color.White.copy(alpha = .94f), Color(0xFFEFF1F4).copy(alpha = .88f))))
    .border(1.dp, Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (escuro) .30f else .95f), Color.White.copy(alpha = if (escuro) .05f else .28f))), forma)

private val RotulosDock = listOf("Diário", "Coordenação", "SGE Web")
private val DescricoesDock = listOf("Meu diário", "Coordenação", "SGE Web")

/**
 * Dock flutuante em cápsula de vidro: o item ativo ganha uma cápsula translúcida e mostra o rótulo ao lado do ícone
 * (a largura anima com mola); os demais mostram só o ícone. Pressionar encolhe levemente o item, com vibração curta.
 */
@Composable
internal fun DockVidro(aba: Int, visiveis: Set<Int>, escolher: (Int) -> Unit) {
    val escuro = MaterialTheme.colorScheme.background.luminance() < .5f
    val baixa = LocalConfiguration.current.screenHeightDp < 480
    val haptico = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (baixa) 6.dp else 14.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.vidro(CircleShape, escuro).padding(if (baixa) 4.dp else 6.dp).animateContentSize(SgeMotion.mola()),
            verticalAlignment = Alignment.CenterVertically) {
            for (i in 0..2) {
                if (i !in visiveis) continue
                val ativa = i == aba
                val origem = remember { MutableInteractionSource() }
                val pressionada by origem.collectIsPressedAsState()
                val escala by animateFloatAsState(if (pressionada) .95f else 1f, SgeMotion.mola(Spring.StiffnessMedium), label = "escalaAba$i")
                val fundo by animateColorAsState(
                    if (ativa) (if (escuro) Color.White.copy(alpha = .17f) else MaterialTheme.colorScheme.primary.copy(alpha = .15f)) else Color.Transparent,
                    SgeMotion.padrao(SgeMotion.PADRAO), label = "fundoAba$i")
                val cor by animateColorAsState(
                    if (ativa) (if (escuro) Color.White else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurfaceVariant,
                    SgeMotion.padrao(SgeMotion.PADRAO), label = "corAba$i")
                Row(Modifier.graphicsLayer { scaleX = escala; scaleY = escala }
                    .heightIn(min = if (baixa) 44.dp else 52.dp).widthIn(min = if (baixa) 44.dp else 52.dp)
                    .clip(CircleShape).background(fundo)
                    .selectable(selected = ativa, interactionSource = origem, indication = null, role = Role.Tab,
                        onClick = { if (!ativa) { haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove); escolher(i) } })
                    .semantics { contentDescription = DescricoesDock[i] }
                    .padding(horizontal = if (ativa) 16.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    Icon(listOf(SgeIcons.Cards, SgeIcons.People, SgeIcons.Globe)[i], null, Modifier.size(24.dp), tint = cor)
                    AnimatedVisibility(visible = ativa, enter = fadeIn(SgeMotion.padrao(SgeMotion.PADRAO - 40, atraso = 40)) + expandHorizontally(SgeMotion.mola()),
                        exit = fadeOut(SgeMotion.padrao(SgeMotion.RAPIDA - 20)) + shrinkHorizontally(SgeMotion.mola(Spring.StiffnessMedium))) {
                        Text(RotulosDock[i], Modifier.padding(start = 8.dp), color = cor, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

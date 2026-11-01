package br.to.seduc.sgediariowidget

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AppleClaro = lightColorScheme(
    primary = Color(0xFF3478F6), onPrimary = Color.White, secondary = Color(0xFF85858B),
    background = Color(0xFFF7F7F8), onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF0F0F2), onSurfaceVariant = Color(0xFF707076),
    secondaryContainer = Color(0xFFFFFFFF), onSecondaryContainer = Color(0xFF1C1C1E),
    error = Color(0xFFE5484D), tertiary = Color(0xFF368A5B), outline = Color(0xFFE4E4E7)
)
private val AppleEscuro = darkColorScheme(
    primary = Color(0xFF79A7FF), onPrimary = Color(0xFF101114), secondary = Color(0xFFA0A0A6),
    background = Color(0xFF111113), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1C1C1F), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF29292D), onSurfaceVariant = Color(0xFFAAAAB0),
    secondaryContainer = Color(0xFF303034), onSecondaryContainer = Color(0xFFF5F5F7),
    error = Color(0xFFFF6B6B), tertiary = Color(0xFF70C492), outline = Color(0xFF39393E)
)
private val TipografiaApple = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp)
)

/** tema: 0 sistema, 1 claro, 2 escuro. estilo: 0 Apple, 1 cores dinâmicas do sistema (Material You). */
@Composable
fun SgeTema(tema: Int, estilo: Int, content: @Composable () -> Unit) {
    val escuro = when (tema) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val esquema = if (estilo == 1 && Build.VERSION.SDK_INT >= 31) {
        if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (escuro) AppleEscuro else AppleClaro
    MaterialTheme(
        colorScheme = esquema, typography = TipografiaApple,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp))
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

@Composable private fun temaClaro() = MaterialTheme.colorScheme.background.luminance() > 0.5f
@Composable fun corErro() = MaterialTheme.colorScheme.error
@Composable fun corOk() = MaterialTheme.colorScheme.tertiary
@Composable fun corAlerta() = if (temaClaro()) Color(0xFFFF9500) else Color(0xFFFF9F0A)
@Composable fun corAtencao() = if (temaClaro()) Color(0xFFB8860B) else Color(0xFFFFD60A)

/** Controle segmentado ao estilo iOS. */
@Composable
fun Segmentado(opcoes: List<String>, selecionado: Int, aoSelecionar: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp)
    ) {
        opcoes.forEachIndexed { i, texto ->
            val sel = i == selecionado
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { aoSelecionar(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    texto, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Navegação fixa e discreta, mantendo a área de conteúdo livre para rolagem. */
@Composable
fun NavegacaoInferior(opcoes: List<String>, selecionado: Int, aoSelecionar: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        opcoes.forEachIndexed { i, texto ->
            NavigationBarItem(
                selected = selecionado == i,
                onClick = { aoSelecionar(i) },
                icon = {
                    Text(listOf("▦", "◉", "▤")[i], style = MaterialTheme.typography.titleMedium,
                        color = if (selecionado == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                label = { Text(texto, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/** Cartões em 1 coluna no celular, 2 em tablets e 3 em telas largas. */
@Composable
fun <T> Grade(itens: List<T>, conteudo: @Composable (T) -> Unit) {
    val largura = LocalConfiguration.current.screenWidthDp
    val colunas = when { largura >= 900 -> 3; largura >= 600 -> 2; else -> 1 }
    itens.chunked(colunas).forEach { linha ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            linha.forEach { Box(Modifier.weight(1f)) { conteudo(it) } }
            repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

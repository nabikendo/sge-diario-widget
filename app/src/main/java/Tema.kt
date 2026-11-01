package br.to.seduc.sgediariowidget

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NeutroClaro = lightColorScheme(
    primary = Color(0xFF2767D9), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9F0FF), onPrimaryContainer = Color(0xFF2358AD),
    secondary = Color(0xFF777984), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF0F4), onSecondaryContainer = Color(0xFF363942),
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF171A20),
    surface = Color.White, onSurface = Color(0xFF171A20),
    surfaceVariant = Color(0xFFEEF0F4), onSurfaceVariant = Color(0xFF686D78),
    outline = Color(0xFFDFE2E8), outlineVariant = Color(0xFFECEEF2),
    error = Color(0xFFBD4A42), onError = Color.White,
    errorContainer = Color(0xFFFFECE9), onErrorContainer = Color(0xFF963F39),
    tertiary = Color(0xFF327E61), tertiaryContainer = Color(0xFFE7F3ED),
    onTertiaryContainer = Color(0xFF27654E), surfaceTint = Color.Transparent
)
private val NeutroEscuro = darkColorScheme(
    primary = Color(0xFF8AB5FF), onPrimary = Color(0xFF112139),
    primaryContainer = Color(0xFF253449), onPrimaryContainer = Color(0xFFBAD2FA),
    secondary = Color(0xFFA3A8B2), onSecondary = Color(0xFF151820),
    secondaryContainer = Color(0xFF292E37), onSecondaryContainer = Color(0xFFD4D8E1),
    background = Color(0xFF101216), onBackground = Color(0xFFF2F3F6),
    surface = Color(0xFF1B1F26), onSurface = Color(0xFFF2F3F6),
    surfaceVariant = Color(0xFF272C35), onSurfaceVariant = Color(0xFFA6ABB6),
    outline = Color(0xFF373D48), outlineVariant = Color(0xFF282D36),
    error = Color(0xFFF5A296), onError = Color(0xFF431612),
    errorContainer = Color(0xFF422826), onErrorContainer = Color(0xFFF0B7AC),
    tertiary = Color(0xFF94CCAF), tertiaryContainer = Color(0xFF233A30),
    onTertiaryContainer = Color(0xFFA8D6BB), surfaceTint = Color.Transparent
)
private fun tipo(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight,
    letterSpacing = if (size >= 22) (-0.6).sp else 0.sp
)
private val TipografiaNeutra = Typography(
    displaySmall = tipo(34, 40, FontWeight.Bold), headlineMedium = tipo(28, 34, FontWeight.Bold),
    headlineSmall = tipo(24, 30, FontWeight.Bold), titleLarge = tipo(22, 28, FontWeight.SemiBold),
    titleMedium = tipo(17, 23, FontWeight.SemiBold), titleSmall = tipo(15, 21, FontWeight.SemiBold),
    bodyLarge = tipo(16, 24), bodyMedium = tipo(14, 21), bodySmall = tipo(12, 18),
    labelLarge = tipo(13, 19, FontWeight.SemiBold), labelMedium = tipo(12, 17, FontWeight.Medium),
    labelSmall = tipo(11, 16, FontWeight.Medium)
)

/** Tema e cor são preferências independentes; os valores antigos continuam compatíveis. */
@Composable
fun SgeTema(tema: Int, estilo: Int = 0, content: @Composable () -> Unit) {
    val escuro = when (tema) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val esquema = if (estilo == 1 && Build.VERSION.SDK_INT >= 31) {
        if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (escuro) NeutroEscuro else NeutroClaro
    MaterialTheme(colorScheme = esquema, typography = TipografiaNeutra,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(34.dp))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
@Composable fun corErro() = MaterialTheme.colorScheme.error
@Composable fun corOk() = MaterialTheme.colorScheme.tertiary
@Composable fun corAlerta() = if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFF92651E) else Color(0xFFE0BB78)

@Composable
fun Segmentado(opcoes: List<String>, selecionado: Int, aoSelecionar: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
        opcoes.forEachIndexed { i, texto ->
            val ativo = i == selecionado
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                .background(if (ativo) MaterialTheme.colorScheme.surface else Color.Transparent)
                .selectable(selected = ativo, role = Role.Tab, onClick = { aoSelecionar(i) })
                .padding(vertical = 11.dp, horizontal = 3.dp), contentAlignment = Alignment.Center) {
                Text(texto, style = MaterialTheme.typography.labelLarge,
                    color = if (ativo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

package br.to.seduc.sgediariowidget

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens compartilhados do design: identidades menta e estados legíveis também em fundos fixos.
internal val MentaClaro = Color(0xFFD8F5D1)
internal val MentaMedio = Color(0xFFB2E3AA)
internal val TintaMenta = Color(0xFF0B1F0E)
internal val TintaMentaSuave = Color(0xFF2A4A2E)
internal val StatusAlertaMenta = Color(0xFF78350F)
internal val StatusRegularMenta = Color(0xFF047857)
internal val StatusPendenteMenta = Color(0xFF9F1239)
private val AlertaClaro = Color(0xFF92651E)
private val AlertaEscuro = Color(0xFFFBBF24)

// Design "OLED + verde-menta": preto puro #000000, cartões #141414/#1C1C1E e destaque menta (ver PoddiUi.kt).
private val NeutroClaro = lightColorScheme(
    primary = Color(0xFF2F7A4B), onPrimary = Color.White,
    primaryContainer = MentaClaro, onPrimaryContainer = TintaMenta,
    secondary = Color(0xFF59635C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF0EA), onSecondaryContainer = Color(0xFF243828),
    background = Color(0xFFF5F8F5), onBackground = Color(0xFF0F1210),
    surface = Color.White, onSurface = Color(0xFF0F1210),
    surfaceVariant = Color(0xFFEAF0EA), onSurfaceVariant = Color(0xFF59635C),
    outline = Color(0xFFD0D9D2), outlineVariant = Color(0xFFE4EAE5),
    error = Color(0xFFE11D48), onError = Color.White,
    errorContainer = Color(0xFFFFE4E8), onErrorContainer = Color(0xFF9F1239),
    tertiary = Color(0xFF2F7A4B), tertiaryContainer = MentaClaro,
    onTertiaryContainer = TintaMenta, surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7FAF7), surfaceContainer = Color(0xFFF2F6F2),
    surfaceContainerHigh = Color(0xFFEDF2ED), surfaceContainerHighest = Color(0xFFE7EDE7)
)
private val NeutroEscuro = darkColorScheme(
    primary = MentaMedio, onPrimary = TintaMenta,
    primaryContainer = Color(0xFF1B2A1D), onPrimaryContainer = MentaClaro,
    secondary = RotuloCinza, onSecondary = PretoOled,
    secondaryContainer = CartaoOledAlto, onSecondaryContainer = Color(0xFFF4F6F5),
    background = PretoOled, onBackground = Color(0xFFF4F6F5),
    surface = CartaoOled, onSurface = Color(0xFFF4F6F5),
    surfaceVariant = CartaoOledAlto, onSurfaceVariant = RotuloCinza,
    outline = PilulaNavBorda, outlineVariant = BordaOled,
    error = Color(0xFFF43F5E), onError = Color.White,
    errorContainer = Color(0xFF371F24), onErrorContainer = Color(0xFFFDA4AF),
    tertiary = MentaMedio, tertiaryContainer = Color(0xFF1B2A1D),
    onTertiaryContainer = MentaClaro, surfaceTint = Color.Transparent,
    surfaceContainerLowest = PretoOled, surfaceContainerLow = Color(0xFF0C0C0C), surfaceContainer = CartaoOled,
    surfaceContainerHigh = CartaoOledAlto, surfaceContainerHighest = BordaOled
)
@OptIn(ExperimentalTextApi::class)
private val Jakarta = FontFamily(listOf(400, 500, 600, 700, 800, 900).map { w ->
    Font(R.font.plus_jakarta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})
private fun tipo(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = if (size >= 22) -0.6f else 0f) = TextStyle(
    fontFamily = Jakarta, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight, letterSpacing = tracking.sp
)
private val TipografiaNeutra = Typography(
    displaySmall = tipo(34, 40, FontWeight.Black, -1f), headlineMedium = tipo(30, 36, FontWeight.Black, -1f),
    headlineSmall = tipo(24, 30, FontWeight.Bold), titleLarge = tipo(21, 27, FontWeight.Bold),
    titleMedium = tipo(17, 23, FontWeight.Bold), titleSmall = tipo(15, 21, FontWeight.SemiBold),
    bodyLarge = tipo(16, 24, FontWeight.Medium), bodyMedium = tipo(14, 21, FontWeight.Medium), bodySmall = tipo(12, 18, FontWeight.Medium),
    labelLarge = tipo(13, 19, FontWeight.SemiBold), labelMedium = tipo(12, 17, FontWeight.SemiBold),
    labelSmall = tipo(11, 16, FontWeight.SemiBold)
)

/** Tema e cor são preferências independentes; os valores antigos continuam compatíveis. */
@Composable
fun SgeTema(tema: Int, estilo: Int = 0, content: @Composable () -> Unit) {
    val escuro = when (tema) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val esquema = if (estilo == 1 && Build.VERSION.SDK_INT >= 31) {
        if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (escuro) NeutroEscuro else NeutroClaro
    // Alto contraste: textos secundários e contornos mais fortes; a situação nunca depende só da cor (há texto e ícone).
    val finalEsquema = if (SgeUi.altoContraste) esquema.copy(onSurfaceVariant = esquema.onSurface,
        outline = esquema.onSurface.copy(alpha = .7f), outlineVariant = esquema.onSurface.copy(alpha = .5f),
        error = if (escuro) Color(0xFFFFB4A9) else Color(0xFF8C1D18), tertiary = if (escuro) Color(0xFFB8F0D0) else Color(0xFF1B5E42)) else esquema
    val dens = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(dens.density, dens.fontScale * (if (SgeUi.fonteGrande) 1.3f else 1f))) {
        MaterialTheme(colorScheme = finalEsquema, typography = TipografiaNeutra,
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(RaioCartao), extraLarge = RoundedCornerShape(RaioHero))) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}
@Composable fun corErro() = MaterialTheme.colorScheme.error
@Composable fun corOk() = MaterialTheme.colorScheme.tertiary
@Composable fun corAlerta() = if (MaterialTheme.colorScheme.background.luminance() > .5f) AlertaClaro else AlertaEscuro

@Composable
fun Segmentado(opcoes: List<String>, selecionado: Int, aoSelecionar: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
        opcoes.forEachIndexed { i, texto ->
            val ativo = i == selecionado
            val fundo by animateColorAsState(if (ativo) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(SgeMotion.duration(150)), label = "fundoSegmento$i")
            val corTexto by animateColorAsState(if (ativo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(SgeMotion.duration(150)), label = "textoSegmento$i")
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                .background(fundo)
                .selectable(selected = ativo, role = Role.Tab, onClick = { aoSelecionar(i) })
                .padding(vertical = 11.dp, horizontal = 3.dp), contentAlignment = Alignment.Center) {
                Text(texto, style = MaterialTheme.typography.labelLarge, color = corTexto)
            }
        }
    }
}

/** Filtro rápido em pílula: ativo = verde-menta com texto escuro. */
@Composable
fun PilulaFiltro(selecionado: Boolean, aoClicar: () -> Unit, conteudo: @Composable () -> Unit) {
    val fundo by animateColorAsState(if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        animationSpec = tween(SgeMotion.duration(150)), label = "pilulaFiltro")
    Surface(onClick = aoClicar, shape = CircleShape, color = fundo,
        contentColor = if (selecionado) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selecionado) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.semantics { this.selected = selecionado }) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) { ProvideTextStyle(MaterialTheme.typography.labelLarge) { conteudo() } }
    }
}

/** Botão quadrado arredondado (atualizar, buscar, filtrar). */
@Composable
fun BotaoQuadrado(descricao: String, aoClicar: () -> Unit, enabled: Boolean = true, marcado: Boolean = false, icone: @Composable () -> Unit) {
    Surface(onClick = aoClicar, enabled = enabled, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (marcado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.semantics { contentDescription = descricao }) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { icone() }
    }
}

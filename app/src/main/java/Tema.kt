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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

// Paleta de destaque do Hero e dos selos. Menta (padrão) ou Lavanda, escolhida em Ajustes → Aparência.
@Immutable
data class PaletaDestaque(val claro: Color, val medio: Color, val tinta: Color, val tintaSuave: Color, val brilho: Color)
private val Menta1 = Color(0xFFD8F5D1); private val Menta2 = Color(0xFFB2E3AA)
private val Menta3 = Color(0xFF0B1F0E); private val Menta4 = Color(0xFF2A4A2E)
internal val PaletaMenta = PaletaDestaque(Menta1, Menta2, Menta3, Menta4, Color(0xFFB2E3AA))
/** Lavanda → azul-pervinca, inspirada em painéis de produtividade limpos. */
internal val PaletaLavanda = PaletaDestaque(Color(0xFFEEE9FF), Color(0xFFD6DEFF), Color(0xFF1C1340), Color(0xFF4A4080), Color(0xFFC9BEFF))
internal val LocalPaleta = staticCompositionLocalOf { PaletaMenta }
internal val MentaClaro: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.claro
internal val MentaMedio: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.medio
internal val TintaMenta: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.tinta
internal val TintaMentaSuave: Color @Composable @ReadOnlyComposable get() = LocalPaleta.current.tintaSuave

/** Estilos de cor gravados em "estilo": 0 verde (menta), 1 cores do sistema, 2 lavanda. */
object EstiloCor { const val VERDE = 0; const val SISTEMA = 1; const val LAVANDA = 2 }
internal val StatusAlertaMenta = Color(0xFF78350F)
internal val StatusRegularMenta = Color(0xFF047857)
internal val StatusPendenteMenta = Color(0xFF9F1239)
private val AlertaClaro = Color(0xFF92651E)
private val AlertaEscuro = Color(0xFFFBBF24)

// Design "OLED + verde-menta": preto puro #000000, cartões #141414/#1C1C1E e destaque menta (ver PoddiUi.kt).
private val NeutroClaro = lightColorScheme(
    primary = Color(0xFF2F7A4B), onPrimary = Color.White,
    primaryContainer = Menta1, onPrimaryContainer = Menta3,
    secondary = Color(0xFF59635C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF0EA), onSecondaryContainer = Color(0xFF243828),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF0F1210),
    surface = Color.White, onSurface = Color(0xFF0F1210),
    surfaceVariant = Color(0xFFEAF0EA), onSurfaceVariant = Color(0xFF59635C),
    outline = Color(0xFFD1D1D6), outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFE11D48), onError = Color.White,
    errorContainer = Color(0xFFFFE4E8), onErrorContainer = Color(0xFF9F1239),
    tertiary = Color(0xFF2F7A4B), tertiaryContainer = Menta1,
    onTertiaryContainer = Menta3, surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7FAF7), surfaceContainer = Color(0xFFF2F6F2),
    surfaceContainerHigh = Color(0xFFEDF2ED), surfaceContainerHighest = Color(0xFFE7EDE7)
)
private val NeutroEscuro = darkColorScheme(
    primary = Menta2, onPrimary = Menta3,
    primaryContainer = Color(0xFF1B2A1D), onPrimaryContainer = Menta1,
    secondary = RotuloCinza, onSecondary = PretoOled,
    secondaryContainer = CartaoOledAlto, onSecondaryContainer = Color(0xFFF4F6F5),
    background = PretoOled, onBackground = Color(0xFFF4F6F5),
    surface = CartaoOled, onSurface = Color(0xFFF4F6F5),
    surfaceVariant = CartaoOledAlto, onSurfaceVariant = RotuloCinza,
    outline = PilulaNavBorda, outlineVariant = BordaOled,
    error = Color(0xFFF43F5E), onError = Color.White,
    errorContainer = Color(0xFF371F24), onErrorContainer = Color(0xFFFDA4AF),
    tertiary = Menta2, tertiaryContainer = Color(0xFF1B2A1D),
    onTertiaryContainer = Menta1, surfaceTint = Color.Transparent,
    surfaceContainerLowest = PretoOled, surfaceContainerLow = Color(0xFF0C0C0C), surfaceContainer = CartaoOled,
    surfaceContainerHigh = CartaoOledAlto, surfaceContainerHighest = BordaOled
)
// Lavanda: o destaque muda; verde continua sendo "em dia" (tertiary) para não mudar o significado dos estados.
private val LavandaClaro = NeutroClaro.copy(
    primary = Color(0xFF6B54EE), onPrimary = Color.White,
    primaryContainer = Color(0xFFEEE9FF), onPrimaryContainer = Color(0xFF1C1340),
    secondary = Color(0xFF615C78), secondaryContainer = Color(0xFFF1EFF9), onSecondaryContainer = Color(0xFF2A2545),
    background = Color(0xFFF6F5FB), onBackground = Color(0xFF14121C), onSurface = Color(0xFF14121C),
    surfaceVariant = Color(0xFFF1EFF9), onSurfaceVariant = Color(0xFF615C78),
    outline = Color(0xFFD7D4E4), outlineVariant = Color(0xFFE9E7F1),
    surfaceContainerLow = Color(0xFFFAF9FE), surfaceContainer = Color(0xFFF4F2FB),
    surfaceContainerHigh = Color(0xFFEFEDF8), surfaceContainerHighest = Color(0xFFE9E6F4)
)
private val LavandaEscuro = NeutroEscuro.copy(
    primary = Color(0xFFBBADFF), onPrimary = Color(0xFF1C1340),
    primaryContainer = Color(0xFF241D40), onPrimaryContainer = Color(0xFFEEE9FF)
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
    val esquema = if (estilo == EstiloCor.SISTEMA && Build.VERSION.SDK_INT >= 31) {
        if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (estilo == EstiloCor.LAVANDA) { if (escuro) LavandaEscuro else LavandaClaro }
    else if (escuro) NeutroEscuro else NeutroClaro
    val paleta = if (estilo == EstiloCor.LAVANDA) PaletaLavanda else PaletaMenta
    // Alto contraste: textos secundários e contornos mais fortes; a situação nunca depende só da cor (há texto e ícone).
    val finalEsquema = if (SgeUi.altoContraste) esquema.copy(onSurfaceVariant = esquema.onSurface,
        outline = esquema.onSurface.copy(alpha = .7f), outlineVariant = esquema.onSurface.copy(alpha = .5f),
        error = if (escuro) Color(0xFFFFB4A9) else Color(0xFF8C1D18), tertiary = if (escuro) Color(0xFFB8F0D0) else Color(0xFF1B5E42)) else esquema
    val dens = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(dens.density, dens.fontScale * (if (SgeUi.fonteGrande) 1.3f else 1f)), LocalPaleta provides paleta) {
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
                animationSpec = SgeMotion.padrao(SgeMotion.RAPIDA + 30), label = "fundoSegmento$i")
            val corTexto by animateColorAsState(if (ativo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = SgeMotion.padrao(SgeMotion.RAPIDA + 30), label = "textoSegmento$i")
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
        animationSpec = SgeMotion.padrao(SgeMotion.RAPIDA + 30), label = "pilulaFiltro")
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

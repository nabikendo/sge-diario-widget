package br.to.seduc.sgediariowidget

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.to.seduc.sgediariowidget.data.GrupoPendencias
import br.to.seduc.sgediariowidget.data.SgePainelStore
import br.to.seduc.sgediariowidget.data.SgePrivacidade

/**
 * Linguagem de movimento do app (personalidade "corporativa": firme, sem ultrapassagem).
 * Três durações, três curvas e transições prontas. Com "Remover animações" ligado no Android,
 * tudo vira instantâneo e o estado final continua correto.
 */
object SgeMotion {
    /** Toques, cores de estado, saídas curtas. */
    const val RAPIDA = 120
    /** Mostrar/esconder blocos, cartões, trocas dentro da tela. */
    const val PADRAO = 220
    /** Troca de tela inteira. */
    const val PAGINA = 320

    /** Curva-assinatura para o que já está na tela (Material 3 Standard). */
    val Padrao: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** O que entra desacelera até pousar (Material 3 Emphasized Decelerate). */
    val Entrada: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** O que sai acelera ao partir (Material 3 Emphasized Accelerate). */
    val Saida: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val animacoesAtivas: Boolean get() = ValueAnimator.areAnimatorsEnabled()
    fun duration(requestedMillis: Int): Int = if (animacoesAtivas) requestedMillis else 0

    fun <T> padrao(ms: Int = PADRAO, atraso: Int = 0): FiniteAnimationSpec<T> = tween(duration(ms), duration(atraso), Padrao)
    fun <T> entrada(ms: Int = PADRAO): FiniteAnimationSpec<T> = tween(duration(ms), easing = Entrada)
    fun <T> saida(ms: Int = RAPIDA + 40): FiniteAnimationSpec<T> = tween(duration(ms), easing = Saida)
    /** Mola sem ultrapassagem para movimentos que podem ser interrompidos (toques, abas). */
    fun <T> mola(rigidez: Float = Spring.StiffnessMediumLow): FiniteAnimationSpec<T> =
        if (animacoesAtivas) spring(dampingRatio = 0.85f, stiffness = rigidez) else snap()

    /** Abrir uma tela: a nova entra pela direita e a de baixo recua um pouco (paralaxe). */
    fun avancar(): ContentTransform =
        (slideInHorizontally(entrada(PAGINA)) { it } + fadeIn(padrao(PADRAO - 40))) togetherWith
            (slideOutHorizontally(entrada(PAGINA)) { -it / 4 } + fadeOut(padrao(PADRAO)))

    /** Voltar: a de baixo retorna e a atual sai pela direita, mais rápido que entrou. */
    fun voltar(): ContentTransform =
        (slideInHorizontally(entrada(PAGINA - 40)) { -it / 4 } + fadeIn(padrao(PADRAO))) togetherWith
            (slideOutHorizontally(saida(PADRAO + 20)) { it } + fadeOut(padrao(PADRAO - 40, atraso = 40)))

    /** Troca lateral entre irmãos (abas, etapas): deslocamento curto na direção escolhida. */
    fun lateral(direcao: Int, fracaoEntra: Int = 6, fracaoSai: Int = 10): ContentTransform =
        (slideInHorizontally(entrada(PAGINA - 40)) { direcao * it / fracaoEntra } + fadeIn(padrao(PADRAO))) togetherWith
            (slideOutHorizontally(saida(PADRAO - 40)) { -direcao * it / fracaoSai } + fadeOut(padrao(RAPIDA)))

    /** Troca discreta no mesmo lugar (detalhe ao lado, conteúdo que muda). */
    fun suave(): ContentTransform =
        (fadeIn(padrao(PADRAO - 50)) + slideInHorizontally(entrada(PADRAO)) { it / 16 }) togetherWith
            fadeOut(padrao(RAPIDA - 20))

    /** Bloco que abre e fecha na vertical: entrada mais longa que a saída. */
    fun abrirBloco(): EnterTransition = expandVertically(entrada(PADRAO)) + fadeIn(padrao(PADRAO - 60, atraso = 40))
    fun fecharBloco(): ExitTransition = shrinkVertically(saida(PADRAO - 50)) + fadeOut(padrao(RAPIDA - 20))
}

/** Preferências de interface lidas pelas telas: privacidade, fonte maior, alto contraste e pedido de busca pelo teclado. */
object SgeUi {
    const val PRIVACIDADE = "ui_privacidade"
    const val FONTE_GRANDE = "ui_fonte_grande"
    const val ALTO_CONTRASTE = "ui_alto_contraste"
    var privacidade by mutableStateOf(false); private set
    var fonteGrande by mutableStateOf(false); private set
    var altoContraste by mutableStateOf(false); private set
    var buscaPedida by mutableStateOf(0)

    fun carregar(ctx: Context) {
        val p = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        privacidade = p.getBoolean(PRIVACIDADE, false); fonteGrande = p.getBoolean(FONTE_GRANDE, false); altoContraste = p.getBoolean(ALTO_CONTRASTE, false)
    }
    fun definir(ctx: Context, chave: String, valor: Boolean) {
        ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(chave, valor).apply()
        carregar(ctx); atividade(ctx)?.let { aplicarJanela(it) }
    }
    private fun atividade(ctx: Context): Activity? { var c: Context? = ctx; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }
    /** Com a privacidade ligada, o Android esconde o app na tela de recentes e bloqueia capturas de tela. */
    fun aplicarJanela(a: Activity) {
        if (privacidade) a.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else a.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private fun grupoDeProfessor(g: GrupoPendencias) = SgeUi.privacidade && g.chave.startsWith("professor:")
fun tituloVisivel(g: GrupoPendencias) =
    if (grupoDeProfessor(g) && g.titulo != "Docência compartilhada" && g.titulo != "Professor não identificado") SgePrivacidade.iniciais(g.titulo) else g.titulo
fun subtituloVisivel(g: GrupoPendencias) =
    if (grupoDeProfessor(g) && g.titulo == "Docência compartilhada") g.subtitulo.split(" · ").joinToString(" · ") { SgePrivacidade.iniciais(it) } else g.subtitulo

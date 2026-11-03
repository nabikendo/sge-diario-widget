package br.to.seduc.sgediariowidget

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.to.seduc.sgediariowidget.data.GrupoPendencias
import br.to.seduc.sgediariowidget.data.SgePainelStore
import br.to.seduc.sgediariowidget.data.SgePrivacidade

/** Duração comum de movimento, respeitando a escala de animações configurada no Android. */
object SgeMotion {
    fun duration(requestedMillis: Int): Int = if (ValueAnimator.areAnimatorsEnabled()) requestedMillis else 0
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

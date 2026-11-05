package br.to.seduc.sgediariowidget

import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Abre o SGE Web na versão de computador (barra superior completa, com o seletor de ano letivo).
 * Combina user agent de desktop, viewport largo e zoom, como o "Site para computador" do Chrome.
 */
object SgeWebComputador {
    /** Largura lógica (px CSS) usada no layout; acima de 1200 o Bootstrap usa o grid "xl". */
    private const val LARGURA = 1280

    fun aplicar(s: WebSettings) {
        val chrome = Regex("Chrome/([0-9.]+)").find(s.userAgentString.orEmpty())?.groupValues?.get(1) ?: "124.0.0.0"
        s.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
    }

    /** Páginas responsivas ignoram o user agent e usam <meta viewport>; troca por largura fixa de computador. */
    fun viewport(view: WebView?) {
        view?.evaluateJavascript(
            """(function(){var c='width=$LARGURA';var m=document.querySelector('meta[name=viewport]');
            if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}
            if(m.getAttribute('content')!==c)m.setAttribute('content',c);})()""", null)
    }
}

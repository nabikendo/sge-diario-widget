package br.to.seduc.sgediariowidget

import android.webkit.WebSettings
import android.webkit.WebView

/** Configura o mesmo WebView para desktop (barra completa do SGE) ou para celular. */
object SgeWebComputador {
    /** Largura lógica (px CSS) usada no layout de computador. */
    private const val LARGURA = 1280
    private const val ATRIBUTO_ORIGINAL = "data-sge-viewport-original"
    private const val ATRIBUTO_CRIADO = "data-sge-viewport-criado"

    fun agenteComputador(agenteCelular: String): String {
        val chrome = Regex("Chrome/([0-9.]+)").find(agenteCelular)?.groupValues?.get(1) ?: "124.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    }

    fun aplicar(s: WebSettings, computador: Boolean = true, agenteCelular: String = s.userAgentString.orEmpty()) {
        s.userAgentString = if (computador) agenteComputador(agenteCelular) else agenteCelular
        s.useWideViewPort = computador
        s.loadWithOverviewMode = computador
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
    }

    /** Desktop usa um viewport largo; ao voltar ao celular, restaura o meta viewport original. */
    fun viewport(view: WebView?, computador: Boolean = true) {
        val script = if (computador) {
            """(function(){var m=document.querySelector('meta[name=viewport]');
            if(!m){m=document.createElement('meta');m.name='viewport';m.setAttribute('$ATRIBUTO_CRIADO','1');(document.head||document.documentElement).appendChild(m);}
            else if(!m.hasAttribute('$ATRIBUTO_ORIGINAL'))m.setAttribute('$ATRIBUTO_ORIGINAL',m.getAttribute('content')||'');
            m.setAttribute('content','width=$LARGURA');})()"""
        } else {
            """(function(){var m=document.querySelector('meta[name=viewport]');if(!m)return;
            if(m.getAttribute('$ATRIBUTO_CRIADO')==='1'){m.remove();return;}
            var o=m.getAttribute('$ATRIBUTO_ORIGINAL');if(o!==null){m.setAttribute('content',o);m.removeAttribute('$ATRIBUTO_ORIGINAL');}})()"""
        }
        view?.evaluateJavascript(script, null)
    }
}

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

    /** Em telas a partir desta largura (dp) o SGE já cabe na largura nativa; não é preciso reduzir a página. */
    private const val LARGURA_NATIVA_DP = 1000
    private const val VIEWPORT_NATIVO = "width=device-width, initial-scale=1"
    const val CAMINHO_LOGIN = "/Acesso/Entrar"

    fun ehLogin(url: String?): Boolean = url?.contains(CAMINHO_LOGIN, ignoreCase = true) == true

    /**
     * Conteúdo do meta viewport para a página atual; null mantém o original da página.
     * O login sempre usa a largura do aparelho, para o formulário abrir inteiro e centralizado.
     * Fora do login, o modo computador usa 1280 px em telas estreitas e a largura nativa em telas largas.
     */
    fun conteudoViewport(computador: Boolean, login: Boolean, larguraDp: Int): String? = when {
        login -> VIEWPORT_NATIVO
        !computador -> null
        larguraDp >= LARGURA_NATIVA_DP -> VIEWPORT_NATIVO
        else -> "width=$LARGURA"
    }

    private fun larguraDp(view: WebView): Int {
        val densidade = view.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        return if (view.width > 0) (view.width / densidade).toInt() else view.resources.configuration.screenWidthDp
    }

    /** Ajusta o viewport à página e ao aparelho; ao voltar ao celular, restaura o meta viewport original. */
    fun viewport(view: WebView?, computador: Boolean = true, url: String? = view?.url) {
        if (view == null) return
        val login = ehLogin(url)
        val conteudo = conteudoViewport(computador, login, larguraDp(view))
        val ajuste = if (conteudo != null) {
            """var m=document.querySelector('meta[name=viewport]');
            if(!m){m=document.createElement('meta');m.name='viewport';m.setAttribute('$ATRIBUTO_CRIADO','1');(document.head||document.documentElement).appendChild(m);}
            else if(!m.hasAttribute('$ATRIBUTO_ORIGINAL'))m.setAttribute('$ATRIBUTO_ORIGINAL',m.getAttribute('content')||'');
            m.setAttribute('content','$conteudo');"""
        } else {
            """var m=document.querySelector('meta[name=viewport]');
            if(m){if(m.getAttribute('$ATRIBUTO_CRIADO')==='1'){m.remove();}
            else{var o=m.getAttribute('$ATRIBUTO_ORIGINAL');if(o!==null){m.setAttribute('content',o);m.removeAttribute('$ATRIBUTO_ORIGINAL');}}}"""
        }
        view.evaluateJavascript("(function(){$ajuste})()", null)
        if (login) {
            view.evaluateJavascript(SCRIPT_CENTRALIZAR_LOGIN, null)
            view.scrollTo(0, 0)
        }
    }

    /**
     * Força o formulário de login a ficar no centro da tela, sobrepondo o layout original do SGE:
     * 1) CSS com !important em html/body e #content_login; 2) neutraliza floats, margens e larguras dos ancestrais
     * e esconde colunas vazias (decorativas) que empurram o formulário; 3) se, medido na tela, o centro ainda
     * estiver fora do centro, fixa o formulário no meio da janela. Repete algumas vezes porque o SGE monta a página aos poucos.
     */
    internal const val SCRIPT_CENTRALIZAR_LOGIN = """(function(){
var tent=0;
function ajustar(){
  var alvo=document.getElementById('content_login')||document.getElementById('login-form');
  if(!alvo){if(tent++<15)setTimeout(ajustar,200);return;}
  var st=document.getElementById('sge-login-centro');
  if(!st){st=document.createElement('style');st.id='sge-login-centro';(document.head||document.documentElement).appendChild(st);}
  st.textContent='html,body{max-width:100vw!important;overflow-x:hidden!important;min-width:0!important;margin:0!important;padding-left:0!important;padding-right:0!important}'
   +'#content_login{float:none!important;left:auto!important;right:auto!important;margin-left:auto!important;margin-right:auto!important;width:min(92vw,420px)!important;max-width:100%!important;min-width:0!important;box-sizing:border-box!important}';
  var el=alvo.parentElement;
  while(el&&el!==document.documentElement){
    var s=el.style;
    s.setProperty('float','none','important');s.setProperty('left','auto','important');s.setProperty('right','auto','important');
    s.setProperty('width','100%','important');s.setProperty('max-width','100%','important');s.setProperty('min-width','0','important');
    s.setProperty('margin-left','0','important');s.setProperty('margin-right','0','important');
    s.setProperty('padding-left','0','important');s.setProperty('padding-right','0','important');
    var irmaos=el.children;
    for(var i=0;i<irmaos.length;i++){var c=irmaos[i];
      if(c.contains(alvo)||/^(SCRIPT|STYLE|LINK|META)${'$'}/.test(c.tagName))continue;
      if(!c.querySelector('input,form,button')&&(c.innerText||'').trim()==='')c.style.setProperty('display','none','important');}
    el=el.parentElement;
  }
  var r=alvo.getBoundingClientRect(),vw=document.documentElement.clientWidth||window.innerWidth;
  if(Math.abs((r.left+r.right)/2-vw/2)>16){
    var fundo=getComputedStyle(document.body).backgroundColor;
    var t=alvo.style;
    t.setProperty('position','fixed','important');t.setProperty('top','50%','important');t.setProperty('left','50%','important');
    t.setProperty('transform','translate(-50%,-50%)','important');t.setProperty('margin','0','important');
    t.setProperty('max-height','96vh','important');t.setProperty('overflow','auto','important');t.setProperty('z-index','2147483000','important');
    if(!fundo||fundo==='rgba(0, 0, 0, 0)'||fundo==='transparent')t.setProperty('background-color','#fff','important');
  }
  window.scrollTo(0,0);
  if(tent++<4)setTimeout(ajustar,400);
}
ajustar();})()"""
}

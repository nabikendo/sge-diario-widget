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
        if (login) view.evaluateJavascript(SCRIPT_CENTRALIZAR_LOGIN, null)
    }

    /** Mantém o formulário no fluxo da página (rolagem com teclado aberto) e remove folgas verticais exageradas do desenho de desktop. */
    internal const val SCRIPT_CENTRALIZAR_LOGIN = """(function(){
var tentativas=0;
function ajustar(){
  var painel=document.getElementById('content_login');
  var formulario=document.getElementById('login-form')||(painel&&painel.querySelector('form'));
  if(!formulario){if(tentativas++<15)setTimeout(ajustar,200);return;}
  var cartao=painel&&painel.contains(formulario)?painel:formulario;
  var estilo=document.getElementById('sge-login-centro');
  if(!estilo){
    estilo=document.createElement('style');
    estilo.id='sge-login-centro';
    estilo.textContent=
      'html,body{margin:0!important;padding:0!important;min-width:0!important;max-width:100%!important}'
      +'.sge-login-fluxo,.sge-login-cartao{display:block!important;position:static!important;'
      +'float:none!important;transform:none!important;translate:none!important;box-sizing:border-box!important;'
      +'min-width:0!important;max-width:100%!important}'
      +'.sge-login-fluxo{width:100%!important;margin-inline:0!important;padding-inline:0!important}'
      +'.sge-login-cartao{width:min(92vw,420px)!important;margin-inline:auto!important}';
    (document.head||document.documentElement).appendChild(estilo);
  }
  cartao.classList.add('sge-login-cartao');
  for(var el=formulario;el&&el!==document.documentElement;el=el.parentElement){
    if(el!==cartao)el.classList.add('sge-login-fluxo');
  }
  enxugar(formulario);
}
/* O SGE reserva ~300 px de margem ou preenchimento vertical (desenho de desktop) acima e abaixo do cartão.
   Reduz espaços verticais exagerados do formulário, dos contêineres acima dele e dos irmãos desses contêineres. */
function enxugar(formulario){
  var ignorar='|SCRIPT|STYLE|LINK|META|TITLE|BR|';
  function reduzir(e){
    var cs=getComputedStyle(e);
    ['margin-top','margin-bottom','padding-top','padding-bottom'].forEach(function(p){
      if(parseFloat(cs.getPropertyValue(p))>48)e.style.setProperty(p,'16px','important');
    });
  }
  for(var e=formulario;e&&e!==document.documentElement;e=e.parentElement){
    reduzir(e);
    for(var i=0;i<e.children.length;i++){if(ignorar.indexOf('|'+e.children[i].tagName+'|')<0)reduzir(e.children[i]);}
  }
}
ajustar();})()"""
}

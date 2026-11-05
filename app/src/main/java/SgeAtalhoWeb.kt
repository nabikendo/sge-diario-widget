package br.to.seduc.sgediariowidget

import android.net.Uri
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import java.text.Normalizer

/** O que o usuário quer fazer no SGE. Módulo: 0 conteúdo, 1 frequência (chamada), 2 plano de aula, 3 plano de curso, 4 PEI. */
data class AtalhoSge(val turmaId: String, val turma: String, val componente: String, val modulo: Int)

/**
 * Abre o SGE Web já no que a pessoa quer fazer: planos e PEI vão direto à listagem da turma com tipo e componente
 * escolhidos; conteúdo e chamada procuram o item no menu do SGE e já selecionam a turma e o componente.
 * Não envia nem salva nada no SGE: só navega e escolhe filtros.
 */
object SgeAtalhoWeb {
    private enum class Etapa { NENHUMA, MENU, BUSCANDO_MENU, SELECAO }
    private var atalho: AtalhoSge? = null
    private var etapa = Etapa.NENHUMA
    private var alvoPath = ""
    private var tentativas = 0
    private var expira = 0L

    fun normalizar(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    /** Código do tipo de registro na tela de planejamentos do SGE (os mesmos usados nas consultas do app). */
    fun tipoRegistro(modulo: Int): String? = when (modulo) { 2 -> "2"; 3 -> "3"; 4 -> "4"; else -> null }

    fun rotulo(modulo: Int) = when (modulo) { 0 -> "Conteúdo"; 1 -> "Frequência"; 2 -> "Plano de aula"; 3 -> "Plano de curso"; else -> "PEI" }

    /** Textos de menu a procurar (já sem acento, do mais específico ao mais geral). */
    fun padroesMenu(modulo: Int): List<String> = when (modulo) {
        0 -> listOf("registro de conteudo", "lancamento de conteudo", "conteudo aplicado", "conteudo")
        1 -> listOf("registro de frequencia", "lancamento de frequencia", "chamada", "frequencia", "notas e faltas")
        else -> emptyList()
    }

    fun urlPlanos(base: String, turmaId: String) = "$base/planejamentoturma?turmaId=${Uri.encode(turmaId)}"

    fun abrir(web: WebView, base: String, a: AtalhoSge) {
        atalho = a; tentativas = 0; expira = System.currentTimeMillis() + 60_000L
        if (tipoRegistro(a.modulo) != null) {
            etapa = Etapa.SELECAO; alvoPath = "/planejamentoturma"; web.loadUrl(urlPlanos(base, a.turmaId))
        } else {
            etapa = Etapa.MENU; alvoPath = ""; web.loadUrl("$base/")
        }
    }

    /** Chamado a cada página carregada no WebView. */
    fun aoPaginaPronta(web: WebView?, url: String?) {
        val a = atalho ?: return
        if (web == null || url == null || System.currentTimeMillis() > expira || url.contains("/Acesso/Entrar", true)) { atalho = null; etapa = Etapa.NENHUMA; return }
        val path = Uri.parse(url).path.orEmpty().lowercase()
        when (etapa) {
            Etapa.MENU -> {
                etapa = Etapa.BUSCANDO_MENU
                web.evaluateJavascript(jsMenu(a)) { raw ->
                    val href = (runCatching { JSONTokener(raw).nextValue() }.getOrNull() as? String)?.takeIf { it.isNotBlank() }
                    if (href == null) {
                        web.evaluateJavascript(jsAviso("Não achei “${rotulo(a.modulo)}” no menu do SGE. Abra pelo menu e escolha a turma ${a.turma}."), null)
                        atalho = null; etapa = Etapa.NENHUMA
                    } else {
                        alvoPath = Uri.parse(href).path.orEmpty().lowercase(); etapa = Etapa.SELECAO; web.loadUrl(href)
                    }
                }
            }
            Etapa.SELECAO -> {
                if (alvoPath.isNotEmpty() && path.startsWith(alvoPath)) {
                    atalho = null; etapa = Etapa.NENHUMA
                    web.evaluateJavascript(if (tipoRegistro(a.modulo) != null) jsPlanos(a) else jsSelecao(a), null)
                } else if (++tentativas > 4) { atalho = null; etapa = Etapa.NENHUMA }
            }
            else -> Unit
        }
    }

    private fun q(s: String) = JSONObject.quote(s)

    private const val AJUDA_JS = """
        function norm(s){return (s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/\s+/g,' ').trim();}
        function aviso(t){var d=document.getElementById('__sgeAviso');if(!d){d=document.createElement('div');d.id='__sgeAviso';
          d.style.cssText='position:fixed;left:50%;transform:translateX(-50%);bottom:24px;max-width:80%;z-index:99999;background:#1f2937;color:#fff;padding:10px 16px;border-radius:12px;font:14px sans-serif;box-shadow:0 4px 16px rgba(0,0,0,.3)';
          document.body.appendChild(d);}d.textContent=t;clearTimeout(window.__sgeAvisoT);window.__sgeAvisoT=setTimeout(function(){if(d.parentNode)d.parentNode.removeChild(d);},6000);}
        function disparar(s){s.dispatchEvent(new Event('change',{bubbles:true}));}
        function escolher(s,teste){for(var i=0;i<s.options.length;i++){if(teste(s.options[i])){if(s.selectedIndex!==i){s.selectedIndex=i;disparar(s);}return true;}}return false;}
    """

    fun jsAviso(texto: String) = "(function(){$AJUDA_JS aviso(${q(texto)});})()"

    /** Devolve o endereço do primeiro item de menu que combina com o destino (ou null). */
    fun jsMenu(a: AtalhoSge) = """(function(){$AJUDA_JS
        var padroes=${padroesMenu(a.modulo).joinToString(prefix = "[", postfix = "]") { q(it) }};
        var links=[].slice.call(document.querySelectorAll('#left-panel a[href], nav a[href], .sidebar a[href]')).filter(function(a){
          var h=a.getAttribute('href')||'';return h&&h!=='#'&&h.indexOf('javascript')!==0;});
        for(var i=0;i<padroes.length;i++){for(var j=0;j<links.length;j++){if(norm(links[j].textContent).indexOf(padroes[i])>=0)return links[j].href;}}
        return null;})()"""

    /** Conteúdo e chamada: escolhe a turma e o componente nos filtros da tela (sem pesquisar nem salvar). */
    fun jsSelecao(a: AtalhoSge) = """(function(){$AJUDA_JS
        var turmaId=${q(a.turmaId)}, turma=norm(${q(a.turma)}), comp=norm(${q(a.componente)});
        var sels=[].slice.call(document.querySelectorAll('select')).filter(function(s){return s.id!=='TurmaContextoId';});
        var ctx=document.getElementById('TurmaContextoId'); if(ctx)sels.push(ctx);
        var ok=sels.some(function(s){return escolher(s,function(o){return o.value===turmaId;});});
        if(!ok)ok=sels.some(function(s){return escolher(s,function(o){var t=norm(o.text);return t&&t===turma;});});
        var n=0,iv=setInterval(function(){n++;var g=document.querySelector('#GradeEscolarId');var feito=false;
          if(g&&g.options.length>1)feito=escolher(g,function(o){return comp&&norm(o.text).indexOf(comp)>=0;});
          if(feito||n>40){clearInterval(iv);
            aviso(ok?(feito?'Turma e componente escolhidos. Confira e toque em pesquisar.':'Turma escolhida. Escolha o componente '+${q(a.componente)}+'.'):'Escolha a turma '+${q(a.turma)}+' nesta tela.');}},300);
        })()"""

    /** Planos e PEI: tipo de registro e componente já escolhidos, listagem pesquisada. */
    fun jsPlanos(a: AtalhoSge) = """(function(){$AJUDA_JS
        var tipo=${q(tipoRegistro(a.modulo).orEmpty())}, comp=norm(${q(a.componente)});
        var t=document.getElementById('TipoDeRegistroPedagogicoId'); if(t&&t.value!==tipo){escolher(t,function(o){return o.value===tipo;});}
        var n=0,iv=setInterval(function(){n++;var g=document.getElementById('GradeEscolarId');var feito=false;
          if(g&&g.options.length>1)feito=escolher(g,function(o){return comp&&norm(o.text).indexOf(comp)>=0;});
          else if(g&&g.options.length===1&&g.options[0].value){feito=true;}
          if(feito||n>40){clearInterval(iv);
            if(feito){setTimeout(function(){var b=document.getElementById('btn_search');if(b)b.click();},400);aviso(${q(rotulo(a.modulo))}+' de '+${q(a.turma)}+': pesquisando…');}
            else aviso('Escolha o componente '+${q(a.componente)}+' e toque em pesquisar.');}},300);
        })()"""
}

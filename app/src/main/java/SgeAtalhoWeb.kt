package br.to.seduc.sgediariowidget

import android.net.Uri
import android.webkit.WebView
import org.json.JSONObject
import java.text.Normalizer

/** O que o usuário quer fazer no SGE. Módulo: 0 conteúdo, 1 frequência (chamada), 2 plano de aula, 3 plano de curso, 4 PEI. */
data class AtalhoSge(val turmaId: String, val turma: String, val componente: String, val modulo: Int)

/**
 * Leva o SGE Web ao que a pessoa quer fazer. O SGE é uma página única (o endereço continua "/"): as telas abrem por
 * clique e a turma vem do seletor de contexto do cabeçalho (#TurmaContextoId). Por isso o atalho faz o mesmo que a pessoa
 * faria: escolhe a turma no contexto, clica na ação/menu da tela, escolhe o componente (e o tipo, nos planos) e pesquisa.
 * Nunca clica em botões que salvam ou geram dados em lote.
 */
object SgeAtalhoWeb {
    private var aguardandoCarga: AtalhoSge? = null
    private var expira = 0L

    fun normalizar(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    /** Código do tipo de registro na tela de planejamentos do SGE (os mesmos usados nas consultas do app). */
    fun tipoRegistro(modulo: Int): String? = when (modulo) { 2 -> "2"; 3 -> "3"; 4 -> "4"; else -> null }

    fun rotulo(modulo: Int) = when (modulo) { 0 -> "Conteúdo"; 1 -> "Frequência"; 2 -> "Plano de aula"; 3 -> "Plano de curso"; else -> "PEI" }

    /** Elemento que só existe quando a tela certa está aberta (ids vistos nas telas reais do SGE). */
    fun marcador(modulo: Int) = when (modulo) { 0 -> "#formConteudoAplicado"; 1 -> "#btnGerar"; else -> "#formPlanejamentoLista" }

    /** Filtro de componente da tela (na tela de conteúdo há dois #GradeEscolarId: vale o que tem mais opções). */
    fun filtroComponente(modulo: Int) = if (modulo == 1) "#DisciplinaId" else "#GradeEscolarId"

    /** Botão que apenas pesquisa/gera a listagem; conteúdo atualiza ao trocar o filtro. */
    fun botaoPesquisar(modulo: Int): String? = when (modulo) { 1 -> "#btnGerar"; 2, 3, 4 -> "#btn_search"; else -> null }

    /** Textos (sem acento) das ações/menus a clicar, do mais específico ao mais geral. */
    fun padroesAcao(modulo: Int): List<String> = when (modulo) {
        0 -> listOf("registrar aula", "registro de conteudo", "lancamento de conteudo", "conteudo aplicado", "conteudo")
        1 -> listOf("fazer chamada", "registro de frequencia", "lancamento de frequencia", "chamada", "frequencia")
        2 -> listOf("plano de aula", "planejamento de aula", "planejamento")
        3 -> listOf("plano de curso", "planejamento de curso", "planejamento")
        else -> listOf("plano de ensino individualizado", "pei", "planejamento")
    }

    val evitar = listOf("verificacao", "relatorio", "historico", "boletim", "resumo")

    /** Abre agora se o SGE já está carregado; senão carrega a página inicial e executa quando ela terminar. */
    fun abrir(web: WebView, base: String, a: AtalhoSge) {
        val url = web.url
        if (url != null && url.startsWith(base) && !url.contains("/Acesso/Entrar", true)) { aguardandoCarga = null; executar(web, a) }
        else { aguardandoCarga = a; expira = System.currentTimeMillis() + 60_000L; web.loadUrl("$base/") }
    }

    /** Chamado a cada página carregada no WebView. */
    fun aoPaginaPronta(web: WebView?, url: String?) {
        val a = aguardandoCarga ?: return
        if (web == null || url == null || System.currentTimeMillis() > expira || url.contains("/Acesso/Entrar", true)) { aguardandoCarga = null; return }
        aguardandoCarga = null
        executar(web, a)
    }

    private fun executar(web: WebView, a: AtalhoSge) = web.evaluateJavascript(jsAtalho(a), null)

    private fun q(s: String) = JSONObject.quote(s)
    private fun lista(l: List<String>) = l.joinToString(prefix = "[", postfix = "]") { q(it) }

    fun jsAtalho(a: AtalhoSge): String = """(async function(){
        function norm(s){return (s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/\s+/g,' ').trim();}
        function aviso(t){var d=document.getElementById('__sgeAviso');if(!d){d=document.createElement('div');d.id='__sgeAviso';
          d.style.cssText='position:fixed;left:50%;transform:translateX(-50%);bottom:24px;max-width:80%;z-index:99999;background:#1f2937;color:#fff;padding:10px 16px;border-radius:12px;font:14px sans-serif;box-shadow:0 4px 16px rgba(0,0,0,.3)';
          document.body.appendChild(d);}d.textContent=t;clearTimeout(window.__sgeAvisoT);window.__sgeAvisoT=setTimeout(function(){if(d.parentNode)d.parentNode.removeChild(d);},7000);}
        var dorme=function(ms){return new Promise(function(r){setTimeout(r,ms);});};
        async function esperar(teste,ms){var fim=Date.now()+ms;while(Date.now()<fim){try{var v=teste();if(v)return v;}catch(e){}await dorme(250);}return null;}
        function escolher(s,valor){if(s.value===valor)return;s.value=valor;
          if(window.jQuery){window.jQuery(s).trigger('change');}else{s.dispatchEvent(new Event('change',{bubbles:true}));}}
        var cfg={turmaId:${q(a.turmaId)},turma:${q(a.turma)},comp:norm(${q(a.componente)}),rotulo:${q(rotulo(a.modulo))},modulo:${a.modulo},
          marcador:${q(marcador(a.modulo))},selComp:${q(filtroComponente(a.modulo))},pesquisar:${q(botaoPesquisar(a.modulo).orEmpty())},
          tipo:${q(tipoRegistro(a.modulo).orEmpty())},acoes:${lista(padroesAcao(a.modulo))},evitar:${lista(evitar)}};
        aviso('Abrindo '+cfg.rotulo+' de '+cfg.turma+'…');
        // 1) turma no contexto do SGE (o seletor do cabeçalho)
        var ctx=await esperar(function(){return document.getElementById('TurmaContextoId');},10000);
        if(ctx&&ctx.value!==cfg.turmaId){
          var ops=[].slice.call(ctx.options),alvoT=norm(cfg.turma);
          var op=ops.find(function(o){return o.value===cfg.turmaId;})||ops.find(function(o){var d=norm(o.getAttribute('data-turmadescricao')||o.text);return d&&(d===alvoT||d.indexOf(alvoT)>=0||alvoT.indexOf(d)>=0);});
          if(op){escolher(ctx,op.value);await dorme(2000);}
        }
        // 2) tela certa (clica na ação do contexto da turma ou no menu)
        if(!document.querySelector(cfg.marcador)){
          function achar(){var els=[].slice.call(document.querySelectorAll('#div-contexto-acoes-turma a,#div-contexto-acoes-turma button,#div-contexto-acoes-turma [onclick],#div-contexto-turma-visao-professor a,#div-contexto-turma-visao-professor button,#content a.btn,#content button,#left-panel a'));
            for(var i=0;i<cfg.acoes.length;i++){for(var j=0;j<els.length;j++){var e=els[j];
              var r=norm([e.textContent,e.getAttribute('title'),e.getAttribute('data-original-title'),e.getAttribute('aria-label')].join(' '));
              if(r.indexOf(cfg.acoes[i])>=0&&!cfg.evitar.some(function(x){return r.indexOf(x)>=0;}))return e;}}return null;}
          var el=await esperar(achar,6000);
          if(!el){aviso('Não achei “'+cfg.rotulo+'” no SGE. Abra a tela e escolha a turma '+cfg.turma+'.');return;}
          el.click();
          if(!(await esperar(function(){return document.querySelector(cfg.marcador);},15000))){aviso('A tela de '+cfg.rotulo+' não abriu. Abra manualmente e escolha a turma '+cfg.turma+'.');return;}
          await dorme(800);
        }
        // 3) filtros: tipo de registro (planos/PEI) e componente
        if(cfg.tipo){var t=document.getElementById('TipoDeRegistroPedagogicoId');if(t){escolher(t,cfg.tipo);await dorme(600);}}
        var feito=await esperar(function(){
          var s=[].slice.call(document.querySelectorAll(cfg.selComp)).sort(function(x,y){return y.options.length-x.options.length;})[0];
          if(!s)return false;var com=[].slice.call(s.options).filter(function(o){return o.value;});if(!com.length)return false;
          var alvo=com.find(function(o){return cfg.comp&&norm(o.text).indexOf(cfg.comp)>=0;})||(com.length===1?com[0]:null);
          if(!alvo)return false;escolher(s,alvo.value);return true;},12000);
        // 4) pesquisa (só o botão que lista; nunca o que salva ou gera em lote)
        if(feito&&cfg.pesquisar){await dorme(500);var b=document.querySelector(cfg.pesquisar);if(b)b.click();}
        aviso(feito?cfg.rotulo+' de '+cfg.turma+': pronto.':'Turma '+cfg.turma+' escolhida. Escolha o componente '+${q(a.componente)}+'.');
        })()"""
}

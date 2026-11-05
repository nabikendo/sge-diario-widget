package br.to.seduc.sgediariowidget

import android.webkit.WebView
import org.json.JSONObject
import java.text.Normalizer

data class AtalhoSge(val turmaId: String, val turma: String, val componente: String, val modulo: Int,
    /** Datas pendentes no formato dd/MM, extraídas do detalhe do módulo (vazio = abre sem data). */
    val datasPendentes: List<String> = emptyList(),
    /** Horário preferencial para a chamada, ex.: "07:00" (vazio = mais antigo disponível). */
    val horarioPref: String = "")

/**
 * Leva o SGE Web exatamente ao que a pessoa quer fazer, reproduzindo o mesmo caminho da mão:
 *  1) lê as ações do contexto da turma (#div-contexto-acoes-turma),
 *  2) chama CarregarPaginaAjaxComLoading com o trecho do onclick correspondente,
 *  3) escolhe o componente/horário e, nos planos/PEI, pesquisa.
 * Nunca toca em botões que salvam ou geram dados.
 */
object SgeAtalhoWeb {
    private var aguardandoCarga: AtalhoSge? = null
    private var expira = 0L

    fun datasPendentes(detalhe: String): List<String> = Regex("""\b\d{2}/\d{2}\b""")
        .findAll(detalhe.substringAfter(':', "")).map { it.value }.distinct().toList()
    fun horarioPendente(detalhe: String): String = Regex("""\b\d{2}:\d{2}\b""")
        .find(detalhe)?.value.orEmpty()

    fun normalizar(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    fun tipoRegistro(modulo: Int): String? = when (modulo) { 2 -> "2"; 3 -> "3"; 4 -> "4"; else -> null }
    fun rotulo(modulo: Int) = when (modulo) { 0 -> "Conteúdo"; 1 -> "Chamada"; 2 -> "Plano de aula"; 3 -> "Plano de curso"; else -> "PEI" }

    /**
     * Padrões (sem acento) para reconhecer a ação certa no onclick do contexto da turma.
     * A chamada preferencial é Frequência Diária (/frequenciadiaria); o Diário de Classe é alternativa.
     */
    fun padroesOnclick(modulo: Int): List<String> = when (modulo) {
        0 -> listOf("/conteudo")
        1 -> listOf("/frequenciadiaria", "/notasfaltas/frequencia")
        2 -> listOf("/planejamentoturma")
        3 -> listOf("/planejamentoturma")
        else -> listOf("/planejamentoturma")
    }

    /** Elemento que confirma que a tela certa está visível. */
    fun marcador(modulo: Int) = when (modulo) {
        0 -> "#formConteudoAplicado"
        1 -> "#tableListaAlunos,#gridFrequencia,#divTabelaFrequencia"
        else -> "#formPlanejamentoLista"
    }

    /** Seletor do componente em cada tela (Frequência Diária usa #GradeEscolarIdPreenchimento). */
    fun filtroComponente(modulo: Int) = when (modulo) {
        0 -> "#GradeEscolarId"
        1 -> "#GradeEscolarIdPreenchimento,#DisciplinaId,#GradeEscolarId"
        else -> "#GradeEscolarId"
    }

    fun botaoPesquisar(modulo: Int): String? = when (modulo) { 1 -> "#btnGerar"; 2, 3, 4 -> "#btn_search"; else -> null }

    fun abrir(web: WebView, base: String, a: AtalhoSge) {
        val url = web.url
        if (url != null && url.startsWith(base) && !url.contains("/Acesso/Entrar", true)) {
            aguardandoCarga = null; executar(web, a)
        } else {
            aguardandoCarga = a; expira = System.currentTimeMillis() + 60_000L; web.loadUrl("$base/")
        }
    }

    fun aoPaginaPronta(web: WebView?, url: String?) {
        val a = aguardandoCarga ?: return
        if (web == null || url == null || System.currentTimeMillis() > expira || url.contains("/Acesso/Entrar", true)) { aguardandoCarga = null; return }
        aguardandoCarga = null; executar(web, a)
    }

    private fun executar(web: WebView, a: AtalhoSge) = web.evaluateJavascript(jsAtalho(a), null)

    private fun q(s: String) = JSONObject.quote(s)
    private fun lista(l: List<String>) = l.joinToString(prefix = "[", postfix = "]") { q(it) }

    fun jsAtalho(a: AtalhoSge): String = """(async function(){
        function norm(s){return(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/\s+/g,' ').trim();}
        function aviso(t){var d=document.getElementById('__sgeAviso');if(!d){d=document.createElement('div');d.id='__sgeAviso';
          d.style.cssText='position:fixed;left:50%;transform:translateX(-50%);bottom:24px;max-width:85%;z-index:99999;background:#1f2937;color:#fff;padding:10px 16px;border-radius:12px;font:14px sans-serif;box-shadow:0 4px 16px rgba(0,0,0,.3);text-align:center';
          document.body.appendChild(d);}d.textContent=t;clearTimeout(window.__sgeAvisoT);window.__sgeAvisoT=setTimeout(function(){if(d.parentNode)d.parentNode.removeChild(d);},8000);}
        var dorme=function(ms){return new Promise(function(r){setTimeout(r,ms);});};
        async function esperar(teste,ms){var fim=Date.now()+ms;while(Date.now()<fim){try{var v=teste();if(v)return v;}catch(e){}await dorme(250);}return null;}
        function dispararJquery(el,ev){if(window.jQuery)window.jQuery(el).trigger(ev);else el.dispatchEvent(new Event(ev,{bubbles:true}));}
        function escolherValor(s,valor){if(s.value===valor)return true;s.value=valor;dispararJquery(s,'change');return s.value===valor;}
        function escolherTexto(s,comp){var ops=[].slice.call(s.options).filter(function(o){return o.value&&comp&&norm(o.text)===comp;});
          if(ops.length!==1)return false;return escolherValor(s,ops[0].value);}
        var cfg={turmaId:${q(a.turmaId)},turma:${q(a.turma)},comp:norm(${q(a.componente)}),rotulo:${q(rotulo(a.modulo))},
          modulo:${a.modulo},padroes:${lista(padroesOnclick(a.modulo))},marcador:${q(marcador(a.modulo))},
          selComp:${q(filtroComponente(a.modulo))},pesquisar:${q(botaoPesquisar(a.modulo).orEmpty())},
          tipo:${q(tipoRegistro(a.modulo).orEmpty())},
          datas:${lista(a.datasPendentes)},horarioPref:${q(a.horarioPref)}};
        aviso('Abrindo '+cfg.rotulo+' de '+cfg.turma+'…');
        // 1) turma no contexto do SGE
        var ctx=await esperar(function(){return document.getElementById('TurmaContextoId');},10000);
        if(!ctx){aviso('Seletor de turma indisponível. Abra o SGE e tente novamente.');return;}
        function nomeTurma(o){var s=norm(o.getAttribute('data-turmadescricao')||o.text);var alvo=norm(cfg.turma);
          return s===alvo||s.indexOf(alvo+'/')===0||s.indexOf(alvo+' -')===0;}
        var selecionada=ctx.options[ctx.selectedIndex];
        var idConfere=selecionada&&(selecionada.value===cfg.turmaId||selecionada.value.split('_')[0]===cfg.turmaId);
        var trocou=false;
        if(!idConfere||!nomeTurma(selecionada)){
          var ops=[].slice.call(ctx.options).filter(function(o){return nomeTurma(o)&&(o.value===cfg.turmaId||o.value.split('_')[0]===cfg.turmaId);});
          if(ops.length===0)ops=[].slice.call(ctx.options).filter(nomeTurma);
          if(ops.length!==1){aviso('Turma não identificada de forma única no SGE. Selecione-a manualmente.');return;}
          if([].slice.call(ctx.options).filter(function(o){return o.value===ops[0].value;}).length!==1){aviso('O SGE repetiu o identificador da turma. Selecione-a manualmente.');return;}
          if(!escolherValor(ctx,ops[0].value)){aviso('Não foi possível selecionar a turma.');return;}
          trocou=true;await dorme(1800);
        }
        selecionada=ctx.options[ctx.selectedIndex];
        if(!selecionada||!nomeTurma(selecionada)){aviso('A turma selecionada não corresponde ao painel.');return;}
        // 2) ação do contexto: usa o onclick CarregarPaginaAjaxComLoading extraído ao vivo
        if(trocou||!cfg.marcador.split(',').some(function(s){return document.querySelector(s);})){
          var acoes=[].slice.call(document.querySelectorAll('#div-contexto-acoes-turma a,#div-contexto-turma-visao-professor a'));
          var el=null;
          for(var i=0;i<cfg.padroes.length&&!el;i++){
            el=acoes.find(function(a){return norm(a.getAttribute('onclick')||'').indexOf(cfg.padroes[i])>=0;});
          }
          if(!el){aviso('Não achei a ação "'+cfg.rotulo+'" no contexto desta turma.');return;}
          // Extrai e executa o CarregarPaginaAjaxComLoading diretamente
          var oc=el.getAttribute('onclick')||'';
          var m=oc.match(/CarregarPaginaAjaxComLoading\(('[^']*'|"[^"]*")/);
          if(m){try{window.CarregarPaginaAjaxComLoading(m[1].slice(1,-1));}catch(e){el.click();}}
          else el.click();
          var marcadores=cfg.marcador.split(',');
          var abriu=await esperar(function(){return marcadores.some(function(s){return document.querySelector(s);});},15000);
          if(!abriu){aviso('A tela de '+cfg.rotulo+' não abriu. Tente abrir pelo menu da turma.');return;}
          await dorme(600);
        }
        // 3) tipo de registro (planos/PEI) e componente
        if(cfg.tipo){
          var t=document.getElementById('TipoDeRegistroPedagogicoId');
          if(t&&t.value!==cfg.tipo){escolherValor(t,cfg.tipo);await dorme(600);}
        }
        var sels=cfg.selComp.split(',').reduce(function(acc,s){return acc.concat([].slice.call(document.querySelectorAll(s.trim())));},[]).filter(function(s){return s.tagName==='SELECT';});
        sels.sort(function(a,b){return b.options.length-a.options.length;});
        var feito=false;
        for(var i=0;i<sels.length&&!feito;i++){
          feito=await esperar(function(s){return function(){return escolherTexto(s,cfg.comp);};}(sels[i]),8000);
        }
        // 4) botão que só pesquisa (nunca salva nem gera dados em lote)
        if(feito&&cfg.pesquisar){await dorme(400);var b=document.querySelector(cfg.pesquisar);if(b&&b.id!=='btn-submit-procedimento-lote'&&!b.id.startsWith('btnGerarFrequencia'))b.click();}
        // 5) Conteúdo: vai direto ao formulário da data pendente mais antiga
        if(feito&&cfg.modulo===0&&cfg.datas.length>0){
          var alvoData=cfg.datas[0];
          // Mapeia "dd/MM" para o título do dia no calendário do SGE e procura o dia vermelho correspondente
          await dorme(400);
          var clicou=await esperar(function(){
            var dias=[].slice.call(document.querySelectorAll('.calendario .semana .vermelho'));
            var d=dias.find(function(el){var t=el.getAttribute('data-original-title')||el.getAttribute('title')||'';var m=t.match(/(\d{2}\/\d{2})\/\d{4}/);return m&&m[1]===alvoData;});
            if(!d)return false;
            d.click();return true;},8000);
          if(clicou)aviso('Dia '+alvoData+' selecionado. Confira o componente e toque em adicionar no SGE.');
          else aviso(cfg.rotulo+' de '+cfg.turma+' pronto. Clique no dia '+alvoData+' (marcado em vermelho) para lançar.');
        }
        // 6) Chamada: apenas um botão de horário explicitamente identificado pode ser acionado.
        else if(feito&&cfg.modulo===1){
          await dorme(600);
          var horarios=[].slice.call(document.querySelectorAll('#_ListaDeHorariosTurma button'));
          var alvoH=cfg.horarioPref;
          var encontrados=horarios.filter(function(b){return alvoH&&(b.textContent||'').indexOf(alvoH)>=0;});
          if(encontrados.length===1){encontrados[0].click();aviso('Horário '+alvoH+' selecionado. Confira a chamada antes de salvar.');}
          else aviso('Chamada de '+cfg.turma+' aberta. Selecione o horário e confira antes de salvar.');
        }
        else if(!feito)aviso('Turma pronta. Escolha o componente '+${q(a.componente)}+'.');
        else if(cfg.modulo>1)aviso(cfg.rotulo+' de '+cfg.turma+' — pronto.');
        })()"""
}

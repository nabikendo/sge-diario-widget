/*
 * sge_automacao.js — SGE Diário
 * Injetado no WebView do SGE 3.0 (Seduc-TO). Coloque em app/src/main/assets/.
 *
 * Regras:
 *  - NUNCA clica em Salvar. Preenche e devolve o controle ao professor.
 *  - Toda resposta volta por window.SgeBridge.resultado(JSON) com o requestId recebido.
 *  - Se não houver ponte (teste no Chrome), a resposta vai para o console.
 */
(function () {
  'use strict';

  var VERSAO = 1;
  if (window.SgeAutomacao && window.SgeAutomacao.versao === VERSAO) return;

  var TEMPO_PADRAO = 20000;
  var CAMPOS = {
    objeto1: 'ItemDoAgrupador_2516',
    habilidades: 'ItemDoAgrupador_2521',
    objeto2: 'ItemDoAgrupador_2525',
    outros: 'ItemDoAgrupador_2527',
    atividades: 'ItemDoAgrupador_2533'
  };

  // ───────────── utilidades ─────────────

  function enviar(msg) {
    var texto = JSON.stringify(msg);
    try {
      if (window.SgeBridge && typeof window.SgeBridge.resultado === 'function') {
        window.SgeBridge.resultado(texto);
        return;
      }
    } catch (e) { /* cai no console */ }
    console.log('[SgeAutomacao]', texto);
  }

  function falha(codigo, mensagem, extra) {
    var e = new Error(mensagem);
    e.codigo = codigo;
    e.extra = extra || null;
    return e;
  }

  function dormir(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }

  async function esperar(condicao, descricao, tempo) {
    var limite = Date.now() + (tempo || TEMPO_PADRAO);
    while (Date.now() < limite) {
      var v = null;
      try { v = condicao(); } catch (e) { v = null; }
      if (v) return v;
      await dormir(200);
    }
    throw falha('tempo_esgotado', 'O SGE demorou demais para ' + descricao + '.');
  }

  /** Espera o jQuery terminar as chamadas AJAX em andamento. */
  function esperarAjax(tempo) {
    return esperar(function () {
      return !window.jQuery || window.jQuery.active === 0;
    }, 'responder', tempo);
  }

  function norm(s) {
    return String(s == null ? '' : s)
      .normalize('NFD').replace(/[̀-ͯ]/g, '')
      .replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function visivel(el) { return !!el && el.getClientRects().length > 0; }

  function porId(id) { return document.getElementById(id); }

  /** Campo DENTRO do formulário de adição (a página tem ids repetidos, ex.: GradeEscolarId). */
  function campo(id) { return document.querySelector('#formAddConteudo #' + id); }

  function opcoesValidas(sel) {
    return Array.prototype.filter.call(sel.options, function (o) { return o.value !== ''; });
  }

  function isoParaBr(iso) {
    var p = String(iso).split('-');
    return p[2] + '/' + p[1] + '/' + p[0];
  }

  function numOuNull(v) { return v == null ? null : Number(v); }

  function destacar(idSelect, cor) {
    var c = porId('s2id_' + idSelect);
    if (c) c.style.outline = cor ? '2px solid ' + cor : '';
  }

  // ───────────── campos ─────────────

  function definirTexto(id, valor) {
    var el = campo(id);
    if (!el) throw falha('campo_ausente', 'Campo ' + id + ' não encontrado na janela.');
    el.value = valor;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  /** 1º pelo value conhecido; 2º texto exato; 3º texto sem acento/maiúscula (se único). */
  function acharOpcao(sel, item) {
    var ops = opcoesValidas(sel);
    if (item.sgeValue != null && item.sgeValue !== '') {
      var v = String(item.sgeValue);
      var porValor = ops.find(function (o) { return o.value === v; });
      if (porValor) return { opcao: porValor, via: 'value' };
    }
    var bruto = String(item.texto || '').trim();
    var exata = ops.find(function (o) { return o.text.trim() === bruto; });
    if (exata) return { opcao: exata, via: 'texto' };

    var alvo = norm(bruto);
    var iguais = ops.filter(function (o) { return norm(o.text) === alvo; });
    if (iguais.length === 1) return { opcao: iguais[0], via: 'texto_normalizado' };
    return null;
  }

  /** Até 5 opções parecidas, para o app mostrar ao professor. */
  function sugerir(sel, texto) {
    var palavras = norm(texto).split(' ').filter(function (p) { return p.length > 3; });
    return opcoesValidas(sel)
      .map(function (o) {
        var t = norm(o.text);
        var pontos = palavras.filter(function (p) { return t.indexOf(p) !== -1; }).length;
        return { texto: o.text.trim(), pontos: pontos };
      })
      .filter(function (x) { return x.pontos > 0; })
      .sort(function (a, b) { return b.pontos - a.pontos; })
      .slice(0, 5)
      .map(function (x) { return x.texto; });
  }

  /** Select2 só atualiza a tela via jQuery .val().trigger('change'). Suporta select múltiplo. */
  function aplicarSelect(sel, valor) {
    var jq = window.jQuery;
    var final = valor;
    if (sel.multiple) {
      var atuais = jq
        ? (jq(sel).val() || [])
        : opcoesValidas(sel).filter(function (o) { return o.selected; }).map(function (o) { return o.value; });
      if (atuais.indexOf(valor) === -1) atuais = atuais.concat([valor]);
      final = atuais;
    }
    if (jq) {
      jq(sel).val(final).trigger('change');
      return;
    }
    var lista = [].concat(final);
    Array.prototype.forEach.call(sel.options, function (o) { o.selected = lista.indexOf(o.value) !== -1; });
    sel.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function preencherSelect(id, item, rotulo, rel) {
    if (!item || !item.texto) return;
    var sel = campo(id);
    if (!sel) throw falha('campo_ausente', 'Campo ' + rotulo + ' não encontrado na janela.');
    var achou = acharOpcao(sel, item);
    if (!achou) {
      rel.naoEncontrados.push(rotulo);
      rel.sugestoes[rotulo] = sugerir(sel, item.texto);
      destacar(id, '#ff3b30');            // vermelho: o professor escolhe à mão
      return;
    }
    aplicarSelect(sel, achou.opcao.value);
    destacar(id, '');
    rel.preenchidos.push(rotulo);
    rel.valores[rotulo] = { value: achou.opcao.value, texto: achou.opcao.text.trim(), via: achou.via };
  }

  // ───────────── mapa de registros ─────────────

  var RE_MAPA = /redirecionarAdicionarViaMapa\(\s*'(\d{4}-\d{2}-\d{2})'\s*,\s*(null|\d+)\s*,\s*(null|\d+)\s*,\s*(null|\d+)/;

  /** Aulas PENDENTES do mapa (só elas têm o botão de adicionar). */
  function aulasDoMapa() {
    var botoes = document.querySelectorAll('[onclick*="redirecionarAdicionarViaMapa"]');
    return Array.prototype.map.call(botoes, function (b) {
      var m = (b.getAttribute('onclick') || '').match(RE_MAPA);
      if (!m) return null;
      var linha = b.closest('tr');
      var celula = linha && linha.querySelector('td');
      return {
        dataIso: m[1],
        horarioId: m[2] === 'null' ? null : m[2],
        horarioDiferencialId: m[3] === 'null' ? null : m[3],
        gradeEscolarId: m[4] === 'null' ? null : m[4],
        componente: celula ? celula.textContent.trim() : null
      };
    }).filter(Boolean);
  }

  function mesmaAula(a, b) {
    return a.dataIso === b.dataIso && a.horarioId === b.horarioId &&
      a.horarioDiferencialId === b.horarioDiferencialId && a.gradeEscolarId === b.gradeEscolarId;
  }

  // ───────────── salvamento (observado, não executado) ─────────────

  function observarSalvamento(requestId, aula) {
    var btn = campo('btnSalvar') || porId('btnSalvar');
    if (!btn || btn.getAttribute('data-sge-observando') === requestId) return;
    btn.setAttribute('data-sge-observando', requestId);

    btn.addEventListener('click', async function aoClicar() {
      btn.removeEventListener('click', aoClicar);
      try {
        await esperar(function () { return !visivel(porId('formAddConteudo')); }, 'salvar', 30000);
        await dormir(500);
        await esperarAjax(15000);
        // Confirma pelo mapa: a aula salva deixa de ter o botão de adicionar.
        var aindaPendente = aulasDoMapa().some(function (a) { return mesmaAula(a, aula); });
        enviar(aindaPendente
          ? { requestId: requestId, status: 'nao_confirmado', mensagem: 'A janela fechou, mas a aula ainda aparece pendente no mapa.' }
          : { requestId: requestId, status: 'salvo' });
      } catch (e) {
        enviar({ requestId: requestId, status: 'nao_salvo', codigo: 'validacao',
                 mensagem: 'A janela não fechou após Salvar. Veja o aviso do SGE.' });
        btn.removeAttribute('data-sge-observando');
        observarSalvamento(requestId, aula);   // continua observando o próximo clique
      }
    });
  }

  // ───────────── API: conteúdo ─────────────

  async function preencherConteudo(p) {
    var rel = { preenchidos: [], naoEncontrados: [], sugestoes: {}, valores: {}, avisos: [] };
    try {
      if (!window.ConteudoEditar || typeof window.ConteudoEditar.redirecionarAdicionarViaMapa !== 'function') {
        throw falha('pagina_errada', 'Abra Conteúdo Aplicado da turma no SGE antes de enviar.');
      }

      var turmaPagina = porId('TurmaId');
      if (p.turmaId && turmaPagina && turmaPagina.value && String(turmaPagina.value) !== String(p.turmaId)) {
        throw falha('turma_errada', 'O SGE está em outra turma. Troque para a turma do rascunho.',
          { turmaNaPagina: turmaPagina.value });
      }

      var dataBr = isoParaBr(p.dataIso);
      var candidatas = aulasDoMapa().filter(function (a) {
        return a.dataIso === p.dataIso && String(a.gradeEscolarId) === String(p.gradeEscolarId);
      });
      if (p.horarioId) {
        candidatas = candidatas.filter(function (a) {
          return a.horarioId === String(p.horarioId) || a.horarioDiferencialId === String(p.horarioId);
        });
      }
      if (!candidatas.length) {
        throw falha('aula_nao_encontrada',
          'Não há aula pendente de ' + p.componente + ' em ' + dataBr +
          '. Confira o mês do mapa ou se ela já foi lançada.',
          { pendentesNoMapa: aulasDoMapa() });
      }
      var aula = candidatas[0];
      if (candidatas.length > 1) {
        rel.avisos.push('Há ' + candidatas.length + ' aulas pendentes nesse dia; foi aberta a primeira.');
      }

      // Abre a janela já na data/horário certos.
      var antigo = porId('formAddConteudo');
      if (antigo) antigo.setAttribute('data-sge-antigo', '1');
      var inicio = Date.now();
      window.ConteudoEditar.redirecionarAdicionarViaMapa(
        aula.dataIso, numOuNull(aula.horarioId), numOuNull(aula.horarioDiferencialId),
        numOuNull(aula.gradeEscolarId), null);

      await esperar(function () {
        var f = porId('formAddConteudo');
        if (!visivel(f)) return null;
        if (!f.hasAttribute('data-sge-antigo')) return f;             // formulário novo
        var ocioso = !window.jQuery || window.jQuery.active === 0;
        return (Date.now() - inicio > 3000 && ocioso) ? f : null;     // mesmo elemento reaproveitado
      }, 'abrir a janela Adicionar Conteúdo');
      await esperarAjax();

      // Segurança: não mexer em registro existente.
      var idRegistro = campo('Id');
      if (idRegistro && idRegistro.value && idRegistro.value !== '0') {
        throw falha('ja_existe', 'Essa aula já tem conteúdo (registro ' + idRegistro.value + '). Nada foi alterado.');
      }
      var dataNoForm = campo('DataAula');
      if (dataNoForm && dataNoForm.value && dataNoForm.value.trim() !== dataBr) {
        throw falha('data_divergente', 'A janela abriu em ' + dataNoForm.value + ', e não em ' + dataBr + '.');
      }

      // Componente: garante o GradeEscolarId do rascunho.
      var selGrade = campo('GradeEscolarId');
      if (selGrade && String(selGrade.value) !== String(p.gradeEscolarId)) {
        var opG = opcoesValidas(selGrade).find(function (o) { return o.value === String(p.gradeEscolarId); });
        if (!opG) {
          throw falha('componente_invalido',
            p.componente + ' não está disponível nessa aula.',
            { disponiveis: opcoesValidas(selGrade).map(function (o) { return { value: o.value, texto: o.text.trim() }; }) });
        }
        aplicarSelect(selGrade, opG.value);
        await dormir(300);
        await esperarAjax();
      }

      // Objetos de conhecimento podem recarregar após o componente.
      await esperar(function () {
        var s = campo(CAMPOS.objeto1);
        return s && opcoesValidas(s).length > 0;
      }, 'carregar os objetos de conhecimento');

      preencherSelect(CAMPOS.objeto1, p.objetoUm, 'Objeto de conhecimento 1', rel);
      preencherSelect(CAMPOS.objeto2, p.objetoDois, 'Objeto de conhecimento 2', rel);

      if (p.habilidadeTexto) {
        definirTexto(CAMPOS.habilidades, p.habilidadeTexto);
        rel.preenchidos.push('Habilidades');
      }
      if (p.outros) {
        definirTexto(CAMPOS.outros, p.outros);
        rel.preenchidos.push('Outros');
      }
      definirTexto(CAMPOS.atividades, p.atividadesObs || '');
      rel.preenchidos.push('Atividades/Observações');

      observarSalvamento(p.requestId, aula);

      enviar({
        requestId: p.requestId,
        status: rel.naoEncontrados.length ? 'parcial' : 'ok',
        mensagem: rel.naoEncontrados.length
          ? 'Preenchido. Escolha no SGE: ' + rel.naoEncontrados.join(', ') + '. Depois toque em Salvar.'
          : 'Preenchido. Confira e toque em Salvar.',
        preenchidos: rel.preenchidos,
        naoEncontrados: rel.naoEncontrados,
        sugestoes: rel.sugestoes,
        avisos: rel.avisos,
        aula: aula,
        dados: { valores: rel.valores }
      });
    } catch (e) {
      enviar({
        requestId: p && p.requestId,
        status: 'erro',
        codigo: e.codigo || 'excecao',
        mensagem: e.message,
        preenchidos: rel.preenchidos,
        dados: e.extra
      });
    }
  }

  // ───────────── API: frequência ─────────────

  /** Desmarca a presença dos alunos indicados. Não salva. */
  function marcarFaltas(p) {
    try {
      if (!porId('tableListaAlunos')) {
        throw falha('pagina_errada', 'Abra Frequência Diária da turma e escolha a data antes.');
      }
      var dataCampo = porId('DataAula');
      if (p.dataIso && dataCampo && dataCampo.value && dataCampo.value.trim() !== isoParaBr(p.dataIso)) {
        throw falha('data_divergente', 'A frequência aberta é de ' + dataCampo.value + ', e não de ' + isoParaBr(p.dataIso) + '.');
      }

      var marcadas = [], jaAusentes = [], naoEncontrados = [];
      (p.alunoIds || []).forEach(function (id) {
        var cb = porId('chkPresente_' + id + '_' + p.horarioId);
        if (!cb) { naoEncontrados.push(String(id)); return; }
        if (!cb.checked) { jaAusentes.push(String(id)); return; }
        cb.click();                                   // dispara os handlers do próprio SGE
        if (cb.checked) {                             // se algo bloqueou o clique
          cb.checked = false;
          cb.dispatchEvent(new Event('change', { bubbles: true }));
        }
        marcadas.push(String(id));
      });

      enviar({
        requestId: p.requestId,
        status: naoEncontrados.length ? 'parcial' : 'ok',
        mensagem: marcadas.length + ' falta(s) marcada(s). Confira e toque em Salvar.',
        naoEncontrados: naoEncontrados,
        dados: { marcadas: marcadas, jaAusentes: jaAusentes, naoEncontrados: naoEncontrados }
      });
    } catch (e) {
      enviar({ requestId: p && p.requestId, status: 'erro', codigo: e.codigo || 'excecao', mensagem: e.message });
    }
  }

  // ───────────── API: diagnóstico ─────────────

  /** Substitui o snippet do Console: devolve as opções dos selects da janela aberta. */
  function exportarOpcoes(requestId) {
    try {
      var form = porId('formAddConteudo');
      if (!visivel(form)) throw falha('janela_fechada', 'Abra a janela Adicionar Conteúdo Aplicado antes.');
      var campos = {};
      [CAMPOS.objeto1, CAMPOS.objeto2, 'GradeEscolarId'].forEach(function (id) {
        var sel = campo(id);
        if (sel) campos[id] = opcoesValidas(sel).map(function (o) { return { value: o.value, texto: o.text.trim() }; });
      });
      var grade = campo('GradeEscolarId');
      enviar({ requestId: requestId, status: 'ok', dados: { gradeEscolarId: grade ? grade.value : null, campos: campos } });
    } catch (e) {
      enviar({ requestId: requestId, status: 'erro', codigo: e.codigo || 'excecao', mensagem: e.message });
    }
  }

  /** Aulas pendentes do mapa, com o GradeEscolarId de cada componente. */
  function listarAulas(requestId) {
    var turma = porId('TurmaId');
    var sel = porId('TurmaContextoId');
    var chosen = document.querySelector('#s2id_TurmaContextoId .select2-chosen');
    var descricao = (sel && sel.selectedIndex >= 0 && sel.options[sel.selectedIndex])
      ? sel.options[sel.selectedIndex].text.trim()
      : (chosen ? chosen.textContent.trim() : null);
    if (!turma || !turma.value || !window.ConteudoEditar) {
      enviar({ requestId: requestId, status: 'erro', codigo: 'pagina_errada',
               mensagem: 'Abra Conteúdo Aplicado de uma turma no SGE (aba Mapa de Registros) e tente de novo.' });
      return;
    }
    enviar({
      requestId: requestId,
      status: 'ok',
      dados: { turmaId: turma.value, turmaDescricao: descricao, aulas: aulasDoMapa() }
    });
  }

  window.SgeAutomacao = {
    versao: VERSAO,
    preencherConteudo: function (p) { preencherConteudo(p); return 'iniciado'; },
    marcarFaltas: function (p) { marcarFaltas(p); return 'iniciado'; },
    exportarOpcoes: function (id) { exportarOpcoes(id); return 'iniciado'; },
    listarAulas: function (id) { listarAulas(id); return 'iniciado'; }
  };
})();

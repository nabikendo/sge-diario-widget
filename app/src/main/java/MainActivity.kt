package br.to.seduc.sgediariowidget

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.updateAll
import androidx.work.WorkManager
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.DiarioGlanceWidget
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker
import kotlinx.coroutines.delay
import org.json.JSONTokener

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
        // Recover from a worker left behind by an older APK. A persisted
        // "syncing" flag must never hide the real login/sync controls forever.
        WorkManager.getInstance(this).cancelUniqueWork("sge_sync_now")
        SgeSyncWorker.agendarPeriodico(this)
        val painelInicial = SgePainelStore.ler(this)
        if (painelInicial.sincronizando) {
            SgePainelStore.salvar(this, painelInicial.copy(
                sincronizando = false,
                inicioConsulta = 0L,
                mensagem = "A consulta anterior foi interrompida. Toque em Sincronizar agora para tentar novamente."
            ))
        }
        // Session cookies stay in Android's cookie store, outside SharedPreferences/backups.
        prefs.edit().remove("sge_cookies").apply()
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            prefs.edit().putString("web_user_agent", settings.userAgentString).apply()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    CookieManager.getInstance().flush()
                    verificarSessao()
                }
            }
        }
        val state = savedInstanceState?.getBundle("web_state")
        if (state == null || web.restoreState(state) == null) web.loadUrl(SgeSyncWorker.BASE + "/")
        setContent {
            var tema by remember { mutableStateOf(prefs.getInt("tema", 0)) }
            var estilo by remember { mutableStateOf(prefs.getInt("estilo", 0)) }
            SgeTema(tema, estilo) {
                var login by remember { mutableStateOf(false) }
                var coord by remember { mutableStateOf(false) }
                var coordPainel by remember { mutableStateOf(SgeCoordStore.ler(this)) }
                var ajustes by remember { mutableStateOf(false) }
                var turmasRaw by remember { mutableStateOf(prefs.getString("turmas_escola", "[]") ?: "[]") }
                val larg = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                var painel by remember { mutableStateOf(SgePainelStore.ler(this)) }
                LaunchedEffect(Unit) {
                    DiarioGlanceWidget().updateAll(this@MainActivity)
                    while (true) {
                        val atualizado = SgePainelStore.ler(this@MainActivity)
                        if (atualizado != painel) {
                            painel = atualizado
                            DiarioGlanceWidget().updateAll(this@MainActivity)
                        }
                        val c = SgeCoordStore.ler(this@MainActivity)
                        if (c != coordPainel) coordPainel = c
                        val tr = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE).getString("turmas_escola", "[]") ?: "[]"
                        if (tr != turmasRaw) turmasRaw = tr
                        if (login) { extrairEscola(); instalarHook() }
                        delay(2500)
                    }
                }
                BackHandler(enabled = login) {
                    if (web.canGoBack()) web.goBack() else login = false
                }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = if (larg >= 600) 28.dp else 12.dp)) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SGE  •  DIÁRIO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (login) "SGE Web" else if (coord) "Coordenação" else "Turmas", style = MaterialTheme.typography.titleLarge)
                        }
                        if (login) TextButton(onClick = { copiarPagina() }) { Text("Copiar página") }
                        androidx.compose.material3.IconButton(onClick = { ajustes = !ajustes }) { Text("⚙", style = MaterialTheme.typography.titleMedium) }
                    }
                    if (ajustes) Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Aparência", style = MaterialTheme.typography.titleMedium)
                            Segmentado(listOf("Sistema", "Claro", "Escuro"), tema) { tema = it; prefs.edit().putInt("tema", it).apply() }
                            Text("Paleta", style = MaterialTheme.typography.titleMedium)
                            Segmentado(listOf("Apple", "Cores do sistema"), estilo) { estilo = it; prefs.edit().putInt("estilo", it).apply() }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // The WebView stays attached when switching tabs, preserving the SGE document.
                    Box(Modifier.weight(1f)) {
                        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                        if (!login && coord) PainelCoordenacao(coordPainel, turmasRaw)
                        if (!login && !coord) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Resumo do diário", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        SelectionContainer { Text(painel.mensagem, style = MaterialTheme.typography.bodyMedium) }
                                    }
                                    Text("◌", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                FilledTonalButton(onClick = {
                                SgePainelStore.salvar(this@MainActivity, painel.copy(
                                    sincronizando = true,
                                    inicioConsulta = System.currentTimeMillis(),
                                    mensagem = "Iniciando consulta manual…"
                                ))
                                painel = SgePainelStore.ler(this@MainActivity)
                                extrairEscola(); extrairTurmas { SgeSyncWorker.sincronizarAgora(this@MainActivity) }
                                }) { Text(if (painel.sincronizando) "Sincronizando…" else "Sincronizar agora  ↻") }
                                TextButton(onClick = {
                                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("diagnostico", montarDiagnostico(painel)))
                                Toast.makeText(this@MainActivity, "Diagnóstico copiado", Toast.LENGTH_SHORT).show()
                                }) { Text("Diagnóstico") }
                            }

                            if (painel.sincronizando) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("SUAS TURMAS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("C/F/A/P/I: conteúdos, frequência, plano de aula, plano de curso e PEI. * cobertura parcial • — não verificado.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Grade(painel.turmas) { resultado ->
                                var detalhesAbertos by remember { mutableStateOf(false) }
                                Card(
                                    onClick = { detalhesAbertos = !detalhesAbertos },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(resultado.turma.nome, style = MaterialTheme.typography.titleMedium)
                                                Text("${resultado.turma.disciplina}  ·  ${resultado.turma.escola}", style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            }
                                            Text(if (detalhesAbertos) "⌃" else "›", style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            resultado.modulos.take(5).forEachIndexed { i, m ->
                                                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                        Text(listOf("C", "F", "A", "P", "I").getOrElse(i) { "•" },
                                                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(m.resumo(), style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }
                                            }
                                        }
                                        if (detalhesAbertos) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                            Text("Consulta: ${resultado.atualizado}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            resultado.modulos.forEachIndexed { i, m ->
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text("${SgePainelStore.NOMES_MODULOS.getOrElse(i) { "Módulo" }}: ${m.resumo()}", style = MaterialTheme.typography.bodyMedium)
                                                    Text(m.detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    NavegacaoInferior(listOf("Turmas", "SGE Web", "Coordenação"), if (login) 1 else if (coord) 2 else 0) { i ->
                        login = i == 1
                        coord = i == 2
                    }
                }
            }
        }
    }
    private fun montarDiagnosticoCoord(c: CoordPainel): String {
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
        val v = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        val n = runCatching { org.json.JSONArray(prefs.getString("turmas_escola", "[]")).length() }.getOrDefault(0)
        return buildString {
            appendLine("SGE Diário v$v (coordenação)")
            appendLine("Mensagem: ${c.mensagem}")
            appendLine("Turmas conhecidas: $n; verificadas: ${c.turmas.size}")
            c.turmas.forEachIndexed { i, t ->
                appendLine("Turma ${i + 1}: ${t.disciplinas.size} disciplina(s)")
                t.disciplinas.forEach { d -> d.modulos.forEachIndexed { j, m ->
                    if (m.quantidade == null || !m.coberturaCompleta) appendLine("  ${d.turma.disciplina} ${SgePainelStore.NOMES_MODULOS.getOrElse(j) { "Módulo" }}: ${m.resumo()} ${m.detalhe}")
                } }
            }
            appendLine(prefs.getString("diagnostico_v1", "(sem diagnóstico guardado)"))
        }
    }

    private fun montarDiagnostico(p: PainelSge): String {
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
        val v = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        return buildString {
            appendLine("SGE Diário v$v")
            appendLine("Mensagem: ${p.mensagem}")
            p.turmas.forEachIndexed { i, r -> r.modulos.forEachIndexed { j, m ->
                appendLine("Turma ${i + 1} ${SgePainelStore.NOMES_MODULOS.getOrElse(j) { "Módulo" }}: ${m.resumo()} ${m.detalhe}")
            } }
            appendLine(prefs.getString("diagnostico_v1", "(sem diagnóstico guardado)"))
        }
    }

    /** O SGE monta #TurmaContextoId no navegador; guardamos a lista lida do WebView logado para o Worker. */
    private fun extrairTurmas(depois: () -> Unit) {
        if (!::web.isInitialized) { depois(); return }
        val js = """(function(){var s=document.querySelector('#TurmaContextoId');if(!s)return null;
            return JSON.stringify(Array.prototype.slice.call(s.options).filter(function(o){return o.value;}).map(function(o){
            return {v:o.value,d:o.getAttribute('data-turmadescricao')||o.text.trim(),e:o.getAttribute('data-escoladescricao')||'',t:o.getAttribute('data-tipodaturmaenum')||''};}));})()"""
        web.evaluateJavascript(js) { raw ->
            runCatching {
                val v = JSONTokener(raw).nextValue()
                if (v is String && v.length > 2) getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
                    .edit().putString("turmas_contexto", v).apply()
            }
            depois()
        }
    }
    /** Copia só a ESTRUTURA da página aberta no WebView (ids, colunas, rotas do menu), sem textos de células. */
    private fun copiarPagina() {
        if (!::web.isInitialized) return
        val js = """(function(){function t(x){return (x||'').replace(/\s+/g,' ').trim().slice(0,40)}
            function A(l){return Array.prototype.slice.call(l)}
            var r={path:location.pathname,title:t(document.title)};
            r.ids=A(document.querySelectorAll('[id]')).map(function(e){return e.id}).filter(function(i){return i.indexOf('eNomenclatura')!==0}).slice(0,80);
            r.selects=A(document.querySelectorAll('select')).slice(0,15).map(function(s){return (s.id||s.name)+'('+s.options.length+')'});
            r.tabelas=A(document.querySelectorAll('table')).slice(0,8).map(function(tb){return {id:tb.id,cab:A(tb.querySelectorAll('thead th')).map(function(h){return t(h.textContent)}),linhas:tb.querySelectorAll('tbody tr').length}});
            r.menu=A(document.querySelectorAll('#left-panel a')).slice(0,100).map(function(a){var h=a.getAttribute('href')||'';return t(a.textContent)+' -> '+h.split('?')[0].slice(0,60)});
            r.rotas=A(new Set(A(document.querySelectorAll('a[href],[data-url],[onclick]')).map(function(e){var s=e.getAttribute('href')||e.getAttribute('data-url')||e.getAttribute('onclick')||'';var m=s.match(/\/[A-Za-z]+(\/[A-Za-z]+)*/);return m?m[0]:null}).filter(Boolean))).slice(0,60);
            r.abas=A(document.querySelectorAll('.nav-tabs a,[role=tab]')).slice(0,12).map(function(a){return t(a.textContent)});
            r.turmasSel=A(document.querySelectorAll('#SelecaoDeTurma option,#TurmaContextoId option')).slice(0,4).map(function(o){return {v:o.value.replace(/[0-9]/g,'9').slice(0,30),attrs:A(o.attributes).map(function(a){return a.name}).join(','),t:t(o.text)}});
            function est(el){var d=document.createElement('div');d.innerHTML=el.getAttribute('data-content')||'';return {cls:(el.className||'').slice(0,60),cab:A(d.querySelectorAll('th')).map(function(h){return t(h.textContent)}),linhas:d.querySelectorAll('tr').length,col1:A(d.querySelectorAll('tr td:first-child')).map(function(c){return t(c.textContent)}).slice(0,6),acoes:A(d.querySelectorAll('a,button')).map(function(b){return t(b.textContent)+'|'+(b.className||'').slice(0,40)}).slice(0,6)}}
            var dv=A(document.querySelectorAll('.calendario .semana [data-content]'));
            var verm=dv.filter(function(e){return /vermelho/.test(e.className)}).slice(0,2);
            var outros=dv.filter(function(e){return !/vermelho/.test(e.className)}).slice(0,2);
            r.diasVermelhos=verm.map(est);r.diasOutros=outros.map(est);
            r.legenda=A(document.querySelectorAll('.calendario .legenda *,.legenda *')).slice(0,12).map(function(e){return (e.className||'').slice(0,30)+':'+t(e.textContent)});
            function linhas(el){var d=document.createElement('div');d.innerHTML=el.getAttribute('data-content')||'';return A(d.querySelectorAll('tr')).map(function(tr){var tds=A(tr.querySelectorAll('td'));return {d:t(tds[0]&&tds[0].textContent),n:tds.length,tc:(tr.className||'').slice(0,30),b:tr.querySelectorAll('a,button').length,i:A(tr.querySelectorAll('i,.glyphicon')).map(function(x){return (x.className||'').slice(0,40)}).slice(0,3),x:tds.slice(1).map(function(c){var s=t(c.textContent);return (s.length<=12?s:'len'+s.length)+':'+(c.className||'').slice(0,20)})}})}
            var vd=dv.filter(function(e){return /verde/.test(e.className)}).slice(0,1);
            r.linhasVermelho=verm.length?linhas(verm[0]):[];r.linhasVerde=vd.length?linhas(vd[0]):[];
            r.xhr=(window.__xhr||[]).slice(-40);
            return JSON.stringify(r)})()"""
        web.evaluateJavascript(js) { raw ->
            val txt = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: "(não foi possível ler a página)"
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("pagina", txt))
            Toast.makeText(this, "Estrutura da página copiada", Toast.LENGTH_SHORT).show()
        }
    }
    /** Lê as turmas de Escola → Conteúdo Aplicado (perfil Unidade escolar) do WebView logado. */
    private fun extrairEscola() {
        if (!::web.isInitialized) return
        val js = """(function(){var s=document.querySelector('#SelecaoDeTurma');if(!s)return null;
            return JSON.stringify(Array.prototype.slice.call(s.options).filter(function(o){return o.value;}).map(function(o){
            return {v:o.value,t:o.text.trim(),tp:o.getAttribute('data-tipodeturma')||'0'};}));})()"""
        web.evaluateJavascript(js) { raw ->
            runCatching {
                val v = JSONTokener(raw).nextValue()
                if (v is String && v.length > 2) getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
                    .edit().putString("turmas_escola", v).apply()
            }
        }
    }
    /** Registra os endereços das chamadas AJAX da página, para mapear telas novas (aparecem em "Copiar página"). */
    private fun instalarHook() {
        if (!::web.isInitialized) return
        web.evaluateJavascript("""(function(){if(window.__hook)return;window.__hook=true;window.__xhr=[];
            var o=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){try{
            window.__xhr.push(m+' '+String(u).split('?')[0].slice(0,80));if(window.__xhr.length>60)window.__xhr.shift();}catch(e){}
            return o.apply(this,arguments);};})()""", null)
    }
    @Composable
    fun PainelCoordenacao(p: CoordPainel, turmasRaw: String) {
        val conhecidas = remember(turmasRaw) {
            runCatching { org.json.JSONArray(turmasRaw).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("v") to o.getString("t") } } } }
                .getOrDefault(emptyList())
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Coordenação • verificação por turma e disciplina", style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(p.mensagem) }
            Text("Perfil Unidade escolar ativo. Abra Escola → Conteúdo Aplicado na aba SGE Web e toque em Carregar turmas.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { extrairEscola(); Toast.makeText(this@MainActivity, "Lendo a lista de turmas…", Toast.LENGTH_SHORT).show() }) { Text("Carregar turmas") }
                Button(enabled = conhecidas.isNotEmpty() && !p.sincronizando, onClick = {
                    SgeCoordStore.salvar(this@MainActivity, p.copy(sincronizando = true, mensagem = "Na fila: ${conhecidas.size} turmas, uma por vez…"))
                    SgeSyncWorker.verificarTurmas(this@MainActivity, conhecidas.map { it.first })
                }) { Text("Verificar todas") }
            }
            FilledTonalButton(onClick = {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("diagnostico", montarDiagnosticoCoord(p)))
                Toast.makeText(this@MainActivity, "Diagnóstico copiado", Toast.LENGTH_SHORT).show()
            }) { Text("Copiar diagnóstico") }
            if (p.sincronizando) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("C conteúdos • F frequência • A plano de aula • P plano de curso • I PEI. * cobertura parcial, — não verificado.", style = MaterialTheme.typography.bodySmall)
            Grade(conhecidas) { (id, nome) ->
                val t = p.turmas.firstOrNull { it.id == id }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(nome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            FilledTonalButton(enabled = !p.sincronizando, onClick = {
                                SgeCoordStore.salvar(this@MainActivity, p.copy(sincronizando = true, mensagem = "Na fila: $nome…"))
                                SgeSyncWorker.verificarTurmas(this@MainActivity, listOf(id))
                            }) { Text(if (t == null) "Verificar" else "Atualizar") }
                        }
                        if (t == null) Text("Ainda não verificada.", style = MaterialTheme.typography.bodySmall)
                        else {
                            Text("Consulta: ${t.atualizado}", style = MaterialTheme.typography.bodySmall)
                            t.disciplinas.forEach { d ->
                                val q = d.modulos.map { it.quantidade }
                                val total = q.sumOf { it ?: 0 }
                                val cor = if (q.any { it == null }) corAlerta() else if (total > 0) corErro() else corOk()
                                val rotulos = listOf("C", "F", "A", "P", "I")
                                Text(d.turma.disciplina, style = MaterialTheme.typography.titleMedium)
                                Text(d.modulos.mapIndexed { i, m -> "${rotulos[i]} ${m.resumo()}" }.joinToString(" • "), color = cor)
                                d.modulos.take(2).filter { (it.quantidade ?: 0) > 0 }.forEach { Text(it.detalhe, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun verificarSessao() {
        if (!::web.isInitialized) return
        web.evaluateJavascript("Boolean(document.querySelector('#TurmaContextoId') || document.querySelector('#ProfessorId') || document.querySelector('#left-panel'))") { raw ->
            val active = runCatching { JSONTokener(raw).nextValue() == true }.getOrDefault(false)
            if (active) { CookieManager.getInstance().flush(); extrairTurmas {}; web.postDelayed({ extrairTurmas {} }, 3000) }
        }
    }
    override fun onPause() { CookieManager.getInstance().flush(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("web_state", Bundle().also { web.saveState(it) })
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        (web.parent as? android.view.ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}

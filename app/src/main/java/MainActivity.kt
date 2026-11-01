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
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color(0xFF10151C))) {
                var login by remember { mutableStateOf(false) }
                var painel by remember { mutableStateOf(SgePainelStore.ler(this)) }
                LaunchedEffect(Unit) {
                    DiarioGlanceWidget().updateAll(this@MainActivity)
                    while (true) {
                        val atualizado = SgePainelStore.ler(this@MainActivity)
                        if (atualizado != painel) {
                            painel = atualizado
                            DiarioGlanceWidget().updateAll(this@MainActivity)
                        }
                        delay(2500)
                    }
                }
                BackHandler(enabled = login) {
                    if (web.canGoBack()) web.goBack() else login = false
                }
                Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp)) {
                    Text("SGE • Pendências por turma", style = MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { login = false }, modifier = Modifier.weight(1f)) { Text("Turmas") }
                        Button(onClick = { login = true }, modifier = Modifier.weight(1f)) { Text("SGE Web") }
                    }
                    // The WebView stays attached when switching tabs, preserving the SGE document.
                    Box(Modifier.weight(1f)) {
                        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                        if (!login) Column(Modifier.fillMaxSize().background(Color.Black)
                            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SelectionContainer { Text(painel.mensagem) }
                            Button(onClick = {
                                SgePainelStore.salvar(this@MainActivity, painel.copy(
                                    sincronizando = true,
                                    inicioConsulta = System.currentTimeMillis(),
                                    mensagem = "Iniciando consulta manual…"
                                ))
                                painel = SgePainelStore.ler(this@MainActivity)
                                extrairTurmas { SgeSyncWorker.sincronizarAgora(this@MainActivity) }
                            }) {
                                Text(if (painel.sincronizando) "Consultando…" else "Sincronizar agora")
                            }
                            Button(onClick = {
                                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("diagnostico", montarDiagnostico(painel)))
                                Toast.makeText(this@MainActivity, "Diagnóstico copiado", Toast.LENGTH_SHORT).show()
                            }) { Text("Copiar diagnóstico") }

                            if (painel.sincronizando) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("— Não verificado • * Cobertura parcial: confira o detalhe. Conteúdos e frequência contam dias pendentes até hoje. Planos/PEIs incluem ausências quando a cobertura é verificada.", style = MaterialTheme.typography.bodySmall)
                            painel.turmas.forEach { resultado ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("${resultado.turma.nome} • ${resultado.turma.disciplina}", style = MaterialTheme.typography.titleMedium)
                                        Text(resultado.turma.escola, style = MaterialTheme.typography.bodySmall)
                                        Text("Consulta: ${resultado.atualizado}", style = MaterialTheme.typography.bodySmall)
                                        resultado.modulos.forEachIndexed { i, m ->
                                            Text("${SgePainelStore.NOMES_MODULOS.getOrElse(i) { "Módulo" }}: ${m.resumo()}")
                                            Text(m.detalhe, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
    private fun verificarSessao() {
        if (!::web.isInitialized) return
        web.evaluateJavascript("Boolean(document.querySelector('#TurmaContextoId') || document.querySelector('#ProfessorId'))") { raw ->
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

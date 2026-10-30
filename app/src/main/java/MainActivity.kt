package br.to.seduc.sgediariowidget

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.updateAll
import br.to.seduc.sgediariowidget.widget.DiarioGlanceWidget

import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker
import kotlinx.coroutines.delay
import org.json.JSONTokener

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var autenticado by mutableStateOf(false)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
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
        SgeSyncWorker.agendarPeriodico(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color(0xFF10151C))) {
                var login by remember { mutableStateOf(false) }
                var painel by remember { mutableStateOf(SgePainelStore.ler(this)) }
                LaunchedEffect(Unit) { DiarioGlanceWidget().updateAll(this@MainActivity);
                    while (true) {
                        painel = SgePainelStore.ler(this@MainActivity)
                        verificarSessao()
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
                            Text(painel.mensagem)
                            Button(onClick = { verificarSessao(); SgeSyncWorker.sincronizarAgora(this@MainActivity) }) {
                                Text(if (painel.sincronizando) "Consultando…" else "Sincronizar agora")
                            }
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
    private fun verificarSessao() {
        if (!::web.isInitialized) return
        web.evaluateJavascript("Boolean(document.querySelector('#TurmaContextoId') || document.querySelector('#ProfessorId'))") { raw ->
            val active = runCatching { JSONTokener(raw).nextValue() == true }.getOrDefault(false)
            if (active && !autenticado) {
                autenticado = true
                CookieManager.getInstance().flush()
                SgeSyncWorker.sincronizarAgora(this)
            } else if (!active && web.url?.contains("/Acesso/Entrar", true) == true) autenticado = false
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

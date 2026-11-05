package br.to.seduc.sgediariowidget

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import br.to.seduc.sgediariowidget.worker.LembreteWorker
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.app.DownloadManager
import android.app.TimePickerDialog
import android.os.Environment
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.withResumed
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import br.to.seduc.sgediariowidget.worker.SgeNoturnoWorker
import java.io.File
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.SgeWidgets
import br.to.seduc.sgediariowidget.widget.SgeWidgetStore
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker
import br.to.seduc.sgediariowidget.worker.SgeDocentesWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONTokener

class MainActivity : FragmentActivity() {
    private lateinit var atualizador: SgeAtualizador
    private var backupPendente by mutableStateOf<SgeBackup.Validado?>(null)
    private var lotePronto by mutableStateOf<ArquivosLote?>(null)
    private var operando by mutableStateOf(false)
    private val criarBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { quandoAtiva { comProtecao { salvarBackup(it) } } }
    }
    private val abrirBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { lifecycleScope.launch {
            try {
                val b = withContext(Dispatchers.IO) { contentResolver.openInputStream(it)!!.use { entrada -> SgeBackup.ler(entrada) } }
                lifecycle.withResumed { comProtecao { backupPendente = b } }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { aviso("Backup inválido ou incompatível. Nenhum dado foi alterado.") }
        } }
    }
    private lateinit var web: WebView
    private lateinit var protecao: CoordProtecao
    private val coordDesbloqueada = mutableStateOf(false)
    private val modoComputadorWeb = mutableStateOf(true)
    private data class WebDownload(val url: String, val userAgent: String?, val contentDisposition: String?, val mimeType: String?)
    private var downloadPendente: WebDownload? = null
    private val pedidoWidget = mutableStateOf<PedidoWidget?>(null)
    private val pedirNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> definirLembrete(ok) }
    private val pedirArmazenamento = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val arquivo = downloadPendente
        downloadPendente = null
        if (ok) arquivo?.let(::baixarWeb) else aviso("Permita acesso aos arquivos para salvar em Downloads.")
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        protecao = CoordProtecao(this)
        atualizador = SgeAtualizador(this)
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
        SgeSyncWorker.agendarPeriodico(this)
        SgeNoturnoWorker.agendar(this, prefs.getBoolean("coord_noturna", false))
        LembreteWorker.agendar(this, prefs.getBoolean("lembrete", false))
        // Cookies permanecem no CookieManager; o app nunca copia a senha do formulário.
        prefs.edit().remove("sge_cookies").apply()
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            val agenteCelular = prefs.getString("web_user_agent_celular", null) ?: settings.userAgentString.orEmpty()
            prefs.edit().putString("web_user_agent_celular", agenteCelular).apply()
            modoComputadorWeb.value = prefs.getBoolean("web_computador", true)
            SgeWebComputador.aplicar(settings, modoComputadorWeb.value, agenteCelular)
            // Consultas em segundo plano mantêm um agente desktop estável, independentemente do modo visível.
            prefs.edit().putString("web_user_agent", SgeWebComputador.agenteComputador(agenteCelular)).apply()
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                val arquivo = WebDownload(url, userAgent, contentDisposition, mimeType)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    downloadPendente = arquivo
                    pedirArmazenamento.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else baixarWeb(arquivo)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageCommitVisible(view: WebView?, url: String?) { SgeWebComputador.viewport(view, modoComputadorWeb.value) }
                override fun onPageFinished(view: WebView?, url: String?) {
                    SgeWebComputador.viewport(view, modoComputadorWeb.value)
                    SgeAtalhoWeb.aoPaginaPronta(view, url)
                    if (url?.contains("/Acesso/Entrar", true) == true) SgeSessaoStore.salvar(this@MainActivity, PerfilSge.EXPIRADO)
                    CookieManager.getInstance().flush(); verificarSessao(); extrairEscola(); instalarHook()
                }
            }
        }
        val state = savedInstanceState?.getBundle("web_state")
        if (state == null || web.restoreState(state) == null) web.loadUrl(SgeSyncWorker.BASE + "/")
        SgeUi.carregar(this); SgeUi.aplicarJanela(this)
        registrarAtalhos()
        receberDestino(intent)
        val versao = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "Prévia"
        setContent {
            var filtros by remember { mutableStateOf(SgeFiltrosStore.ler(this)) }
            var filtrosSalvos by remember { mutableStateOf(SgeFiltrosSalvosStore.ler(this)) }
            var organizacao by remember { mutableStateOf(SgeOrganizacao.ler(this)) }
            var economia by remember { mutableStateOf(SgeEconomia.ler(this)) }
            var historico by remember { mutableStateOf(SgeHistoricoStore.ler(this)) }
            var perfil by remember { mutableStateOf(SgeSessaoStore.perfil(this)) }
            var bloqueio by remember { mutableStateOf(SgeSessaoStore.protegida(this)) }
            var noturna by remember { mutableStateOf(prefs.getBoolean("coord_noturna", false)) }
            var statusNoturno by remember { mutableStateOf(prefs.getString("coord_noturna_status", "").orEmpty()) }
            var tema by remember { mutableStateOf(prefs.getInt("tema", 0)) }
            var cor by remember { mutableStateOf(prefs.getInt("estilo", 0)) }
            var painel by remember { mutableStateOf(SgePainelStore.ler(this)) }
            var coord by remember { mutableStateOf(SgeCoordStore.ler(this)) }
            var filaCoord by remember { mutableStateOf(SgeFila.ler(this)) }
            var pausaDiario by remember { mutableStateOf(SgePausaPessoal.ler(this)) }
            var docentes by remember { mutableStateOf(SgeDocentesStore.ler(this)) }
            var conhecidas by remember { mutableStateOf(SgeWidgetStore.conhecidas(this)) }
            var webVisivel by remember { mutableStateOf(false) }
            var papel by remember { mutableStateOf(prefs.getInt("papel", 0)) }
            var lembrete by remember { mutableStateOf(prefs.getBoolean("lembrete", false)) }
            var lembreteMinuto by remember { mutableStateOf(prefs.getInt("lembrete_minuto", 17 * 60).coerceIn(0, 1439)) }
            val scope = rememberCoroutineScope()
            DisposableEffect(this@MainActivity, webVisivel) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, evento ->
                    if (evento == Lifecycle.Event.ON_RESUME && !webVisivel && prefs.getBoolean("introducao_v2",false)) scope.launch { atualizador.verificar(false) }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, chave -> runOnUiThread {
                    if (chave == null || chave == "historico_semanal_v1") historico = SgeHistoricoStore.ler(this@MainActivity)
                    filtros = SgeFiltrosStore.ler(this@MainActivity)
                    filtrosSalvos = SgeFiltrosSalvosStore.ler(this@MainActivity)
                    organizacao = SgeOrganizacao.ler(this@MainActivity); economia = SgeEconomia.ler(this@MainActivity)
                    perfil = SgeSessaoStore.perfil(this@MainActivity); bloqueio = SgeSessaoStore.protegida(this@MainActivity)
                    noturna = prefs.getBoolean("coord_noturna", false); statusNoturno = prefs.getString("coord_noturna_status", "").orEmpty()
                    painel = SgePainelStore.ler(this@MainActivity)
                    coord = SgeCoordStore.ler(this@MainActivity)
                    filaCoord = SgeFila.ler(this@MainActivity)
                    pausaDiario = SgePausaPessoal.ler(this@MainActivity)
                    docentes = SgeDocentesStore.ler(this@MainActivity)
                    conhecidas = SgeWidgetStore.conhecidas(this@MainActivity)
                    tema = prefs.getInt("tema", 0); cor = prefs.getInt("estilo", 0)
                    papel = prefs.getInt("papel", 0); lembrete = prefs.getBoolean("lembrete", false)
                    lembreteMinuto = prefs.getInt("lembrete_minuto", 17 * 60).coerceIn(0, 1439)
                } }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            LaunchedEffect(Unit) { SgeWidgets.atualizar(this@MainActivity) }
            LaunchedEffect(webVisivel) { if (!webVisivel && prefs.getBoolean("introducao_v2",false)) atualizador.verificar(false) }
            LaunchedEffect(webVisivel) {
                while (webVisivel) { extrairEscola(); instalarHook(); lerPerfilDaPagina(); delay(4000) }
            }
            SgeTema(tema, cor) {
                val scheme = MaterialTheme.colorScheme
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = scheme.background.luminance() > .5f
                        isAppearanceLightNavigationBars = scheme.background.luminance() > .5f
                    }
                }
                AppDiario(web, painel, coord, docentes, conhecidas, !prefs.getBoolean("introducao_v2", false), pedidoWidget.value,
                    tema, cor, versao,
                    { prefs.edit().putInt("tema", it).apply(); scope.launch { SgeWidgets.atualizar(this@MainActivity) } },
                    { prefs.edit().putInt("estilo", it).apply(); scope.launch { SgeWidgets.atualizar(this@MainActivity) } },
                    { prefs.edit().putBoolean("introducao_v2", true).apply() },
                    { sincronizarPessoal() }, { verificarTurmas(it) },
                    { extrairEscola(); Toast.makeText(this@MainActivity, "Lendo as turmas da página do SGE…", Toast.LENGTH_SHORT).show() },
                    { SgeDocentesWorker.atualizar(this@MainActivity) },
                    { _ -> comProtecao { copiarTexto(SgeDiagnosticoUnico.gerar(this@MainActivity), "Diagnóstico único copiado") } },
                    { copiarPagina() },
                    { resultado, ps -> SgeDocentesStore.vincularLocal(this@MainActivity, resultado, ps); scope.launch { SgeWidgets.atualizar(this@MainActivity) } },
                    { tipo -> if (!SgeWidgets.fixar(this@MainActivity, tipo)) Toast.makeText(this@MainActivity,
                        "Abra o seletor de widgets da tela inicial e procure SGE Diário.", Toast.LENGTH_LONG).show() },
                    { g, recorte -> compartilhar(g, recorte) }, { webVisivel = it },
                    papel, lembrete, { definirPapel(it) }, { alternarLembrete(it) }, { sairDoSge() }, { comProtecao { limparDadosLocais() } }, { abrirUrl(it) },
                    historico, perfil, bloqueio, coordDesbloqueada.value, { comProtecao {} }, { alterarBloqueio(it) },
                    noturna, { ativarNoturna(it) }, statusNoturno, { g, fonte, recorte -> exportarPdf(g, fonte, recorte) },
                    RecursosSge(organizacao, economia,
                        { SgeOrganizacao.salvar(this@MainActivity,it) }, { SgeEconomia.salvar(this@MainActivity,it); reagendar(); aviso("Preferências de sincronização salvas") },
                        { comProtecao { criarBackup.launch("SGE-Diario-backup-${java.time.LocalDate.now()}.json") } },
                        { comProtecao { abrirBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } },
                        { atualizador.aberto = true; scope.launch { atualizador.verificar(true) } },
                        { gs,escola,responsavel,recorte -> gerarLote(gs,escola,responsavel,recorte) },
                        filtros, { fonte, f -> SgeFiltrosStore.salvar(this@MainActivity,fonte,f) }, filtrosSalvos,
                        { fonte, salvo ->
                            val atualizados = filtrosSalvos.toMutableMap().apply { put(fonte, (get(fonte).orEmpty() + salvo).takeLast(20)) }
                            filtrosSalvos = atualizados; SgeFiltrosSalvosStore.salvar(this@MainActivity, atualizados)
                        },
                        { fonte, id ->
                            val atualizados = filtrosSalvos.toMutableMap().apply { put(fonte, get(fonte).orEmpty().filterNot { it.id == id }) }
                            filtrosSalvos = atualizados; SgeFiltrosSalvosStore.salvar(this@MainActivity, atualizados)
                        }, filaCoord,
                        { SgeSyncWorker.pausar(this@MainActivity) },
                        { SgeSyncWorker.retomar(this@MainActivity) },
                        { SgeSyncWorker.tentarFalhas(this@MainActivity) },
                        pausaDiario = pausaDiario, aoPausarDiario = { SgeSyncWorker.pausarPessoal(this@MainActivity) },
                        aoRetomarDiario = { SgeSyncWorker.retomarPessoal(this@MainActivity) }),
                    horarioLembrete = String.format(java.util.Locale.ROOT, "%02d:%02d", lembreteMinuto / 60, lembreteMinuto % 60),
                    aoEscolherHorarioLembrete = {
                        val atual = prefs.getInt("lembrete_minuto", 17 * 60).coerceIn(0, 1439)
                        TimePickerDialog(this@MainActivity, { _, hora, minuto ->
                            prefs.edit().putInt("lembrete_minuto", hora * 60 + minuto).apply()
                            LembreteWorker.agendar(this@MainActivity, prefs.getBoolean("lembrete", false), recriar = true)
                        }, atual / 60, atual % 60, true).show()
                    },
                    modoComputador = modoComputadorWeb.value,
                    aoModoComputador = { alterarModoWeb(it) },
                    aoAtalhoSge = { id, turma, componente, modulo -> abrirAtalhoSge(id, turma, componente, modulo) })
                AtualizacoesDialog(atualizador) { lifecycleScope.launch {
                    try { val arquivo = atualizador.prepararInstalacao(); lifecycle.withResumed { if(atualizador.aberto) atualizador.instalar(arquivo) } }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { atualizador.falha(e.message ?: "Não foi possível conferir a atualização.") }
                } }
                if (operando) AlertDialog(onDismissRequest = {}, title = { Text("Preparando arquivos…") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
                if (!bloqueio || coordDesbloqueada.value) {
                    backupPendente?.let { b -> AlertDialog(onDismissRequest = { backupPendente = null }, title = { Text("Restaurar este backup?") },
                        text = { Text("Backup da versão ${b.versao}, criado em ${b.criado}. Contém ${b.resultados} resultado(s) de turma/componente. Substitui painéis, histórico, vínculos e preferências locais. Sua sessão e proteção biométrica não são importadas.") },
                        confirmButton = { Button(onClick = { backupPendente = null; restaurarBackup(b) }) { Text("Restaurar e substituir") } },
                        dismissButton = { TextButton(onClick = { backupPendente = null }) { Text("Cancelar") } }) }
                    lotePronto?.let { l -> AlertDialog(onDismissRequest = { lotePronto = null }, title = { Text("${l.professores} relatório(s) prontos") },
                        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Escolha o arquivo para salvar, compartilhar ou imprimir. As folhas de recebimento têm espaço para data e assinatura.")
                            Button(onClick = { compartilharArquivo(l.zip,"application/zip") }) { Text("ZIP por professor") }
                            OutlinedButton(onClick = { compartilharArquivo(l.impressao,"application/pdf") }) { Text("PDF para impressão") }
                            OutlinedButton(onClick = { compartilharArquivo(l.recibos,"application/pdf") }) { Text("Folhas de recebimento") }
                            l.planilha?.let { p -> OutlinedButton(onClick = { compartilharArquivo(p,"text/csv") }) { Text("Planilha (CSV) para reunião") } }
                        } }, confirmButton = { TextButton(onClick = { lotePronto = null }) { Text("Concluir") } }) }
                }
            }
        }
    }
    private fun aviso(texto: String) { Toast.makeText(this, texto, Toast.LENGTH_LONG).show() }
    private fun quandoAtiva(acao: ()->Unit) { lifecycleScope.launch { lifecycle.withResumed { acao() } } }
    private fun baixarWeb(arquivo: WebDownload) {
        val uri = runCatching { Uri.parse(arquivo.url) }.getOrNull()
        if (uri?.scheme != "https" || !uri.host.equals("sge.seduc.to.pontoid.com.br", ignoreCase = true)) {
            aviso("Por segurança, só é possível baixar arquivos do domínio oficial do SGE."); return
        }
        runCatching {
            val nome = android.webkit.URLUtil.guessFileName(arquivo.url, arquivo.contentDisposition, arquivo.mimeType)
                .replace(Regex("[^\\p{L}\\p{N}._() -]"), "_").trim().trim('.').take(140).ifBlank { "arquivo-sge" }
            val pedido = DownloadManager.Request(uri).setTitle(nome).setDescription("Baixando arquivo do SGE")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, nome)
            arquivo.mimeType?.takeIf { it.isNotBlank() }?.let(pedido::setMimeType)
            CookieManager.getInstance().getCookie(arquivo.url)?.takeIf { it.isNotBlank() }?.let { pedido.addRequestHeader("Cookie", it) }
            (arquivo.userAgent?.takeIf { it.isNotBlank() } ?: web.settings.userAgentString).let { pedido.addRequestHeader("User-Agent", it) }
            val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(pedido)
            aviso("Download iniciado. O arquivo ficará em Downloads (nº $id).")
        }.onFailure { aviso("Não foi possível iniciar o download. Confira a conexão e o espaço disponível.") }
    }
    private fun abrirAtalhoSge(turmaId: String, turma: String, componente: String, modulo: Int) {
        if (!::web.isInitialized) return
        SgeAtalhoWeb.abrir(web, SgeSyncWorker.BASE, AtalhoSge(turmaId, turma, componente, modulo))
    }
    private fun alterarModoWeb(computador: Boolean) {
        if (!::web.isInitialized || modoComputadorWeb.value == computador) return
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
        val agenteCelular = prefs.getString("web_user_agent_celular", null) ?: web.settings.userAgentString.orEmpty()
        modoComputadorWeb.value = computador
        SgeWebComputador.aplicar(web.settings, computador, agenteCelular)
        prefs.edit().putBoolean("web_computador", computador)
            .putString("web_user_agent", SgeWebComputador.agenteComputador(agenteCelular)).apply()
        web.reload()
    }
    private fun reagendar() {
        val p = getSharedPreferences(SgePainelStore.PREFS_NAME,MODE_PRIVATE)
        SgeSyncWorker.agendarPeriodico(this)
        SgeNoturnoWorker.agendar(this,p.getBoolean("coord_noturna",false),recriar=true)
        LembreteWorker.agendar(this,p.getBoolean("lembrete",false),recriar=true)
    }
    private fun salvarBackup(uri: Uri) {
        lifecycleScope.launch {
            operando = true
            try {
                val valores = getSharedPreferences(SgePainelStore.PREFS_NAME,MODE_PRIVATE).all
                val versao = packageManager.getPackageInfo(packageName,0).versionName.orEmpty()
                withContext(Dispatchers.IO) {
                    val texto = SgeBackup.criar(valores,versao)
                    SgeBackup.validar(texto)
                    contentResolver.openOutputStream(uri,"wt")!!.use { it.write(texto.toByteArray(Charsets.UTF_8)) }
                }
                aviso("Backup salvo no local escolhido")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { aviso("Não foi possível salvar o backup. Confira o local escolhido.") }
            finally { operando = false }
        }
    }
    private fun restaurarBackup(b: SgeBackup.Validado) {
        comProtecao { lifecycleScope.launch {
            operando = true
            try {
                cancelarConsultas()
                withContext(Dispatchers.IO) {
                    // Aguarda o cancelamento das filas antes da transação de restauração.
                    val wm = androidx.work.WorkManager.getInstance(this@MainActivity)
                    listOf("sge_sync_now","sge_periodic_sync","sge_coord","sge_docentes_lista","sge_docentes_horarios","sge_coord_noturna","sge_retry_morning").forEach { wm.cancelUniqueWork(it).result.get() }
                    SgeBackup.restaurar(this@MainActivity,b)
                }
                reagendar(); SgeWidgets.atualizar(this@MainActivity)
                aviso("Backup restaurado. Confira a sessão no SGE Web antes de atualizar.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { aviso("Não foi possível restaurar o backup.") }
            finally { operando = false }
        } }
    }
    private fun gerarLote(gs: List<GrupoPendencias>, escola: String, responsavel: String, recorte: String) {
        if (operando) return
        comProtecao { lifecycleScope.launch {
            operando = true
            try {
                val l = withContext(Dispatchers.IO) { SgeRelatoriosLote.criar(File(cacheDir,"relatorios"),gs,escola,responsavel,recorte) }
                lifecycle.withResumed { lotePronto = l }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { aviso("Não foi possível gerar o lote de relatórios.") }
            finally { operando = false }
        } }
    }
    private fun compartilharArquivo(arquivo: File, tipo: String) {
        comProtecao {
            runCatching {
                val uri = FileProvider.getUriForFile(this,"$packageName.arquivos",arquivo)
                val enviar = Intent(Intent.ACTION_SEND).setType(tipo).putExtra(Intent.EXTRA_STREAM,uri)
                    .apply { clipData = android.content.ClipData.newUri(contentResolver,"Arquivo SGE",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                startActivity(Intent.createChooser(enviar,"Salvar ou compartilhar arquivo"))
            }.onFailure { aviso("Não foi possível abrir o compartilhamento deste arquivo.") }
        }
    }
    private fun comProtecao(acao: () -> Unit) {
        if (!SgeSessaoStore.protegida(this) || coordDesbloqueada.value) acao()
        else protecao.autenticar { coordDesbloqueada.value = true; acao() }
    }
    private fun alterarBloqueio(ativo: Boolean) {
        val aplicar = {
            getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE).edit().putBoolean("coord_biometria", ativo).apply()
            coordDesbloqueada.value = ativo
            lifecycleScope.launch { SgeWidgets.atualizar(this@MainActivity) }
            Unit
        }
        if (ativo) protecao.autenticar(aplicar) else comProtecao(aplicar)
    }
    private fun ativarNoturna(ativa: Boolean) {
        val hora = SgeEconomia.ler(this).horaNoturna.coerceIn(0, 23)
        getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean("coord_noturna", ativa)
            .putBoolean("coord_retry_pending", false)
            .putString("coord_noturna_status", if (ativa) "Ativa; consulta noturna prevista por volta de %02d:00. O Android pode adiá-la.".format(hora)
                else "Verificação noturna desativada.")
            .apply()
        SgeNoturnoWorker.agendar(this, ativa, recriar = true)
    }
    private fun exportarPdf(g: GrupoPendencias, fonte: FontePainel, recorte: String = "") {
        val iniciar = {
            lifecycleScope.launch {
                try {
                    val arquivo = withContext(Dispatchers.IO) { SgeRelatorioPdf.criar(File(cacheDir, "relatorios"), g, fonte, recorte) }
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || (fonte == FontePainel.COORDENACAO && SgeSessaoStore.protegida(this@MainActivity) && !coordDesbloqueada.value)) return@launch
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.arquivos", arquivo)
                    val enviar = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                        .apply { clipData = android.content.ClipData.newUri(contentResolver, "Relatório SGE", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    startActivity(Intent.createChooser(enviar, "Compartilhar PDF"))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Toast.makeText(this@MainActivity, "Não foi possível gerar ou compartilhar o PDF.", Toast.LENGTH_LONG).show() }
            }
            Unit
        }
        if (fonte == FontePainel.COORDENACAO) comProtecao(iniciar) else iniciar()
    }
    private fun lerPerfilDaPagina() {
        if (!::web.isInitialized || web.url?.startsWith(SgeSyncWorker.BASE + "/") != true) return
        web.evaluateJavascript("""(function(){var e=document.querySelector('#ehPerfilEscola'),p=document.querySelector('#ehPerfilProfessor');if(!e||!p)return null;return JSON.stringify({escola:e.value,professor:p.value});})()""") { raw ->
            runCatching {
                val value = JSONTokener(raw).nextValue()
                if (value is String) {
                    val j = org.json.JSONObject(value)
                    val e = j.optString("escola").equals("true", true); val p = j.optString("professor").equals("true", true)
                    if (e != p) SgeSessaoStore.salvar(this, if (e) PerfilSge.COORDENACAO else PerfilSge.PROFESSOR)
                }
            }
        }
    }
    override fun onStop() { coordDesbloqueada.value = false; super.onStop() }
    private fun definirPapel(valor: Int) { getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE).edit().putInt("papel", valor).apply() }
    private fun definirLembrete(ligado: Boolean) {
        getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE).edit().putBoolean("lembrete", ligado).apply()
        LembreteWorker.agendar(this, ligado, recriar = true)
        if (!ligado) Toast.makeText(this, "Lembrete desativado", Toast.LENGTH_SHORT).show()
    }
    private fun alternarLembrete(ligar: Boolean) {
        if (ligar && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS); return
        }
        definirLembrete(ligar)
        if (ligar) {
            val minuto = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE).getInt("lembrete_minuto", 17 * 60).coerceIn(0, 1439)
            val hora = String.format(java.util.Locale.ROOT, "%02d:%02d", minuto / 60, minuto % 60)
            Toast.makeText(this, "Lembrete diário ativado para $hora", Toast.LENGTH_SHORT).show()
        }
    }
    private fun cancelarConsultas() {
        SgeSessaoStore.invalidar(this)
        val wm = androidx.work.WorkManager.getInstance(this)
        listOf("sge_sync_now", "sge_periodic_sync", "sge_coord", "sge_docentes_lista", "sge_docentes_horarios", "sge_coord_noturna", "sge_retry_morning").forEach { wm.cancelUniqueWork(it) }
    }
    private fun sairDoSge() {
        cancelarConsultas()
        coordDesbloqueada.value = false; SgeSessaoStore.salvar(this, PerfilSge.EXPIRADO)
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush(); if (!isFinishing && !isDestroyed) web.loadUrl(SgeSyncWorker.BASE + "/") }
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().remove("turmas_contexto").remove("turmas_escola").apply()
        Toast.makeText(this, "Sessão encerrada neste aparelho", Toast.LENGTH_SHORT).show()
    }
    private fun limparDadosLocais() {
        cancelarConsultas()
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
        val manter = SgeEconomia.chaves + setOf("tema", "estilo", "papel", "lembrete", "lembrete_minuto", "introducao_v2", "web_user_agent", "web_user_agent_celular", "web_computador", "coord_biometria", "coord_noturna", "coord_retry_pending", "sessao_geracao")
        prefs.edit().also { e -> prefs.all.keys.filter { it !in manter }.forEach { e.remove(it) } }.apply()
        lifecycleScope.launch { SgeWidgets.atualizar(this@MainActivity) }
        Toast.makeText(this, "Dados locais apagados", Toast.LENGTH_SHORT).show()
    }
    private fun abrirUrl(url: String) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }

    private fun sincronizarPessoal() {
        val p = SgePainelStore.ler(this)
        if (p.sincronizando) return
        SgePainelStore.salvar(this, p.copy(sincronizando = true, inicioConsulta = System.currentTimeMillis(), mensagem = "Iniciando consulta…"))
        extrairEscola(); extrairTurmas { SgeSyncWorker.sincronizarAgora(this) }
    }
    private fun verificarTurmas(ids: List<String>) {
        if (ids.isEmpty()) return
        val fila = SgeFila.ler(this)
        val todas = fila != null && ids.toSet().containsAll(fila.ids)
        // Com a fila andando, tocar no botão geral pausa; com a fila pausada, retoma de onde parou.
        if (SgeCoordStore.ler(this).sincronizando) { if (todas) SgeSyncWorker.pausar(this); return }
        if (fila != null && fila.pausada && fila.ids.isNotEmpty() && todas) { SgeSyncWorker.retomar(this); return }
        SgeSyncWorker.verificarTurmas(this, ids.distinct())
    }
    private fun copiarTexto(texto: String, mensagem: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("SGE Diário", texto))
        Toast.makeText(this, mensagem, Toast.LENGTH_SHORT).show()
    }
    private fun compartilhar(g: GrupoPendencias, recorte: String = "") {
        val porProfessor = g.chave.startsWith("professor:")
        val texto = if (porProfessor) SgeRelatorioTexto.mensagemProfessor(g) else SgeRelatorioTexto.gerar(g, recorte)
        val titulo = if (porProfessor) "Mensagem pronta para o professor" else "Compartilhar resumo"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), titulo))
    }
    private fun registrarAtalhos() {
        runCatching {
            fun atalho(id: String, curto: String, longo: String, icone: Int) = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(curto).setLongLabel(longo).setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, icone))
                .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("sge_atalho", id)).build()
            androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this, listOf(
                atalho("diario", "Meu diário", "Abrir meu diário", R.drawable.ic_widget_refresh),
                atalho("sincronizar", "Sincronizar", "Sincronizar agora", R.drawable.ic_widget_refresh),
                atalho("coordenacao", "Coordenação", "Abrir a coordenação", R.drawable.ic_widget_settings),
                atalho("relatorio", "Relatório", "Gerar relatório em PDF", R.drawable.ic_widget_down)))
        }
    }
    private fun atalhoSincronizar() {
        if (SgeSessaoStore.perfil(this) == PerfilSge.COORDENACAO) comProtecao { verificarTurmas(SgeWidgetStore.conhecidas(this).map { it.first }.distinct()) }
        else sincronizarPessoal()
    }
    private fun atalhoRelatorio() {
        val escola = SgeSessaoStore.perfil(this) == PerfilSge.COORDENACAO
        val rs = if (escola) SgeCoordStore.ler(this).turmas.flatMap { it.disciplinas } else SgePainelStore.ler(this).turmas
        if (rs.isEmpty()) { aviso("Ainda não há consulta para gerar o relatório."); return }
        val g = GrupoPendencias(if (escola) "escola" else "diario", if (escola) "Escola inteira" else "Seu diário",
            "${rs.map { it.turma.id }.distinct().size} turma(s)", rs, rs.map { it.turma.id }.toSet())
        exportarPdf(g, if (escola) FontePainel.COORDENACAO else FontePainel.PESSOAL)
    }
    private fun receberDestino(intent: Intent?) {
        intent?.getStringExtra("sge_atalho")?.let { a ->
            intent?.removeExtra("sge_atalho")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                when (a) {
                    "diario" -> pedidoWidget.value = PedidoWidget(FontePainel.PESSOAL, Agrupamento.TURMA, "")
                    "sincronizar" -> atalhoSincronizar()
                    "coordenacao" -> pedidoWidget.value = PedidoWidget(FontePainel.COORDENACAO, Agrupamento.TURMA, "")
                    "relatorio" -> atalhoRelatorio()
                }
            }
        }
        if (intent?.hasExtra("sge_fonte") != true) return
        runCatching { PedidoWidget(FontePainel.valueOf(intent.getStringExtra("sge_fonte")!!),
            Agrupamento.valueOf(intent.getStringExtra("sge_por") ?: "TURMA"), intent.getStringExtra("sge_grupo").orEmpty()) }
            .getOrNull()?.let { pedidoWidget.value = it }
    }
    /** Teclado externo (notebook/tablet): Ctrl+R atualiza, Ctrl+F busca, Ctrl+1 e Ctrl+2 trocam de painel. */
    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        if (e.action == android.view.KeyEvent.ACTION_DOWN && e.isCtrlPressed && e.repeatCount == 0) {
            when (e.keyCode) {
                android.view.KeyEvent.KEYCODE_R -> { atalhoSincronizar(); return true }
                android.view.KeyEvent.KEYCODE_F -> { SgeUi.buscaPedida++; return true }
                android.view.KeyEvent.KEYCODE_1 -> { pedidoWidget.value = PedidoWidget(FontePainel.PESSOAL, Agrupamento.TURMA, ""); return true }
                android.view.KeyEvent.KEYCODE_2 -> { pedidoWidget.value = PedidoWidget(FontePainel.COORDENACAO, Agrupamento.TURMA, ""); return true }
            }
        }
        return super.dispatchKeyEvent(e)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receberDestino(intent) }
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
            r.menu=A(document.querySelectorAll('#left-panel a')).slice(0,100).map(function(a){var h=a.getAttribute('href')||'';return t(a.textContent)+' -> '+h.split('?')[0].slice(0,60)+(a.getAttribute('onclick')?' [onclick:'+a.getAttribute('onclick').slice(0,50)+']':'')+' ['+A(a.attributes).map(function(x){return x.name}).filter(function(n){return n.indexOf('data-')===0}).join(',')+']'});
            r.acoesContexto=A(document.querySelectorAll('#div-contexto-acoes-turma a,#div-contexto-acoes-turma button,#div-contexto-acoes-turma [onclick],#div-contexto-turma-visao-professor a,#div-contexto-turma-visao-professor button')).slice(0,40).map(function(e){return {tag:e.tagName,id:e.id,t:t(e.textContent),titulo:t(e.getAttribute('title')||e.getAttribute('data-original-title')||''),href:(e.getAttribute('href')||'').slice(0,50),oc:(e.getAttribute('onclick')||'').slice(0,60),cls:(e.className||'').slice(0,40)}});
            r.botoes=A(document.querySelectorAll('#content button,#content a.btn,#content input[type=button],#content input[type=submit]')).slice(0,30).map(function(e){return (e.id||'-')+':'+t(e.textContent||e.value)});
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
    private fun verificarSessao() {
        if (!::web.isInitialized) return
        web.evaluateJavascript("Boolean(document.querySelector('#TurmaContextoId') || document.querySelector('#ProfessorId') || document.querySelector('#left-panel'))") { raw ->
            val active = runCatching { JSONTokener(raw).nextValue() == true }.getOrDefault(false)
            if (active) { SgeSyncWorker.agendarPeriodico(this); SgeNoturnoWorker.agendar(this, getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE).getBoolean("coord_noturna", false)); lerPerfilDaPagina(); CookieManager.getInstance().flush(); extrairTurmas {}; web.postDelayed({ extrairTurmas {} }, 3000) }
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

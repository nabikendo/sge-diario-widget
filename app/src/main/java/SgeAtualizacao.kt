package br.to.seduc.sgediariowidget

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.readBytesLimitados
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Metadados publicados junto ao APK. Uma oferta nunca troca pacote ou canal. */
data class OfertaAtualizacao(val pacote: String, val versao: String, val codigo: Long, val minSdk: Int, val arquivo: String, val tamanho: Long, val sha256: String, val url: String, val notas: String = "") {
    fun json() = JSONObject().put("schema", 1).put("pacote", pacote).put("versao", versao).put("codigo", codigo).put("minSdk", minSdk).put("arquivo", arquivo).put("tamanho", tamanho).put("sha256", sha256).put("url", url).put("notas", notas).toString()
}
object PoliticaAtualizacao {
    const val REPO = "nabikendo/sge-diario-widget"
    fun urlAsset(url: String): Boolean = runCatching { URL(url).let { it.protocol == "https" && it.host == "github.com" && it.port == -1 && it.userInfo == null && it.path.startsWith("/$REPO/releases/download/") && !it.path.contains("..") } }.getOrDefault(false)
    fun ler(j: JSONObject, pacote: String, instalado: Long, sdk: Int, url: String, notas: String = ""): OfertaAtualizacao {
        require(j.getInt("schema") == 1 && j.getString("pacote") == pacote) { "Atualização de outro aplicativo ou canal." }
        val o = OfertaAtualizacao(pacote,j.getString("versao"),j.getLong("codigo"),j.getInt("minSdk"),j.getString("arquivo"),j.getLong("tamanho"),j.getString("sha256").lowercase(),url,notas.take(3000))
        require(o.codigo > instalado) { "O app já está atualizado." }
        require(o.minSdk <= sdk && o.minSdk >= 21) { "Esta versão exige um Android mais recente." }
        require(o.tamanho in 1..209_715_200L && o.sha256.matches(Regex("[a-f0-9]{64}")) && o.arquivo.matches(Regex("[A-Za-z0-9._-]+\\.apk"))) { "Metadados inválidos." }
        require(urlAsset(o.url) && URL(o.url).path.endsWith("/${o.arquivo}")) { "Origem da atualização inválida." }
        return o
    }
    fun assinaturaPermitida(atuais: Set<String>, novas: Set<String>, historicoNovo: Set<String>): Boolean = atuais.isNotEmpty() && novas.isNotEmpty() && (atuais == novas || (atuais.size == 1 && novas.size == 1 && historicoNovo.containsAll(atuais)))
}

object SgeValidadorApk {
    fun validar(ctx: Context, arquivo: File, o: OfertaAtualizacao, atual: android.content.pm.PackageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)) {
        require(arquivo.length() == o.tamanho) { "Tamanho do APK inválido." }
        val digest = MessageDigest.getInstance("SHA-256")
        arquivo.inputStream().use { entrada -> val buffer = ByteArray(32768); while(true) { val n=entrada.read(buffer);if(n<0)break;digest.update(buffer,0,n) } }
        require(digest.digest().joinToString("") { "%02x".format(it) } == o.sha256) { "Integridade do APK inválida." }
        val novo = ctx.packageManager.getPackageArchiveInfo(arquivo.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES) ?: error("APK inválido.")
        require(novo.packageName == ctx.packageName && novo.packageName == o.pacote && novo.longVersionCode == o.codigo && novo.longVersionCode > atual.longVersionCode && (novo.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "APK incompatível com este aplicativo." }
        fun certs(s: Array<android.content.pm.Signature>?): Set<String> = s.orEmpty().map { it.toCharsString() }.toSet()
        require(PoliticaAtualizacao.assinaturaPermitida(certs(atual.signingInfo?.apkContentsSigners),certs(novo.signingInfo?.apkContentsSigners),certs(novo.signingInfo?.signingCertificateHistory))) { "A assinatura não corresponde ao aplicativo instalado." }
    }
}

class SgeAtualizador(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("sge_atualizacoes", 0)
    private val dm = ctx.getSystemService(DownloadManager::class.java)
    private val info get() = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    var aberto by mutableStateOf(false)
    var oferta by mutableStateOf<OfertaAtualizacao?>(null); private set
    var mensagem by mutableStateOf(""); private set
    var ocupado by mutableStateOf(false); private set
    var progresso by mutableStateOf(-1f); private set
    var pronto by mutableStateOf(false); private set
    var wifi by mutableStateOf(prefs.getBoolean("wifi", true)); private set
    var automatico by mutableStateOf(prefs.getBoolean("automatico", true)); private set
    val baixando get() = prefs.getLong("download", -1) >= 0 && !pronto
    fun wifi(v: Boolean) { wifi = v; prefs.edit().putBoolean("wifi",v).apply() }
    fun automatico(v: Boolean) { automatico = v; prefs.edit().putBoolean("automatico",v).apply() }
    init {
        oferta = runCatching { val j = JSONObject(prefs.getString("oferta", "")!!); PoliticaAtualizacao.ler(j,ctx.packageName,info.longVersionCode,Build.VERSION.SDK_INT,j.getString("url"),j.optString("notas")) }.getOrNull()
        if (oferta == null) cancelar()
    }
    suspend fun verificar(manual: Boolean) {
        if (ocupado) return
        if (!manual && (!automatico || System.currentTimeMillis() - prefs.getLong("consulta",0) < 86_400_000L)) return
        if (manual) aberto = true
        if (baixando || pronto) { atualizarProgresso(); return }
        ocupado = true; mensagem = "Verificando nova versão…"
        try {
            val nova = withContext(Dispatchers.IO) {
                val teste = ctx.packageName.endsWith(".teste")
                val release = JSONObject(baixarJson("https://api.github.com/repos/${PoliticaAtualizacao.REPO}/releases/" + if (teste) "tags/latest" else "latest"))
                require(!release.optBoolean("draft") && (teste || !release.optBoolean("prerelease")))
                val assets = release.getJSONArray("assets")
                val lista = (0 until assets.length()).map { assets.getJSONObject(it) }
                val meta = lista.firstOrNull { it.getString("name") == if (teste) "atualizacao-teste.json" else "atualizacao.json" } ?: return@withContext null
                val metaUrl = meta.getString("browser_download_url"); require(PoliticaAtualizacao.urlAsset(metaUrl))
                val j = JSONObject(baixarJson(metaUrl))
                if (j.getLong("codigo") <= info.longVersionCode) return@withContext null
                val asset = lista.single { it.getString("name") == j.getString("arquivo") }
                require(asset.getLong("size") == j.getLong("tamanho"))
                PoliticaAtualizacao.ler(j,ctx.packageName,info.longVersionCode,Build.VERSION.SDK_INT,asset.getString("browser_download_url"),release.optString("body"))
            }
            prefs.edit().putLong("consulta",System.currentTimeMillis()).apply()
            oferta = nova; mensagem = if (nova == null) "Você está com a versão mais recente disponível para este canal." else "Nova versão disponível: ${nova.versao}"
            if (nova != null) {
                prefs.edit().putString("oferta",nova.json()).apply()
                if (!manual && (prefs.getLong("adiar_codigo",-1) != nova.codigo || System.currentTimeMillis() >= prefs.getLong("adiar_ate",0))) aberto = true
            } else prefs.edit().remove("oferta").apply()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mensagem = "Não foi possível verificar a atualização. Tente novamente com internet." }
        finally { ocupado = false }
    }
    private fun baixarJson(url: String): String {
        var atual = URL(url)
        repeat(6) {
            require(atual.protocol == "https" && atual.userInfo == null && atual.port == -1 && atual.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
            val c = atual.openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 20_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "SGE-Diario-Atualizador"); c.setRequestProperty("Accept", "application/json")
            try {
                if (c.responseCode in 300..399) { atual = URL(atual,c.getHeaderField("Location")); return@repeat }
                check(c.responseCode == 200)
                return c.inputStream.use { it.readBytesLimitados(512 * 1024).toString(Charsets.UTF_8) }
            } finally { c.disconnect() }
        }
        error("Redirecionamentos demais")
    }
    fun baixar() {
        val o = oferta ?: return
        cancelarDownload()
        val pasta = File(checkNotNull(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)),"atualizacoes").apply { mkdirs() }
        pasta.listFiles()?.forEach { it.delete() }
        val dest = File(pasta,"atualizacao.apk")
        val r = DownloadManager.Request(Uri.parse(o.url)).setTitle("SGE Diário ${o.versao}").setDescription("Atualização solicitada por você")
            .setDestinationUri(Uri.fromFile(dest)).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false).setAllowedNetworkTypes(if (wifi) DownloadManager.Request.NETWORK_WIFI else DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        prefs.edit().putLong("download",dm.enqueue(r)).apply()
        pronto = false; progresso = 0f; mensagem = "Download iniciado. Ao concluir, toque em Instalar atualização."
    }
    suspend fun atualizarProgresso() {
        val id = prefs.getLong("download",-1); if (id < 0 || ocupado) return
        val estado = withContext(Dispatchers.IO) { dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use Triple(-1,0L,0L)
            Triple(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
        } }
        if (estado == null || estado.first == -1 || estado.first == DownloadManager.STATUS_FAILED) { cancelarDownload(); mensagem = "O download foi interrompido. Toque em baixar para tentar de novo."; return }
        progresso = if (estado.third > 0) (estado.second.toFloat()/estado.third).coerceIn(0f,1f) else -1f
        pronto = estado.first == DownloadManager.STATUS_SUCCESSFUL
        mensagem = when { pronto -> "Download concluído. A integridade e a assinatura serão conferidas antes de instalar."; estado.first == DownloadManager.STATUS_PAUSED -> "Download aguardando rede disponível. Confira a opção de rede sem franquia."; else -> "Baixando atualização…" }
    }
    suspend fun prepararInstalacao(): File {
        val o = oferta ?: error("Verifique a atualização novamente.")
        ocupado = true
        return try { withContext(Dispatchers.IO) {
            val id = prefs.getLong("download",-1); val uri = dm.getUriForDownloadedFile(id) ?: error("Download indisponível. Baixe novamente.")
            val pasta = File(ctx.cacheDir,"atualizacoes").apply { mkdirs() }
            val arquivo = File(pasta,"SGE-Diario-atualizacao.apk"); arquivo.delete()
            try {
                val hash = MessageDigest.getInstance("SHA-256"); var tamanho = 0L
                ctx.contentResolver.openInputStream(uri)!!.use { entrada -> arquivo.outputStream().use { saida ->
                    val buffer = ByteArray(32768)
                    while (true) { val n = entrada.read(buffer); if (n < 0) break; tamanho += n; require(tamanho <= o.tamanho) { "Tamanho do APK inválido." }; hash.update(buffer,0,n); saida.write(buffer,0,n) }
                } }
                require(tamanho == o.tamanho && hash.digest().joinToString("") { "%02x".format(it) } == o.sha256) { "Integridade do APK inválida. Baixe novamente." }
                SgeValidadorApk.validar(ctx,arquivo,o)
                arquivo
            } catch (e: Exception) { arquivo.delete(); throw e }
        } } finally { ocupado = false }
    }
    fun instalar(arquivo: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            mensagem = "Autorize o SGE Diário a instalar atualizações nesta tela do Android. Depois volte e toque em Instalar atualização."
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${ctx.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(ctx,"${ctx.packageName}.arquivos",arquivo)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    fun falha(texto: String) { mensagem = texto }
    private fun cancelarDownload() { val id = prefs.getLong("download",-1); if (id >= 0) dm.remove(id); prefs.edit().remove("download").apply(); pronto = false; progresso = -1f }
    fun cancelar() { cancelarDownload(); File(ctx.cacheDir,"atualizacoes").deleteRecursively() }
    fun adiar() { oferta?.let { prefs.edit().putLong("adiar_codigo",it.codigo).putLong("adiar_ate",System.currentTimeMillis()+86_400_000L).apply() }; aberto = false }
}

@Composable fun AtualizacoesDialog(a: SgeAtualizador, instalar: () -> Unit) {
    val scope = rememberCoroutineScope()
    if (!a.aberto) return
    LaunchedEffect(a.aberto, a.baixando) { while (a.aberto && a.baixando) { a.atualizarProgresso(); kotlinx.coroutines.delay(1000) } }
    AlertDialog(onDismissRequest = { a.adiar() }, title = { Text("Atualizações do app") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(a.mensagem.ifBlank { "Verifique se existe uma nova versão do SGE Diário." })
            a.oferta?.let { Text("Versão ${it.versao} · ${it.tamanho/1024/1024} MB",style=MaterialTheme.typography.titleSmall) }
            if (a.baixando || a.ocupado) { if (a.progresso >= 0 && !a.ocupado) LinearProgressIndicator(a.progresso,Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth()) }
            Row { Text("Avisar quando houver atualização",Modifier.weight(1f)); Switch(a.automatico,{ a.automatico(it) }) }
            Row { Text("Baixar só em rede sem franquia (Wi-Fi)",Modifier.weight(1f)); Switch(a.wifi,{ a.wifi(it) },enabled=!a.baixando) }
            Text("O Android pede sua confirmação para instalar. Seus dados são mantidos ao atualizar com o mesmo pacote e assinatura.",style=MaterialTheme.typography.bodySmall)
            a.oferta?.notas?.takeIf { it.isNotBlank() }?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            if (a.baixando) TextButton(onClick = { a.cancelar() }) { Text("Cancelar download") }
        } },
        confirmButton = { Button(enabled=!a.ocupado && !a.baixando,onClick = { when { a.pronto -> instalar(); a.oferta != null -> runCatching { a.baixar() }.onFailure { a.falha("Não foi possível iniciar o download.") }; else -> scope.launch { a.verificar(true) } } }) { Text(if (a.pronto) "Instalar atualização" else if (a.oferta != null) "Baixar atualização" else "Verificar agora") } },
        dismissButton = { TextButton(onClick = { a.adiar() }) { Text("Mais tarde") } })
}

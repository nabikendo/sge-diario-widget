package br.to.seduc.sgediariowidget.worker

import android.content.Context
import android.webkit.CookieManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.to.seduc.sgediariowidget.data.SgeRepository
import br.to.seduc.sgediariowidget.data.SgeSummary
import br.to.seduc.sgediariowidget.widget.DiarioGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class SgeSyncWorker(private val appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = "https://sge.seduc.to.pontoid.com.br"
        val cookies = CookieManager.getInstance().getCookie(baseUrl)
        if (cookies.isNullOrBlank()) return@withContext marcarSessaoExpirada()
        try {
            val home = Jsoup.connect("$baseUrl/").header("Cookie", cookies).userAgent(USER_AGENT).timeout(15000).get()
            if (home.location().contains("/Acesso/Entrar", true)) return@withContext marcarSessaoExpirada()
            val turmaId = extrairTurmaId(home)
            if (turmaId.isBlank()) return@withContext Result.retry()
            val conteudos = contarPagina("$baseUrl/conteudo?tipoDeTurma=Escolarizacao&turmaId=$turmaId", cookies, "Conteúdo", "Pendente")
            val frequencia = contarPagina("$baseUrl/frequenciadiaria/index/$turmaId", cookies, "Frequência", "Pendente")
            val planejamento = contarPagina("$baseUrl/planejamentoturma?turmaId=$turmaId", cookies, "Plano", "Pendente")
            val atual = SgeSummary(
                conteudos = conteudos,
                frequencia = frequencia,
                planosAula = planejamento,
                planoCurso = 0,
                peis = 0,
                detalhes = "Conteúdos, frequência e planejamento sincronizados; Plano de Curso e PEIs aguardam rota própria do SGE.",
                sessaoAtiva = true
            )
            SgeRepository.salvarResumo(appContext, atual)
            DiarioGlanceWidget().updateAll(appContext)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    private fun extrairTurmaId(home: org.jsoup.nodes.Document): String {
        val onclick = home.select("a[data-acaonaturma=ConteudoAplicado]").attr("onclick")
        return Regex("turmaId=(\\d+)").find(onclick)?.groupValues?.get(1) ?: ""
    }

    private fun contarPagina(url: String, cookies: String, modulo: String, status: String): Int {
        val page = Jsoup.connect(url).header("Cookie", cookies).userAgent(USER_AGENT).timeout(15000).get()
        val calendar = page.select(".mes-subtitulo span").mapNotNull { el ->
            Regex("(\\d+)\\s+de\\s+(\\d+)\\s+registrados", RegexOption.IGNORE_CASE).find(el.text())?.let {
                ((it.groupValues[2].toIntOrNull() ?: 0) - (it.groupValues[1].toIntOrNull() ?: 0)).coerceAtLeast(0)
            }
        }
        if (calendar.isNotEmpty()) return calendar.sum()
        return page.select(".badge-danger, .label-danger, tr:matches((?i)$modulo):matches((?i)$status)").distinctBy { it.outerHtml() }.size
    }

    private suspend fun marcarSessaoExpirada(): Result {
        val atual = SgeRepository.lerResumo(appContext)
        SgeRepository.salvarResumo(appContext, atual.copy(sessaoAtiva = false, detalhes = "Sessão expirada. Abra o app e entre no SGE."))
        DiarioGlanceWidget().updateAll(appContext)
        return Result.success()
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S721B) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36"
        fun agendarPeriodico(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("sge_periodic_sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SgeSyncWorker>(2, TimeUnit.HOURS).build()) }
        fun sincronizarAgora(context: Context) { WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SgeSyncWorker>().build()) }
    }
}

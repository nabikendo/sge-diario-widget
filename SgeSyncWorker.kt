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

class SgeSyncWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = "https://sge.seduc.to.pontoid.com.br"
        val cookies = CookieManager.getInstance().getCookie(baseUrl)

        if (cookies.isNullOrBlank()) {
            val atual = SgeRepository.lerResumo(appContext)
            SgeRepository.salvarResumo(
                appContext,
                atual.copy(
                    sessaoAtiva = false,
                    detalhes = "Sessão expirada. Abra o app para logar no SGE."
                )
            )
            DiarioGlanceWidget().updateAll(appContext)
            return@withContext Result.success()
        }

        try {
            val doc = Jsoup.connect("$baseUrl/")
                .header("Cookie", cookies)
                .userAgent("Mozilla/5.0 (Linux; Android 14; SM-S721B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .timeout(15000)
                .followRedirects(true)
                .get()

            if (doc.location().contains("/Acesso/Entrar", ignoreCase = true)) {
                val atual = SgeRepository.lerResumo(appContext)
                SgeRepository.salvarResumo(
                    appContext,
                    atual.copy(
                        sessaoAtiva = false,
                        detalhes = "Sessão SGE expirada • Toque para entrar"
                    )
                )
                DiarioGlanceWidget().updateAll(appContext)
                return@withContext Result.success()
            }

            val conteudos = contarOcorrenciasModulo(baseUrl, cookies, "rota_conteudos", "", "Conteúdo", "Pendente")
            val frequencia = contarOcorrenciasModulo(baseUrl, cookies, "rota_frequencia", "", "Frequência", "Pendente")
            val planosAula = contarOcorrenciasModulo(baseUrl, cookies, "rota_plano_aula", "", "Plano de Aula", "Pendente")
            val planoCurso = contarOcorrenciasModulo(baseUrl, cookies, "rota_plano_curso", "", "Plano de Curso", "Pendente")
            val peis = contarOcorrenciasModulo(baseUrl, cookies, "rota_pei", "", "PEI", "Pendente")

            val resumo = SgeSummary(
                conteudos = conteudos,
                frequencia = frequencia,
                planosAula = planosAula,
                planoCurso = planoCurso,
                peis = peis,
                detalhes = if (conteudos + frequencia + planosAula + planoCurso + peis == 0)
                    "Diário 100% em dia no SGE 3.0"
                else
                    "Pendências detectadas no SGE 3.0",
                sessaoAtiva = true
            )

            SgeRepository.salvarResumo(appContext, resumo)
            DiarioGlanceWidget().updateAll(appContext)
            Result.success()
        } catch (e: Exception) {
            DiarioGlanceWidget().updateAll(appContext)
            Result.retry()
        }
    }

    private fun contarOcorrenciasModulo(
        baseUrl: String,
        cookies: String,
        prefKey: String,
        defaultPath: String,
        termoModulo: String,
        termoStatus: String
    ): Int {
        val path = SgeRepository.lerSeletorCss(appContext, prefKey, defaultPath)
        val targetUrl = if (path.isBlank()) baseUrl else "$baseUrl$path"
        return try {
            val page = Jsoup.connect(targetUrl)
                .header("Cookie", cookies)
                .timeout(12000)
                .get()
            val badges = page.select(".badge-danger, .label-danger, .text-danger, tr:contains($termoModulo):contains($termoStatus)")
            badges.size
        } catch (_: Exception) {
            0
        }
    }

    companion object {
        fun agendarPeriodico(context: Context) {
            val request = PeriodicWorkRequestBuilder<SgeSyncWorker>(2, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sge_periodic_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun sincronizarAgora(context: Context) {
            val request = OneTimeWorkRequestBuilder<SgeSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

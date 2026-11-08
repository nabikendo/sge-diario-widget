package br.to.seduc.sgediariowidget.worker

import android.content.Context
import android.webkit.CookieManager
import androidx.work.*
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.widget.SgeWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

/** Uma consulta de horário por tarefa evita que uma escola inteira estoure o tempo do Worker. */
class SgeDocentesWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(SgeSessaoStore.contextoConsulta(ctx)) { executar() }
    private suspend fun executar(): Result {
        val geracao = SgeSessaoStore.geracao(ctx)
        val id = inputData.getString("professor_id")
        val ultimo = inputData.getBoolean("ultimo", false)
        val cookiesAtualizados = mutableListOf<String>()
        try {
            val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
            val cookies = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(SgeSyncWorker.BASE) }
            if (cookies.isNullOrBlank()) throw SessaoExpirada()
            val agent = prefs.getString("web_user_agent", null) ?: error("Entre no SGE Web primeiro.")
            val client = SgeReadClient(cookies, agent, prefs.getString("turmas_contexto", null)) { cookiesAtualizados.add(it) }
            val anteriores = SgeDocentesStore.ler(ctx)
            if (id == null) {
                check(withContext(Dispatchers.IO) { withTimeout(60_000L) { client.ehPerfilEscola().also { SgeSessaoStore.salvar(ctx, client.perfilObservado) } } }) {
                    "Ative o perfil Unidade escolar no SGE Web para ler os professores."
                }
                val professores = withContext(Dispatchers.IO) { withTimeout(60_000L) { client.professoresDaEscola() } }
                SgeDocentesStore.salvar(ctx, anteriores.copy(professores = professores, sincronizando = true,
                    inicio = System.currentTimeMillis(), mensagem = "Lendo os vínculos de ${professores.size} professores…"))
                prefs.edit().putInt("docentes_falhas", 0).commit()
                val reqs = professores.mapIndexed { i, p -> OneTimeWorkRequestBuilder<SgeDocentesWorker>()
                    .setConstraints(rede()).setInputData(workDataOf("professor_id" to p.id, "professor_nome" to p.nome,
                        "posicao" to i + 1, "total" to professores.size, "ultimo" to (i == professores.lastIndex))).build() }
                var chain = WorkManager.getInstance(ctx).beginUniqueWork("sge_docentes_horarios", ExistingWorkPolicy.REPLACE, reqs.first())
                reqs.drop(1).forEach { chain = chain.then(it) }; SgeSessaoStore.gravarSeAtual(ctx) { chain.enqueue() }
            } else {
                val a = JSONArray(prefs.getString("turmas_escola", "[]"))
                val turmas = (0 until a.length()).map { a.getJSONObject(it) }.filter { SgeEscopo.permitida(ctx, it.optString("t")) }
                    .map { o -> o.getString("v") to o.getString("t") }
                check(turmas.isNotEmpty()) { "Carregue as turmas da escola antes de atualizar os vínculos." }
                val professor = DocenteRef(id, inputData.getString("professor_nome").orEmpty())
                val leitura = withContext(Dispatchers.IO) { withTimeout(90_000L) { client.analisarVinculosDocente(professor, turmas) } }
                val vinculos = leitura.vinculos
                val p = SgeDocentesStore.ler(ctx); val escopo = turmas.map { it.first }.toSet()
                val falhas = prefs.getInt("docentes_falhas", 0)
                SgeDocentesStore.salvar(ctx, p.copy(
                    vinculos = p.vinculos.filter { it.docente.id != id || it.turmaId !in escopo } + vinculos,
                    diagnosticos = p.diagnosticos.filterNot { it.professorId == id } + leitura.diagnosticos,
                    sincronizando = !ultimo, inicio = if (ultimo) 0L else System.currentTimeMillis(), mensagem = if (ultimo) mensagemFinal(falhas)
                        else "Horários consultados: ${inputData.getInt("posicao", 0)}/${inputData.getInt("total", 0)}"))
            }
            return Result.success()
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            val p = SgeDocentesStore.ler(ctx)
            SgeDocentesStore.salvar(ctx, p.copy(sincronizando = false, mensagem = "A leitura do horário excedeu o tempo disponível. Tente atualizar os vínculos novamente."))
            return Result.failure()
        }
        catch (e: Exception) {
            val p = SgeDocentesStore.ler(ctx)
            val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
            val falhas = prefs.getInt("docentes_falhas", 0) + 1
            prefs.edit().putInt("docentes_falhas", falhas).commit()
            val encerrar = id == null || ultimo || e is SessaoExpirada
            SgeDocentesStore.salvar(ctx, p.copy(sincronizando = !encerrar, inicio = if (encerrar) 0L else System.currentTimeMillis(),
                mensagem = if (encerrar) "${mensagemFinal(falhas)} ${e.message.orEmpty().take(180)}"
                    else "Um horário não pôde ser lido; os vínculos anteriores são preservados."))
            return if (e is SessaoExpirada || id == null) Result.failure() else Result.success()
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                if (SgeSessaoStore.geracao(ctx) == geracao) {
                    cookiesAtualizados.forEach { CookieManager.getInstance().setCookie(SgeSyncWorker.BASE, it) }; CookieManager.getInstance().flush()
                }
                SgeWidgets.atualizar(ctx)
            }
        }
    }
    private fun mensagemFinal(falhas: Int) = if (falhas == 0) "Vínculos do horário atualizados."
        else "Vínculos atualizados com $falhas horário(s) não verificado(s). Vínculos anteriores são mantidos."
    companion object {
        private fun rede() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun atualizar(context: Context) {
            val p = SgeDocentesStore.ler(context)
            SgeDocentesStore.salvar(context, p.copy(sincronizando = true, inicio = System.currentTimeMillis(), mensagem = "Lendo a lista de professores…"))
            WorkManager.getInstance(context).cancelUniqueWork("sge_docentes_horarios")
            WorkManager.getInstance(context).enqueueUniqueWork("sge_docentes_lista", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SgeDocentesWorker>().setConstraints(rede()).build())
        }
    }
}

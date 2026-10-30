package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ResultadoModulo(
    val quantidade: Int? = null,
    val detalhe: String = "Não verificado",
    val aguardando: Int = 0,
    val aprovados: Int = 0,
    val coberturaCompleta: Boolean = false
) {
    fun resumo(): String = quantidade?.let { "$it${if (coberturaCompleta) "" else "*"}" } ?: "—"
    fun json() = JSONObject().put("quantidade", quantidade ?: JSONObject.NULL)
        .put("detalhe", detalhe).put("aguardando", aguardando).put("aprovados", aprovados)
        .put("completa", coberturaCompleta)
    companion object {
        fun ler(json: JSONObject) = ResultadoModulo(
            if (json.isNull("quantidade")) null else json.getInt("quantidade"),
            json.optString("detalhe", "Não verificado"), json.optInt("aguardando"),
            json.optInt("aprovados"), json.optBoolean("completa")
        )
    }
}

data class ResultadoTurma(val turma: TurmaMonitorada, val modulos: List<ResultadoModulo>, val atualizado: String) {
    fun json() = JSONObject().put("id", turma.id).put("nome", turma.nome).put("escola", turma.escola)
        .put("disciplina", turma.disciplina).put("atualizado", atualizado)
        .put("modulos", JSONArray().also { a -> modulos.forEach { a.put(it.json()) } })
    companion object {
        fun ler(j: JSONObject): ResultadoTurma {
            val a = j.getJSONArray("modulos")
            return ResultadoTurma(TurmaMonitorada(j.getString("id"), j.getString("nome"),
                j.getString("escola"), j.getString("disciplina")),
                (0 until a.length()).map { ResultadoModulo.ler(a.getJSONObject(it)) }, j.optString("atualizado"))
        }
    }
}

data class PainelSge(val turmas: List<ResultadoTurma> = emptyList(),
    val mensagem: String = "Entre no SGE para consultar o horário e as turmas.",
    val sincronizando: Boolean = false, val sessaoAtiva: Boolean = false,
    val inicioConsulta: Long = 0L)

object SgePainelStore {
    const val PREFS_NAME = "sge_diario_prefs"
    val NOMES_MODULOS = listOf("Conteúdos (dias)", "Frequência (dias)", "Planos de Aula", "Plano de Curso", "PEIs")
    fun salvar(context: Context, painel: PainelSge) {
        val j = JSONObject().put("mensagem", painel.mensagem).put("sincronizando", painel.sincronizando)
            .put("inicioConsulta", painel.inicioConsulta)
            .put("sessaoAtiva", painel.sessaoAtiva).put("turmas", JSONArray().also { a ->
                painel.turmas.forEach { a.put(it.json()) }
            })
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("painel_v2", j.toString()).apply()
    }
    fun ler(context: Context): PainelSge = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("painel_v2", null)
            ?: return@runCatching PainelSge()
        val j = JSONObject(raw)
        val a = j.getJSONArray("turmas")
        val carregado = PainelSge((0 until a.length()).map { ResultadoTurma.ler(a.getJSONObject(it)) },
            j.optString("mensagem"), j.optBoolean("sincronizando"), j.optBoolean("sessaoAtiva"),
            j.optLong("inicioConsulta"))
        if (carregado.sincronizando && (carregado.inicioConsulta <= 0L || System.currentTimeMillis() - carregado.inicioConsulta > 5 * 60 * 1000L)) {
            carregado.copy(mensagem = "A sincronização anterior foi interrompida. Toque em atualizar para tentar novamente.",
                sincronizando = false)
        } else carregado
    }.getOrElse { PainelSge(mensagem = "Dados locais não puderam ser lidos. Sincronize novamente.") }
}

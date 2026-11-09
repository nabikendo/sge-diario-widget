package br.to.seduc.sgediariowidget.widget

import android.content.Context
import br.to.seduc.sgediariowidget.data.*
import org.json.JSONArray
import org.json.JSONObject

enum class TipoWidget(val titulo: String, val descricao: String, val tamanho: String) {
    RESUMO("Resumo do diário", "Turmas com pendências e dados a conferir em um cartão pequeno.", "2 × 2"),
    TURMAS("Minhas turmas", "Cartões por turma, com rolagem vertical e acesso aos detalhes.", "4 × 3"),
    COMPONENTES("Por componente", "Uma visão de Química, Física ou outro componente escolhido.", "4 × 3"),
    COORDENACAO("Coordenação", "Pendências da escola agrupadas por turma ou componente.", "4 × 3"),
    PROFESSORES("Por professor", "Vínculos docentes e pendências da turma/componente, sem duplicação.", "4 × 3"),
    FOCO("Cartão em foco", "Um recorte de cada vez, com botões para alternar. Ideal para uma área pequena.", "4 × 2")
}

data class ConfigWidget(
    val fonte: FontePainel = FontePainel.PESSOAL,
    val agrupamento: Agrupamento = Agrupamento.TURMA,
    val grupo: String = "",
    val soAtencao: Boolean = false,
    val ocultarNomes: Boolean = false
)

object SgeWidgetStore {
    private fun prefs(context: Context) = context.getSharedPreferences("sge_widgets_v2", Context.MODE_PRIVATE)
    fun padrao(tipo: TipoWidget) = ConfigWidget(
        fonte = if (tipo == TipoWidget.COORDENACAO || tipo == TipoWidget.PROFESSORES) FontePainel.COORDENACAO else FontePainel.PESSOAL,
        agrupamento = when(tipo) { TipoWidget.COMPONENTES -> Agrupamento.COMPONENTE; TipoWidget.PROFESSORES -> Agrupamento.PROFESSOR; else -> Agrupamento.TURMA })
    fun ler(context: Context, id: Int, tipo: TipoWidget): ConfigWidget = runCatching {
        val raw = prefs(context).getString("config_$id", null) ?: return@runCatching padrao(tipo)
        val j = JSONObject(raw)
        ConfigWidget(FontePainel.valueOf(j.getString("fonte")), Agrupamento.valueOf(j.getString("por")),
            j.optString("grupo"), j.optBoolean("atencao"), j.optBoolean("ocultar"))
    }.getOrElse { padrao(tipo) }
    fun salvar(context: Context, id: Int, c: ConfigWidget) {
        check(prefs(context).edit().putString("config_$id", JSONObject().put("fonte", c.fonte.name)
            .put("por", c.agrupamento.name).put("grupo", c.grupo).put("atencao", c.soAtencao)
            .put("ocultar", c.ocultarNomes).toString()).remove("foco_$id").commit())
    }
    fun foco(context: Context, id: Int) = prefs(context).getString("foco_$id", "").orEmpty()
    fun salvarFoco(context: Context, id: Int, chave: String) { prefs(context).edit().putString("foco_$id", chave).commit() }
    fun remover(context: Context, ids: IntArray) { prefs(context).edit().apply { ids.forEach { remove("config_$it"); remove("foco_$it") } }.apply() }

    fun conhecidas(context: Context): List<Pair<String, String>> = runCatching {
        val a = JSONArray(context.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getString("turmas_escola", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.filter { SgeEscopo.permitida(context, it.optString("t")) }
            .map { o -> o.getString("v") to o.getString("t") }.distinctBy { it.first }
    }.getOrDefault(emptyList())

    fun grupos(
        context: Context,
        config: ConfigWidget,
        resultados: List<ResultadoTurma> = if (config.fonte == FontePainel.PESSOAL) SgePainelStore.ler(context).turmas
            else SgeCoordStore.ler(context).turmas.flatMap { it.disciplinas }
    ): List<GrupoPendencias> {
        val docentes = SgeDocentesStore.ler(context)
        val gs = SgeDashboard.agrupar(resultados, config.agrupamento, docentes.vinculos, docentes.locais)
        return gs.filter { (config.grupo.isBlank() || it.chave == config.grupo) && (!config.soAtencao || it.estado != EstadoGrupo.EM_DIA) }
    }
}

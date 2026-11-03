package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Organização local: série e turno nunca são deduzidos do código da turma. */
data class DadosTurma(val escola: String = "", val serie: String = "", val turno: String = "")
data class FiltroAvancado(val escola: String = "", val serie: String = "", val turno: String = "", val componente: String = "", val professor: String = "", val modulo: Int = -1, val estado: Int = 0, val qualificador: String = "") {
    val ativo get() = listOf(escola, serie, turno, componente, professor).any { it.isNotEmpty() } || modulo >= 0 || estado > 0 || qualificador.isNotBlank()
}
object SgeOrganizacao {
    const val CHAVE = "organizacao_turmas_v1"
    fun ler(texto: String): Map<String, DadosTurma> {
        val j = JSONObject(texto)
        return j.keys().asSequence().associateWith { id -> j.getJSONObject(id).let { DadosTurma(it.getString("escola"), it.getString("serie"), it.getString("turno")) } }
    }
    fun json(dados: Map<String, DadosTurma>): String = JSONObject().also { j -> dados.forEach { (id, d) -> j.put(id, JSONObject().put("escola", d.escola).put("serie", d.serie).put("turno", d.turno)) } }.toString()
    fun ler(ctx: Context) = runCatching { ler(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, 0).getString(CHAVE, "{}").orEmpty()) }.getOrDefault(emptyMap())
    fun salvar(ctx: Context, dados: Map<String, DadosTurma>) { ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, 0).edit().putString(CHAVE, json(dados)).apply() }
    fun dados(r: ResultadoTurma, organizacao: Map<String, DadosTurma>): DadosTurma = (organizacao[r.turma.id] ?: DadosTurma()).let { it.copy(escola = it.escola.ifBlank { r.turma.escola }) }
    fun professores(r: ResultadoTurma, docentes: PainelDocentes): List<DocenteRef> = (docentes.locais[chaveResultado(r)] ?: docentes.vinculos.filter { chaveVinculo(it.turmaId, it.componente) == chaveResultado(r) }.map { it.docente }).distinctBy { it.id }
    fun filtrar(rs: List<ResultadoTurma>, f: FiltroAvancado, org: Map<String, DadosTurma>, docentes: PainelDocentes): List<ResultadoTurma> = rs.distinctBy(::chaveResultado).filter { r ->
        val d = dados(r, org)
        fun igual(filtro: String, valor: String) = filtro.isEmpty() || normalizarSge(filtro) == normalizarSge(valor)
        val ms = if (f.modulo in 0..4) listOf(r.modulos.getOrNull(f.modulo) ?: ResultadoModulo()) else (0..4).map { r.modulos.getOrNull(it) ?: ResultadoModulo() }
        val situacaoOk = when (f.qualificador) {
            "conferir" -> ms.any { it.quantidade == null || !it.coberturaCompleta }
            "sem_vinculo" -> professores(r, docentes).isEmpty() || docentes.diagnosticos.any { diagnostico ->
                diagnostico.candidatos.any { it.first == r.turma.id } &&
                    (diagnostico.componente.isBlank() || normalizarSge(diagnostico.componente) == normalizarSge(r.turma.disciplina))
            }
            "dados_antigos" -> (SgeIdade.maisAntigaPorModulo(listOf(r), LocalDateTime.now()) ?: -1L) >= 24L
            else -> true
        }
        igual(f.escola, d.escola) && igual(f.serie, d.serie) && igual(f.turno, d.turno) && igual(f.componente, r.turma.disciplina) &&
            (f.professor.isEmpty() || professores(r, docentes).any { it.id == f.professor }) && when (f.estado) {
                1 -> ms.any { (it.quantidade ?: 0) > 0 }
                2 -> ms.any { it.quantidade == null || !it.coberturaCompleta }
                3 -> ms.all { it.quantidade == 0 && it.coberturaCompleta }
                else -> true
            } && situacaoOk
    }
    /** O lote entrega uma cópia por docente identificado. Não somar esses relatórios como total da escola. */
    fun porProfessor(rs: List<ResultadoTurma>, docentes: PainelDocentes, professor: String = ""): List<GrupoPendencias> {
        val pares = rs.distinctBy(::chaveResultado).flatMap { r -> professores(r, docentes).filter { professor.isEmpty() || it.id == professor }.map { it to r } }
        return pares.groupBy { it.first.id }.map { (id, ps) ->
            val resultados = ps.map { it.second }.distinctBy(::chaveResultado)
            GrupoPendencias("individual:$id", ps.first().first.nome,
                "${resultados.map { it.turma.id }.distinct().size} turma(s) · relatório individual", resultados, resultados.map { it.turma.id }.toSet(), setOf(id),
                resultados.any { professores(it, docentes).size > 1 }, resultados.any { docentes.locais.containsKey(chaveResultado(it)) })
        }.sortedBy { normalizarSge(it.titulo) }
    }
}

object SgeFiltrosStore {
    val chaves = setOf("filtro_pessoal_v1", "filtro_coord_v1")
    private fun chave(f: FontePainel) = if(f == FontePainel.PESSOAL) "filtro_pessoal_v1" else "filtro_coord_v1"
    fun json(f: FiltroAvancado) = JSONObject().put("escola",f.escola).put("serie",f.serie).put("turno",f.turno).put("componente",f.componente).put("professor",f.professor).put("modulo",f.modulo).put("estado",f.estado).put("qualificador",f.qualificador).toString()
    fun ler(raw: String): FiltroAvancado = JSONObject(raw).let { j ->
        FiltroAvancado(j.optString("escola"),j.optString("serie"),j.optString("turno"),j.optString("componente"),j.optString("professor"),j.optInt("modulo", -1),j.optInt("estado"),j.optString("qualificador")).also { require(it.modulo in -1..4 && it.estado in 0..3 && it.qualificador in setOf("", "conferir", "sem_vinculo", "dados_antigos")) }
    }
    fun ler(ctx: Context): Map<FontePainel,FiltroAvancado> = FontePainel.values().associateWith { f->runCatching { ler(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME,0).getString(chave(f),json(FiltroAvancado()))!!) }.getOrDefault(FiltroAvancado()) }
    fun salvar(ctx: Context, fonte: FontePainel, f: FiltroAvancado) { ctx.getSharedPreferences(SgePainelStore.PREFS_NAME,0).edit().putString(chave(fonte),json(f)).apply() }
}

data class FiltroSalvo(val id: String, val nome: String, val filtro: FiltroAvancado)
object SgeFiltrosSalvosStore {
    const val CHAVE = "filtros_salvos_v1"
    fun json(filtros: Map<FontePainel, List<FiltroSalvo>>) = JSONObject().also { root ->
        FontePainel.values().forEach { fonte ->
            val itens = JSONArray()
            filtros[fonte].orEmpty().forEach { f ->
                itens.put(JSONObject().put("id", f.id).put("nome", f.nome).put("filtro", JSONObject(SgeFiltrosStore.json(f.filtro))))
            }
            root.put(fonte.name, itens)
        }
    }.toString()
    fun ler(raw: String): Map<FontePainel, List<FiltroSalvo>> {
        val root = JSONObject(raw)
        return FontePainel.values().associateWith { fonte ->
            val a = root.optJSONArray(fonte.name) ?: JSONArray()
            (0 until a.length()).mapNotNull { i -> runCatching {
                val o = a.getJSONObject(i)
                FiltroSalvo(o.getString("id"), o.getString("nome"), SgeFiltrosStore.ler(o.getJSONObject("filtro").toString()))
            }.getOrNull() }
        }
    }
    fun ler(ctx: Context) = runCatching { ler(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getString(CHAVE, "{}")!!) }
        .getOrDefault(FontePainel.values().associateWith { emptyList() })
    fun salvar(ctx: Context, filtros: Map<FontePainel, List<FiltroSalvo>>) {
        ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putString(CHAVE, json(filtros)).apply()
    }
}

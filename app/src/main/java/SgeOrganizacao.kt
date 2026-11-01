package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONObject

/** Organização local: série e turno nunca são deduzidos do código da turma. */
data class DadosTurma(val escola: String = "", val serie: String = "", val turno: String = "")
data class FiltroAvancado(val escola: String = "", val serie: String = "", val turno: String = "", val componente: String = "", val professor: String = "", val modulo: Int = -1, val estado: Int = 0) {
    val ativo get() = listOf(escola, serie, turno, componente, professor).any { it.isNotEmpty() } || modulo >= 0 || estado > 0
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
        igual(f.escola, d.escola) && igual(f.serie, d.serie) && igual(f.turno, d.turno) && igual(f.componente, r.turma.disciplina) &&
            (f.professor.isEmpty() || professores(r, docentes).any { it.id == f.professor }) && when (f.estado) {
                1 -> ms.any { (it.quantidade ?: 0) > 0 }
                2 -> ms.any { it.quantidade == null || !it.coberturaCompleta }
                3 -> ms.all { it.quantidade == 0 && it.coberturaCompleta }
                else -> true
            }
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

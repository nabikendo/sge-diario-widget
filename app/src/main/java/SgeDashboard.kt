package br.to.seduc.sgediariowidget.data

import java.text.Normalizer

/** Os resultados continuam sendo da turma/componente, mesmo quando agrupados pelo horário docente. */
data class DocenteRef(val id: String, val nome: String)
data class VinculoDocente(val turmaId: String, val componente: String, val docente: DocenteRef)
enum class Agrupamento(val titulo: String) { TURMA("Turmas"), COMPONENTE("Componentes"), PROFESSOR("Professores") }
enum class FontePainel { PESSOAL, COORDENACAO }
enum class EstadoGrupo { PENDENTE, CONFERIR, EM_DIA, SEM_DADOS }

fun normalizarSge(texto: String): String = Normalizer.normalize(texto, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('\u00a0', ' ').trim().replace(Regex("\\s+"), " ").lowercase()
fun chaveResultado(r: ResultadoTurma) = chaveVinculo(r.turma.id, r.turma.disciplina)
fun chaveVinculo(turmaId: String, componente: String) = "$turmaId|${normalizarSge(componente)}"

data class ResumoModulo(
    val indice: Int,
    val quantidade: Int?,
    val consultas: Int,
    val verificadas: Int,
    val completa: Boolean,
    val aguardando: Int,
    val aprovados: Int
) {
    val aConferir get() = consultas - verificadas
    val parcial get() = quantidade != null && !completa
    fun valor(): String = quantidade?.let { "$it${if (completa) "" else "*"}" } ?: "—"
    fun unidade(): String = if (indice < 2) { if (quantidade == 1) "dia" else "dias" }
        else if (quantidade == 1) "documento" else "documentos"
}

data class GrupoPendencias(
    val chave: String,
    val titulo: String,
    val subtitulo: String,
    val resultados: List<ResultadoTurma>,
    val turmaIds: Set<String>,
    val professorIds: Set<String> = emptySet(),
    val compartilhado: Boolean = false,
    val vinculoLocal: Boolean = false
) {
    val modulos get() = SgeDashboard.resumir(resultados)
    val exigeConferencia get() = resultados.isEmpty() || modulos.any { !it.completa }
    val estado get() = when {
        resultados.isEmpty() -> EstadoGrupo.SEM_DADOS
        modulos.any { (it.quantidade ?: 0) > 0 } -> EstadoGrupo.PENDENTE
        modulos.any { !it.completa } -> EstadoGrupo.CONFERIR
        else -> EstadoGrupo.EM_DIA
    }
}

object SgeDashboard {
    fun progresso(anteriores: List<ResultadoTurma>, concluidos: List<ResultadoTurma>, escopo: Set<String>): List<ResultadoTurma> {
        val novas = concluidos.map(::chaveResultado).toSet()
        return (anteriores.filter { chaveResultado(it) in escopo && chaveResultado(it) !in novas } + concluidos).distinctBy(::chaveResultado)
    }

    val nomes = listOf("Conteúdos", "Frequência", "Plano de aula", "Plano de curso", "PEI")
    val descricoes = listOf(
        "Dias com conteúdo ainda sem registro até a data da consulta. Várias aulas no mesmo dia e componente contam como um dia.",
        "Dias cuja frequência ainda não está concluída. Este número não é a quantidade de alunos faltosos.",
        "Documentos de plano de aula ausentes, não enviados ou reprovados nos períodos que já começaram.",
        "Documentos de plano de curso ausentes, não enviados ou reprovados nos períodos que já começaram.",
        "Plano de Ensino Individualizado: documentos ausentes, não enviados ou reprovados para os estudantes elegíveis."
    )
    fun resumir(resultados: List<ResultadoTurma>): List<ResumoModulo> {
        val unicos = resultados.distinctBy(::chaveResultado)
        return (0..4).map { i ->
            val dados = unicos.map { it.modulos.getOrNull(i) ?: ResultadoModulo() }
            val conhecidos = dados.mapNotNull { it.quantidade }
            ResumoModulo(i, conhecidos.takeIf { it.isNotEmpty() }?.sum(), dados.size,
                dados.count { it.quantidade != null && it.coberturaCompleta },
                dados.isNotEmpty() && dados.all { it.quantidade != null && it.coberturaCompleta },
                dados.sumOf { it.aguardando }, dados.sumOf { it.aprovados })
        }
    }

    fun agrupar(
        resultados: List<ResultadoTurma>,
        por: Agrupamento,
        vinculos: List<VinculoDocente> = emptyList(),
        locais: Map<String, List<DocenteRef>> = emptyMap()
    ): List<GrupoPendencias> {
        val unicos = resultados.distinctBy(::chaveResultado)
        val porVinculo = vinculos.groupBy { chaveVinculo(it.turmaId, it.componente) }
        fun professores(r: ResultadoTurma): List<DocenteRef> =
            (locais[chaveResultado(r)] ?: porVinculo[chaveResultado(r)].orEmpty().map { it.docente })
                .distinctBy { it.id }.sortedBy { it.id }
        val grupos = unicos.groupBy { r -> when (por) {
            Agrupamento.TURMA -> "turma:${r.turma.id}"
            Agrupamento.COMPONENTE -> "componente:${normalizarSge(r.turma.disciplina)}"
            Agrupamento.PROFESSOR -> professores(r).let { p ->
                if (p.isEmpty()) "professor:desconhecido" else "professor:${p.joinToString("+") { it.id }}"
            }
        } }.map { (key, itens) ->
            val docentes = if (por == Agrupamento.PROFESSOR) professores(itens.first()) else emptyList()
            val turmas = itens.map { it.turma.id }.distinct()
            val componentes = itens.map { it.turma.disciplina }.distinct()
            val titulo = when (por) {
                Agrupamento.TURMA -> itens.first().turma.nome.substringBefore('/')
                Agrupamento.COMPONENTE -> componentes.first()
                Agrupamento.PROFESSOR -> when {
                    docentes.isEmpty() -> "Professor não identificado"
                    docentes.size > 1 -> "Docência compartilhada"
                    else -> docentes.single().nome
                }
            }
            val subtitulo = when (por) {
                Agrupamento.TURMA -> componentes.joinToString(" · ")
                Agrupamento.COMPONENTE -> "${turmas.size} turma(s)"
                Agrupamento.PROFESSOR -> if (docentes.size > 1) docentes.joinToString(" · ") { it.nome }
                    else "${turmas.size} turma(s) · ${componentes.size} componente(s)"
            }
            GrupoPendencias(key, titulo, subtitulo, itens, turmas.toSet(), docentes.map { it.id }.toSet(),
                docentes.size > 1, itens.any { locais.containsKey(chaveResultado(it)) })
        }
        return grupos.sortedWith(compareBy<GrupoPendencias>({ when (it.estado) {
            EstadoGrupo.PENDENTE -> 0; EstadoGrupo.CONFERIR -> 1; EstadoGrupo.SEM_DADOS -> 2; EstadoGrupo.EM_DIA -> 3
        } }, { normalizarSge(it.titulo) }))
    }
}

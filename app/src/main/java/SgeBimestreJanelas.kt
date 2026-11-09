package br.to.seduc.sgediariowidget.data

import java.time.LocalDate

/**
 * Consulta a sequência completa dos quatro bimestres. Não usa o sucesso de
 * um único parâmetro como prova de índice zero ou um.
 * Falha de modo fechado se os quatro intervalos forem inválidos ou ambíguos.
 */
object SgeBimestreJanelas {
    fun carregar(buscar: (String) -> Pair<LocalDate, LocalDate>?): Map<Int, Pair<LocalDate, LocalDate>> {
        val candidatos = listOf(
            (0..3).map { it.toString() },
            (1..4).map { it.toString() }
        )
        val mapas = candidatos.mapNotNull { parametros ->
            val intervalos = parametros.map { buscar(it) }
            if (intervalos.any { it == null }) return@mapNotNull null
            val validos = intervalos.filterNotNull()
            val ordenados = validos.zipWithNext().all { (anterior, proximo) ->
                anterior.first.isBefore(proximo.first) && anterior.second.isBefore(proximo.first)
            }
            if (!ordenados) null else (1..4).zip(validos).toMap()
        }.distinct()
        return mapas.singleOrNull()
            ?: error("Bimestres não identificados de forma inequívoca no SGE.")
    }
}

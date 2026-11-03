package br.to.seduc.sgediariowidget.data

import java.time.LocalDate

/** Mapa de dias da semana para o cabeçalho de detalhes, montado só com o que a consulta já trouxe. */
object SgeMapaDias {
    enum class Situacao { PENDENTE, PARCIAL, REGULAR }
    data class Dia(val data: LocalDate, val situacao: Situacao)

    private val ate = Regex("""até (\d{2})/(\d{2})/(\d{4})""")
    private val curta = Regex("""(\d{2})/(\d{2})""")

    /** Lê o detalhe de Conteúdos/Frequência ("... até dd/MM/aaaa: dd/MM, dd/MM") e devolve a data de referência e os dias pendentes. */
    fun diasPendentes(detalhe: String): Pair<LocalDate?, List<LocalDate>> {
        val m = ate.find(detalhe) ?: return null to emptyList()
        val ref = runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull() ?: return null to emptyList()
        val dias = curta.findAll(detalhe.substring(m.range.last + 1)).mapNotNull { x ->
            runCatching {
                val mes = x.groupValues[2].toInt()
                LocalDate.of(if (mes > ref.monthValue) ref.year - 1 else ref.year, mes, x.groupValues[1].toInt())
            }.getOrNull()
        }.toList()
        return ref to dias
    }

    fun ultimosUteis(ref: LocalDate, n: Int = 5): List<LocalDate> {
        val lista = mutableListOf<LocalDate>(); var d = ref
        while (lista.size < n && d.isAfter(ref.minusDays(40))) { if (SgeCalendario.util(d)) lista += d; d = d.minusDays(1) }
        return lista.sorted()
    }

    /** Vermelho: dia pendente no mapa do SGE. Âmbar: cobertura parcial ou módulo não verificado. Verde: sem pendência com cobertura completa. */
    fun montar(resultados: List<ResultadoTurma>, hoje: LocalDate): List<Dia> {
        val mods = resultados.flatMap { r -> listOf(0, 1).mapNotNull { r.modulos.getOrNull(it) } }
        if (mods.isEmpty()) return emptyList()
        val lidos = mods.map { diasPendentes(it.detalhe) }
        val pendentes = lidos.flatMap { it.second }.toSet()
        val parcial = mods.any { it.quantidade == null || !it.coberturaCompleta }
        val ref = lidos.mapNotNull { it.first }.maxOrNull() ?: hoje
        return ultimosUteis(ref).map { d -> Dia(d, when { d in pendentes -> Situacao.PENDENTE; parcial -> Situacao.PARCIAL; else -> Situacao.REGULAR }) }
    }
}

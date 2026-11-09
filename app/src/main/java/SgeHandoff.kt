package br.to.seduc.sgediariowidget

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Só navegação: credenciais, nomes e dados escolares ficam no aparelho de origem. */
data class ContextoHandoff(val aba: Int, val modulo: Int = -1, val semana: LocalDate? = null)

class PedidoHandoff(val contexto: ContextoHandoff)

internal fun lerSemanaHandoff(valor: String?): LocalDate? {
    if (valor == null || valor.length != 10) return null
    return try {
        LocalDate.parse(valor).takeIf { it.year in 2000..2100 }
    } catch (_: DateTimeParseException) {
        null
    }
}

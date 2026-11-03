package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SgeMapaDiasTest {
    private val hoje = LocalDate.of(2026, 11, 3)
    private fun mod(q: Int, detalhe: String, completa: Boolean = true) = ResultadoModulo(q, detalhe, coberturaCompleta = completa)
    private fun r(c: ResultadoModulo, f: ResultadoModulo) = ResultadoTurma(TurmaMonitorada("1", "13.01/EM", "Escola", "Química"), listOf(c, f, mod(0, ""), mod(0, ""), mod(0, "")), "03/11 00:15")

    @Test fun leDiasPendentesDoDetalheComAnoDaReferencia() {
        val (ref, dias) = SgeMapaDias.diasPendentes("Dias pendentes no mapa do SGE até 03/11/2026: 29/10, 30/10")
        assertEquals(LocalDate.of(2026, 11, 3), ref); assertEquals(listOf(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30)), dias)
    }
    @Test fun semListaOuSemReferenciaNaoInventaDias() {
        assertTrue(SgeMapaDias.diasPendentes("Dias pendentes no mapa do SGE até 03/11/2026.").second.isEmpty())
        assertNull(SgeMapaDias.diasPendentes("Não verificado: tempo esgotado").first)
    }
    @Test fun viradaDeAnoUsaOAnoAnterior() {
        assertEquals(LocalDate.of(2025, 12, 30), SgeMapaDias.diasPendentes("até 05/01/2026: 30/12").second.single())
    }
    @Test fun ultimosCincoDiasUteisPulamFimDeSemana() {
        val l = SgeMapaDias.ultimosUteis(LocalDate.of(2026, 11, 3))
        assertEquals(5, l.size); assertTrue(l.all { SgeCalendario.util(it) }); assertEquals(LocalDate.of(2026, 11, 3), l.last())
    }
    @Test fun mapaMarcaPendenteParcialERegular() {
        val ok = SgeMapaDias.montar(listOf(r(mod(1, "Dias pendentes no mapa do SGE até 03/11/2026: 03/11"), mod(0, "Dias do filtro oficial até 03/11/2026."))), hoje)
        assertEquals(SgeMapaDias.Situacao.PENDENTE, ok.last().situacao); assertTrue(ok.dropLast(1).all { it.situacao == SgeMapaDias.Situacao.REGULAR })
        val parcial = SgeMapaDias.montar(listOf(r(mod(0, "até 03/11/2026.", false), mod(0, "até 03/11/2026."))), hoje)
        assertTrue(parcial.all { it.situacao == SgeMapaDias.Situacao.PARCIAL })
        assertTrue(SgeMapaDias.montar(emptyList(), hoje).isEmpty())
    }
}

package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SgeCalendarioOficialTest {
    private val texto = """
        Fevereiro de 2026 - 18 dias letivos
        Julho de 2026 - 0 dias letivos
        LEGENDAS
        EVENTOS
        16/02 - Recesso
        17/02 - Carnaval
        20/11 - Dia Nacional de Zumbi e da Consciência Negra
        DIVISÃO DE BIMESTRE
        Início - Fim 1º Bimestre: 02/02/2026 à 11/04/2026
        Dias Letivos 1º Bimestre 47
        Início - Fim 2º Bimestre: 13/04/2026 à 30/06/2026
        Dias Letivos 2º Bimestre 53
        Total Geral 200
    """.trimIndent()

    @Test fun leBimestresEFeriados() {
        val c = SgeCalendarioOficial.ler(texto)
        assertEquals(2, c.bimestres.size)
        assertEquals(LocalDate.of(2026, 2, 2), c.bimestres[0].inicio)
        assertEquals(47, c.bimestres[0].diasLetivos)
        assertEquals(3, c.feriados.size)
        assertEquals("Carnaval", c.feriados[1].nome)
        assertEquals(LocalDate.of(2026, 2, 17), c.feriados[1].data)
    }

    @Test fun leDiasLetivosPorMes() {
        val c = SgeCalendarioOficial.ler(texto)
        assertEquals(18, c.diasLetivosPorMes[2])
        assertEquals(0, c.diasLetivosPorMes[7])
    }

    @Test fun feriadoNaoEhDiaLetivo() {
        val c = SgeCalendarioOficial.ler(texto)
        assertFalse(c.ehDiaLetivo(LocalDate.of(2026, 2, 17)))
        assertTrue(c.ehDiaLetivo(LocalDate.of(2026, 2, 3)))
        assertFalse(c.ehDiaLetivo(LocalDate.of(2026, 2, 7)))
    }
}

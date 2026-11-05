package br.to.seduc.sgediariowidget.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SgeAgendaNoturnaTest {
    private val fuso = ZoneId.of("America/Araguaina")

    @Test fun tentativaAntesDasOitoFicaParaAMesmaManha() {
        val agora = ZonedDateTime.of(2026, 11, 5, 7, 20, 0, 0, fuso)
        assertEquals(40 * 60_000L, SgeAgendaNoturna.atraso(agora, 8))
    }

    @Test fun tentativaDepoisDasOitoVaiParaAManhaSeguinte() {
        val agora = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, fuso)
        val alvo = agora.plusNanos(SgeAgendaNoturna.atraso(agora, 8) * 1_000_000L)
        assertEquals(ZonedDateTime.of(2026, 11, 6, 8, 0, 0, 0, fuso), alvo)
    }
}

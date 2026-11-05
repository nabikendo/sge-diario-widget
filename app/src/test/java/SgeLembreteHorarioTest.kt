package br.to.seduc.sgediariowidget.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SgeLembreteHorarioTest {
    @Test fun horarioAindaHojeUsaOProximoInstanteEscolhido() {
        val agora = LocalDateTime.of(2026, 11, 5, 7, 30)
        assertEquals(LocalDateTime.of(2026, 11, 5, 8, 15), LembreteHorario.proximaExecucao(agora, 8 * 60 + 15))
    }

    @Test fun horarioQueJaPassouVaiParaOProximoDia() {
        val agora = LocalDateTime.of(2026, 11, 5, 17, 0)
        assertEquals(LocalDateTime.of(2026, 11, 6, 17, 0), LembreteHorario.proximaExecucao(agora, 17 * 60))
    }

    @Test fun minutosSaoLimitadosAoDiaLocal() {
        val agora = LocalDateTime.of(2026, 11, 5, 23, 50)
        assertEquals(LocalDateTime.of(2026, 11, 5, 23, 59), LembreteHorario.proximaExecucao(agora, 24 * 60))
    }
}

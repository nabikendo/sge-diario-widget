package br.to.seduc.sgediariowidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SgeExtrasTest {
    private fun turma(id: String = "1", disc: String = "Química", vararg m: ResultadoModulo) =
        ResultadoTurma(TurmaMonitorada(id, "13.0$id/EM", "Escola", disc), m.toList(), "01/11 08:00")
    private fun mod(q: Int?, completa: Boolean = true, datas: DatasModulo? = null) =
        ResultadoModulo(quantidade = q, coberturaCompleta = completa, datas = datas)

    @Test fun turmaZeradaSoComCoberturaCompleta() {
        val antes = listOf(turma(m = arrayOf(mod(3), mod(0))))
        assertEquals(listOf("13.01 · Química"), SgeAlertas.turmasZeradas(antes, listOf(turma(m = arrayOf(mod(0), mod(0))))))
        assertTrue(SgeAlertas.turmasZeradas(antes, listOf(turma(m = arrayOf(mod(0, completa = false), mod(0))))).isEmpty())
        assertTrue(SgeAlertas.turmasZeradas(antes, listOf(turma(m = arrayOf(mod(null), mod(0))))).isEmpty())
    }

    @Test fun pendenciaAntigaUsaDataMaisAntiga() {
        val hoje = LocalDate.of(2026, 11, 17)
        val datas = DatasModulo(null, hoje, pendentes = listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 10)))
        val rs = listOf(turma(m = arrayOf(mod(2, datas = datas), mod(0))))
        assertEquals(1, SgeAlertas.pendenciasAntigas(rs, hoje, 7).size)
        assertTrue(SgeAlertas.pendenciasAntigas(rs, hoje, 30).isEmpty())
    }

    @Test fun bimestreComecaUmaSemanaDepois() {
        assertEquals(2, SgeAlertas.bimestreComecandoEm(LocalDate.of(2026, 4, 13)))
        assertNull(SgeAlertas.bimestreComecandoEm(LocalDate.of(2026, 4, 14)))
    }

    @Test fun motivoDeFalhaEmLinguagemSimples() {
        assertEquals("A conexão com o SGE foi interrompida", SgeFalhas.motivo("Não verificado: Connection reset"))
        assertEquals("O SGE demorou demais para responder", SgeFalhas.motivo("Não verificado: tempo esgotado"))
    }
}

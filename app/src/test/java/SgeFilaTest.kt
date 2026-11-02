package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test

class SgeFilaTest {
    private val agora = 1_800_000_000_000L
    private fun mod(q: Int?, completa: Boolean = true) = ResultadoModulo(q, "", coberturaCompleta = completa)
    private fun turma(id: String, q: Int? = 0, ms: Long = 0L, erro: String? = null) = CoordTurma(id, "$id/EM",
        listOf(ResultadoTurma(TurmaMonitorada(id, "$id/EM", "Escola", "Química"), listOf(mod(q), mod(0), mod(0), mod(0), mod(0)), "01/11 10:00")), erro, "", ms)

    @Test fun pendenciasPrimeiroDepoisNuncaVerificadasDepoisEmDia() {
        val ts = listOf(turma("a", 0, agora - 9 * 3600_000L), turma("b", 3, agora - 9 * 3600_000L))
        val p = SgeFila.planejar(listOf("a", "c", "b"), ts, agora, pular = true)
        assertEquals(listOf("b", "c", "a"), p.ordem)
    }
    @Test fun puladasAsVerificadasHaPoucasHoras() {
        val ts = listOf(turma("a", 0, agora - 3600_000L), turma("b", 0, agora - 5 * 3600_000L))
        val p = SgeFila.planejar(listOf("a", "b"), ts, agora, pular = true)
        assertEquals(listOf("b"), p.ordem); assertEquals(1, p.puladas)
    }
    @Test fun turmaEscolhidaDePropositoNaoEPulada() {
        val p = SgeFila.planejar(listOf("a"), listOf(turma("a", 0, agora - 60_000L)), agora, pular = false)
        assertEquals(listOf("a"), p.ordem); assertEquals(0, p.puladas)
    }
    @Test fun turmaComErroNuncaEPulada() {
        assertFalse(SgeFila.recente(turma("a", 0, agora - 60_000L, erro = "x"), agora))
    }
    @Test fun estimativaArredondaParaCimaEMediaSuaviza() {
        assertEquals(3, SgeFila.estimativaMin(2, 90_000L)); assertEquals(1, SgeFila.estimativaMin(1, 20_000L))
        assertEquals(100_000L, SgeFila.novaMedia(90_000L, 120_000L)); assertEquals(90_000L, SgeFila.novaMedia(90_000L, 0L))
    }
    @Test fun cacheSoReaproveitaCompletoSemPendenciaEDentroDeSeteDias() {
        val ok = mod(0); val nc = ResultadoModulo(0, "A periodicidade precisa ser definida pelo professor")
        val dia = 24 * 3600_000L
        assertTrue(SgeCachePlanos.reaproveitavel(ok, agora - 2 * dia, agora))
        assertTrue(SgeCachePlanos.reaproveitavel(nc, agora - 2 * dia, agora))
        assertFalse(SgeCachePlanos.reaproveitavel(ok, agora - 8 * dia, agora))
        assertFalse(SgeCachePlanos.reaproveitavel(mod(2), agora - dia, agora))
        assertFalse(SgeCachePlanos.reaproveitavel(mod(0, completa = false), agora - dia, agora))
        assertFalse(SgeCachePlanos.reaproveitavel(null, agora - dia, agora)); assertFalse(SgeCachePlanos.reaproveitavel(ok, 0L, agora))
    }
    @Test fun anotacaoDoCacheNaoSeAcumulaENaoPerdeNaoConfigurado() {
        val nc = ResultadoModulo(0, "A periodicidade precisa ser definida pelo professor")
        val a = SgeCachePlanos.anotar(SgeCachePlanos.anotar(nc, agora), agora)
        assertEquals(1, Regex("reaproveitado").findAll(a.detalhe).count()); assertTrue(a.naoConfigurado)
    }
}

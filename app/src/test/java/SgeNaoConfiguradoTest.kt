package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test

class SgeNaoConfiguradoTest {
    private val nc = ResultadoModulo(0, "A periodicidade precisa ser definida pelo professor", coberturaCompleta = false)
    private fun r(id: String, disc: String, plano: ResultadoModulo) =
        ResultadoTurma(TurmaMonitorada(id, "$id/EM", "Escola", disc),
            listOf(ResultadoModulo(0, coberturaCompleta = true), ResultadoModulo(0, coberturaCompleta = true), plano, ResultadoModulo(0, coberturaCompleta = true), ResultadoModulo(0, coberturaCompleta = true)), "01/11 10:00")

    @Test fun zeroSemPeriodicidadeViraNcENaoAsterisco() {
        assertTrue(nc.naoConfigurado); assertEquals("n/c", nc.resumo())
        assertEquals("0*", ResultadoModulo(0, "Cobertura não verificada: x", coberturaCompleta = false).resumo())
    }
    @Test fun resumoDoModuloTodoNaoConfiguradoMostraNc() {
        val m = SgeDashboard.resumir(listOf(r("1", "Química", nc), r("2", "Física", nc)))[2]
        assertTrue(m.naoConfigurado); assertEquals("n/c", m.valor()); assertTrue(m.completa)
    }
    @Test fun listaDeConfiguracaoPendenteCitaTurmaEDisciplina() {
        val l = SgeConfiguracao.pendencias(listOf(r("33.01", "QUÍMICA", nc), r("33.03", "FÍSICA", nc)))
        assertEquals(1, l.size); assertTrue(l[0].contains("Química 33.01")); assertTrue(l[0].contains("Física 33.03"))
    }
    @Test fun diagnosticoAgrupaLinhasRepetidas() {
        val rs = (1..62).map { r("$it", "Química", nc) }
        val l = SgeDiagnosticoAgrupado.linhas(rs)
        assertEquals(1, l.size); assertTrue(l[0].startsWith("62 consultas com periodicidade não definida"))
    }
}

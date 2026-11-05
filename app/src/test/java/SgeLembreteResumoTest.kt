package br.to.seduc.sgediariowidget.worker

import br.to.seduc.sgediariowidget.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SgeLembreteResumoTest {
    private fun resultado(id: String, quantidade: Int?, completa: Boolean = true) = ResultadoTurma(
        TurmaMonitorada(id, "Turma $id", "", "Química"),
        listOf(ResultadoModulo(quantidade, coberturaCompleta = completa)), "consulta")

    @Test fun avisaSomentePorContagensPositivasConhecidas() {
        val resumo = LembreteResumo.pendenciasPorModulo(listOf(
            resultado("1", 2), resultado("2", 0), resultado("3", null), resultado("4", 0, completa = false)))
        assertEquals(listOf("Conteúdos: 1 turma(s)/componente(s)"), resumo)
    }

    @Test fun naoRepeteTurmaComponenteDuplicados() {
        val r = resultado("1", 1)
        assertEquals(listOf("Conteúdos: 1 turma(s)/componente(s)"), LembreteResumo.pendenciasPorModulo(listOf(r, r)))
        assertTrue(LembreteResumo.pendenciasPorModulo(listOf(resultado("2", null))).isEmpty())
    }
}

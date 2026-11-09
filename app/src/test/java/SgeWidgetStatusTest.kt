package br.to.seduc.sgediariowidget

import br.to.seduc.sgediariowidget.data.ResultadoModulo
import br.to.seduc.sgediariowidget.data.ResultadoTurma
import br.to.seduc.sgediariowidget.data.TurmaMonitorada
import br.to.seduc.sgediariowidget.widget.ultimaConsultaWidget
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SgeWidgetStatusTest {
    private val turma = TurmaMonitorada("1", "13.01", "Escola", "Química")
    private val agora = LocalDateTime.of(2026, 11, 9, 15, 0)

    @Test fun tentativaSemDadosNaoSubstituiHorarioDaUltimaConsulta() {
        val confirmado = ResultadoTurma(turma, listOf(ResultadoModulo(0)), "08/11 13:05")
        val falha = ResultadoTurma(turma, listOf(ResultadoModulo()), "09/11 12:00")
        assertEquals("08/11 13:05", ultimaConsultaWidget(listOf(confirmado, falha), agora))
        assertNull(ultimaConsultaWidget(listOf(falha), agora))
    }

    @Test fun reaproveitamentoPreservaHorarioDoModulo() {
        val carimbo = agora.minusHours(2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reaproveitado = ResultadoTurma(turma, listOf(ResultadoModulo(2, atualizadoMs = carimbo)), "09/11 15:00")
        assertEquals("09/11 13:00", ultimaConsultaWidget(listOf(reaproveitado), agora))
    }
}

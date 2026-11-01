package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SgeHistoricoTest {
    private val hoje = LocalDate.of(2026, 11, 1)
    private fun r(id: String = "1", n: Int? = 2, completa: Boolean = true) = ResultadoTurma(TurmaMonitorada(id, "Turma", "", "Química"),
        List(5) { ResultadoModulo(n, "texto que não integra o histórico", coberturaCompleta = completa) }, "01/11 10:00")
    @Test fun consultasDoMesmoDiaAtualizamSemDuplicar() {
        val a = SgeHistorico.registrar(emptyList(), FontePainel.PESSOAL, listOf(r(), r("2")), hoje)
        val b = SgeHistorico.registrar(a, FontePainel.PESSOAL, listOf(r(n = 1)), hoje)
        assertEquals(1, b.size); assertEquals(2, b.single().turmas); assertEquals(3, b.single().modulos[0].quantidade)
    }
    @Test fun diasDiferentesNaoHerdamTurmasNaoConsultadas() {
        val a = SgeHistorico.registrar(emptyList(), FontePainel.PESSOAL, listOf(r(), r("2")), hoje.minusDays(1))
        val b = SgeHistorico.registrar(a, FontePainel.PESSOAL, listOf(r()), hoje)
        assertEquals(1, b.last().turmas); assertNull(b.last().variacao(b.first(), 0))
    }
    @Test fun desconhecidosEParciaisNaoProduzemVariacao() {
        val a = DiaHistorico(hoje.minusDays(1), FontePainel.COORDENACAO, listOf(MedicaoHistorica("1|quimica", "1", r().modulos)))
        val b = a.copy(data = hoje, medicoes = listOf(a.medicoes[0].copy(modulos = r(n = null).modulos)))
        assertNull(b.modulos[0].quantidade); assertNull(b.variacao(a, 0))
        val c = b.copy(medicoes = listOf(a.medicoes[0].copy(modulos = r(n = 0, completa = false).modulos)))
        assertEquals("0*", c.modulos[0].valor()); assertNull(c.variacao(a, 0))
    }
    @Test fun variacaoSoComMesmoEscopoECoberturaCompleta() {
        val a = SgeHistorico.registrar(emptyList(), FontePainel.PESSOAL, listOf(r(n = 4)), hoje.minusDays(1)).single()
        val b = SgeHistorico.registrar(listOf(a), FontePainel.PESSOAL, listOf(r(n = 1)), hoje).last()
        assertEquals(-3, b.variacao(a, 0)); assertNull(b.copy(fonte = FontePainel.COORDENACAO).variacao(a, 0))
    }
    @Test fun fontesFicamSeparadasENaoPersistemDetalhes() {
        val a = SgeHistorico.registrar(emptyList(), FontePainel.PESSOAL, listOf(r()), hoje)
        val b = SgeHistorico.registrar(a, FontePainel.COORDENACAO, listOf(r(n = null)), hoje)
        val raw = SgeHistorico.json(b); assertFalse(raw.contains("texto que")); assertEquals(b, SgeHistorico.ler(raw))
    }
    @Test fun retencaoMantemSeteDiasEMaisUmParaComparacao() {
        val a = SgeHistorico.registrar(emptyList(), FontePainel.PESSOAL, listOf(r()), hoje.minusDays(8))
        val b = SgeHistorico.registrar(a, FontePainel.PESSOAL, listOf(r()), hoje)
        assertEquals(listOf(hoje), b.map { it.data })
    }
}

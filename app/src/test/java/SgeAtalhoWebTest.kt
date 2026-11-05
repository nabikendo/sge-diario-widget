package br.to.seduc.sgediariowidget

import org.junit.Assert.*
import org.junit.Test

class SgeAtalhoWebTest {
    @Test fun normalizaAcentosEEspacos() {
        assertEquals("frequencia", SgeAtalhoWeb.normalizar("  Frequência "))
        assertEquals("lingua portuguesa", SgeAtalhoWeb.normalizar("LÍNGUA   PORTUGUESA"))
    }

    @Test fun tipoDeRegistroSoParaPlanosEPei() {
        assertNull(SgeAtalhoWeb.tipoRegistro(0)); assertNull(SgeAtalhoWeb.tipoRegistro(1))
        assertEquals("2", SgeAtalhoWeb.tipoRegistro(2)); assertEquals("3", SgeAtalhoWeb.tipoRegistro(3)); assertEquals("4", SgeAtalhoWeb.tipoRegistro(4))
    }

    @Test fun padroesOnclickPorModulo() {
        assertTrue("/conteudo" in SgeAtalhoWeb.padroesOnclick(0))
        assertTrue("/frequenciadiaria" in SgeAtalhoWeb.padroesOnclick(1))
        assertTrue("/notasfaltas/frequencia" in SgeAtalhoWeb.padroesOnclick(1))
        assertTrue("/planejamentoturma" in SgeAtalhoWeb.padroesOnclick(2))
    }

    @Test fun marcadoresEFiltrosCorretos() {
        assertTrue(SgeAtalhoWeb.marcador(0).contains("formConteudoAplicado"))
        assertTrue(SgeAtalhoWeb.marcador(1).contains("tableListaAlunos"))
        assertTrue(SgeAtalhoWeb.filtroComponente(1).contains("GradeEscolarIdPreenchimento"))
        assertTrue(SgeAtalhoWeb.filtroComponente(0).contains("GradeEscolarId"))
    }

    @Test fun nuncaClicaBotoesQueGravamDados() {
        (0..4).forEach { mod ->
            val js = SgeAtalhoWeb.jsAtalho(AtalhoSge("1_2", "13.01", "QUÍMICA", mod))
            assertFalse("mod=$mod: não deve clicar em btnGerarFrequenciaParaTodos", js.contains("btnGerarFrequenciaParaTodos"))
            assertFalse("mod=$mod: não deve clicar em btn-submit-procedimento-lote", js.contains("btn-submit-procedimento-lote"))
        }
    }

    @Test fun scriptCarregaDadosDaTurmaComAspasEscapadas() {
        val js = SgeAtalhoWeb.jsAtalho(AtalhoSge("123_4", "1ª \"A\" Matutino", "LÍNGUA PORTUGUESA", 2))
        assertTrue(js.contains("\"123_4\""))
        assertTrue(js.contains("1ª \\\"A\\\" Matutino"))
        assertTrue(js.contains("/planejamentoturma"))
    }
}

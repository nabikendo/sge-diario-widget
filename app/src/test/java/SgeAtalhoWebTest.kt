package br.to.seduc.sgediariowidget

import org.junit.Assert.*
import org.junit.Test

class SgeAtalhoWebTest {
    @Test fun extraiDatasEHorarioDoDetalheReal() {
        assertEquals(listOf("05/11", "06/11"), SgeAtalhoWeb.datasPendentes("Dias pendentes até 06/11/2026: 05/11, 06/11"))
        assertEquals("13:50", SgeAtalhoWeb.horarioPendente("Chamada 13:50 - 14:40"))
    }

    @Test fun atalhoNaoClicaControleDePresencaNemUsaJsoupNoNavegador() {
        val js = SgeAtalhoWeb.jsAtalho(AtalhoSge("27713", "13.01", "QUÍMICA", 1))
        assertFalse(js.contains("check_ativo_"))
        assertFalse(js.contains("Jsoup?"))
        assertTrue(js.contains("querySelectorAll(s.trim())"))
    }
    @Test fun turmaExigeNomeEIdentificadorUnicos() {
        val js = SgeAtalhoWeb.jsAtalho(AtalhoSge("27713", "13.01", "QUÍMICA", 0))
        assertTrue(js.contains("data-turmadescricao"))
        assertTrue(js.contains("nomeTurma(selecionada)"))
        assertTrue(js.contains("O SGE repetiu o identificador da turma"))
    }
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
        assertTrue("/planejamentoturma" in SgeAtalhoWeb.padroesOnclick(4))
    }

    @Test fun marcadoresEFiltrosCorretos() {
        assertTrue(SgeAtalhoWeb.marcador(0).contains("formConteudoAplicado"))
        assertTrue(SgeAtalhoWeb.marcador(1).contains("tableListaAlunos"))
        assertTrue(SgeAtalhoWeb.filtroComponente(1).contains("GradeEscolarIdPreenchimento"))
        assertTrue(SgeAtalhoWeb.filtroComponente(0).contains("GradeEscolarId"))
    }

    @Test fun nuncaClicarBotoesQueGravamDados() {
        // O JS pode mencionar o id do botão proibido para evitá-lo, mas nunca deve chamar .click() nele.
        // Verificamos que btnGerarFrequenciaParaTodos nunca é referenciado de forma alguma.
        (0..4).forEach { mod ->
            val js = SgeAtalhoWeb.jsAtalho(AtalhoSge("1_2", "13.01", "QUÍMICA", mod))
            assertFalse("mod=$mod: btnGerarFrequenciaParaTodos não deve aparecer no JS", js.contains("btnGerarFrequenciaParaTodos"))
            // O botão de lote só aparece como string de exclusão, nunca como alvo de .click()
            assertFalse("mod=$mod: .click() não deve seguir o btn-submit",
                js.contains("btn-submit-procedimento-lote').click()") ||
                js.contains("btn-submit-procedimento-lote\").click()"))
        }
    }

    @Test fun scriptCarregaDadosDaTurmaComAspasEscapadas() {
        val js = SgeAtalhoWeb.jsAtalho(AtalhoSge("123_4", "1ª \"A\" Matutino", "LÍNGUA PORTUGUESA", 2))
        assertTrue(js.contains("\"123_4\""))
        assertTrue(js.contains("1ª \\\"A\\\" Matutino"))
        // módulo 2 (plano de aula) deve procurar /planejamentoturma no onclick
        assertTrue(js.contains("/planejamentoturma"))
    }
}

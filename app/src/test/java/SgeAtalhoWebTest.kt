package br.to.seduc.sgediariowidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun cadaTelaTemSeuMarcadorEFiltro() {
        assertEquals("#formConteudoAplicado", SgeAtalhoWeb.marcador(0))
        assertEquals("#btnGerar", SgeAtalhoWeb.marcador(1)); assertEquals("#DisciplinaId", SgeAtalhoWeb.filtroComponente(1))
        assertEquals("#formPlanejamentoLista", SgeAtalhoWeb.marcador(3)); assertEquals("#GradeEscolarId", SgeAtalhoWeb.filtroComponente(3))
    }

    @Test fun soPesquisaComBotoesQueNaoSalvam() {
        assertNull(SgeAtalhoWeb.botaoPesquisar(0))
        assertEquals("#btn_search", SgeAtalhoWeb.botaoPesquisar(2))
        // "Gerar frequência para todos" grava dados: nunca é clicado.
        (0..4).forEach { assertFalse(SgeAtalhoWeb.jsAtalho(AtalhoSge("1_2", "13.01", "QUÍMICA", it)).contains("btnGerarFrequenciaParaTodos")) }
    }

    @Test fun acoesDoMaisEspecificoAoMaisGeral() {
        assertEquals("registrar aula", SgeAtalhoWeb.padroesAcao(0).first())
        assertTrue("chamada" in SgeAtalhoWeb.padroesAcao(1))
        assertTrue("verificacao" in SgeAtalhoWeb.evitar)
    }

    @Test fun scriptCarregaDadosDaTurmaComAspasEscapadas() {
        val js = SgeAtalhoWeb.jsAtalho(AtalhoSge("123_4", "1ª \"A\" Matutino", "LÍNGUA PORTUGUESA", 2))
        assertTrue(js.contains("\"123_4\""))
        assertTrue(js.contains("1ª \\\"A\\\" Matutino"))
        assertTrue(js.contains("\"#btn_search\""))
    }
}

package br.to.seduc.sgediariowidget

import org.junit.Assert.assertEquals
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

    @Test fun menuProcuraDoMaisEspecificoAoMaisGeral() {
        assertEquals("registro de conteudo", SgeAtalhoWeb.padroesMenu(0).first())
        assertTrue("chamada" in SgeAtalhoWeb.padroesMenu(1))
        assertTrue(SgeAtalhoWeb.padroesMenu(2).isEmpty())
    }

    @Test fun scriptsCarregamOsDadosDaTurmaSemQuebrarTexto() {
        val a = AtalhoSge("123", "1ª \"A\" Matutino", "LÍNGUA PORTUGUESA", 2)
        val js = SgeAtalhoWeb.jsPlanos(a)
        assertTrue(js.contains("\"123\"".replace("123", "2")))
        assertTrue(js.contains("1ª \\\"A\\\" Matutino"))
        assertTrue(SgeAtalhoWeb.jsSelecao(a.copy(modulo = 1)).contains("\"123\""))
        assertTrue(SgeAtalhoWeb.jsMenu(a.copy(modulo = 1)).contains("\"frequencia\""))
    }
}

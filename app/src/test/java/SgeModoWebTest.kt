package br.to.seduc.sgediariowidget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SgeModoWebTest {
    @Test fun userAgentDeComputadorMantemChromiumERemoveIdentificadoresMoveis() {
        val userAgentMovel = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.91 Mobile Safari/537.36"
        val userAgentComputador = SgeModoWeb.userAgentComputador(userAgentMovel)

        assertTrue(userAgentComputador.contains("X11; Linux x86_64"))
        assertTrue(userAgentComputador.contains("Chrome/130.0.6723.91"))
        assertFalse(userAgentComputador.contains("Android"))
        assertFalse(userAgentComputador.contains("Mobile"))
        assertFalse(userAgentComputador.contains("; wv"))
    }
}

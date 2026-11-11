package br.to.seduc.sgediariowidget

import org.junit.Assert.*
import org.junit.Test

class SgeWebComputadorTest {
    @Test fun reconheceTelaDeLoginPelaUrl() {
        assertTrue(SgeWebComputador.ehLogin("https://sge.example/Acesso/Entrar?ReturnUrl=%2F"))
        assertTrue(SgeWebComputador.ehLogin("https://sge.example/acesso/entrar"))
        assertFalse(SgeWebComputador.ehLogin("https://sge.example/"))
        assertFalse(SgeWebComputador.ehLogin(null))
    }

    @Test fun loginSempreUsaLarguraDoAparelhoEmQualquerModo() {
        assertEquals("width=device-width, initial-scale=1", SgeWebComputador.conteudoViewport(computador = true, login = true, larguraDp = 360))
        assertEquals("width=device-width, initial-scale=1", SgeWebComputador.conteudoViewport(computador = false, login = true, larguraDp = 800))
    }

    @Test fun modoComputadorSeAdaptaALarguraDaTela() {
        assertEquals("width=1280", SgeWebComputador.conteudoViewport(true, false, 360))
        assertEquals("width=1280", SgeWebComputador.conteudoViewport(true, false, 800))
        assertEquals("width=device-width, initial-scale=1", SgeWebComputador.conteudoViewport(true, false, 1000))
        assertEquals("width=device-width, initial-scale=1", SgeWebComputador.conteudoViewport(true, false, 1280))
    }

    @Test fun modoCelularMantemOViewportOriginalForaDoLogin() {
        assertNull(SgeWebComputador.conteudoViewport(false, false, 360))
    }
}

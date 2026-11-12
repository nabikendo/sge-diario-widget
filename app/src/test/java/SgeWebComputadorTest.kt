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

    @Test fun scriptDeLoginCentralizaFormularioNoFluxoDaPagina() {
        val js = SgeWebComputador.SCRIPT_CENTRALIZAR_LOGIN
        assertTrue(js.contains("content_login") && js.contains("login-form"))
        assertTrue(js.contains("sge-login-centro"))
        assertTrue(js.contains("position:static") && js.contains("margin-inline:auto"))
        assertTrue(js.contains("enxugar") && js.contains("margin-top") && js.contains("padding-bottom"))
        assertFalse(js.contains("position:fixed") || js.contains("scrollTo"))
        assertFalse("não deve sobrar interpolação de Kotlin", js.contains("\${"))
    }
}

package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SgeBimestreJanelasTest {
    private fun d(dia: Int, mes: Int) = LocalDate.of(2026, mes, dia)
    private val janelas = listOf(d(2,2) to d(30,4), d(1,5) to d(15,7), d(1,8) to d(10,10), d(11,10) to d(20,12))

    @Test fun aceitaIndiceZeroQuandoIndiceUmEhInconsistente() {
        val m = SgeBimestreJanelas.carregar { p -> janelas.getOrNull(p.toInt()) }
        assertEquals(janelas[0], m[1])
        assertEquals(janelas[3], m[4])
    }

    @Test fun aceitaIndiceUmQuandoZeroEhAusente() {
        val m = SgeBimestreJanelas.carregar { p ->
            p.toInt().takeIf { it in 1..4 }?.let { janelas[it-1] }
        }
        assertEquals(janelas[0], m[1])
    }

    @Test fun intervaloRepetidoNaoSeTornaBimestreValido() {
        assertThrows(IllegalStateException::class.java) {
            SgeBimestreJanelas.carregar { janelas[0] }
        }
    }

    @Test fun semQuatroBimestresNaoAfirmaQueEstaCompleto() {
        assertThrows(IllegalStateException::class.java) {
            SgeBimestreJanelas.carregar { p -> janelas.getOrNull(p.toInt()).takeIf { p != "3" } }
        }
    }
}

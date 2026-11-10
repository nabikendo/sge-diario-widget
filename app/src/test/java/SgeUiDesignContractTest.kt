package br.to.seduc.sgediariowidget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Contrato visual estrutural: protege tokens essenciais sem comparar pixels ou screenshots. */
class SgeUiDesignContractTest {
    @Test
    fun temaOledUsaPretoPuroECartaoPrincipalComRaioDe32Dp() {
        assertEquals(Color(0xFF000000), PretoOled)
        assertEquals(32.dp, RaioCartao)
        assertEquals(32.dp, RaioHero)
    }
}

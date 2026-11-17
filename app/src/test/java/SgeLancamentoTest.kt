package br.to.seduc.sgediariowidget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SgeLancamentoTest {
    @Test
    fun deduzSerieDoCodigoDaTurma() {
        assertThat(TurmaAutomacao("1", "13.02/EM.MAT-ANL - Matutino/Manhã", emptyMap()).serieDeduzida).isEqualTo("1ª")
        assertThat(TurmaAutomacao("2", "23.01/EM.MAT-ANL", emptyMap()).serieDeduzida).isEqualTo("2ª")
        assertThat(TurmaAutomacao("3", "33.03/EM.VESP-ANL", emptyMap()).serieDeduzida).isEqualTo("3ª")
    }

    @Test
    fun turmaForaDoPadraoNaoDeduzSerie() {
        assertThat(TurmaAutomacao("4", "EJA 3º Segmento A", emptyMap()).serieDeduzida).isNull()
        assertThat(TurmaAutomacao("5", "MULTI 1ª/2ª", emptyMap()).serieDeduzida).isNull()
    }

    @Test
    fun reconheceComponenteDoSge() {
        assertThat(Componente.deTexto("QUÍMICA")).isEqualTo(Componente.QUIMICA)
        assertThat(Componente.deTexto(" Física ")).isEqualTo(Componente.FISICA)
        assertThat(Componente.deTexto("MATEMÁTICA")).isNull()
    }

    @Test
    fun normalizaIgnorandoAcentosEspacos() {
        assertThat("  Separação   de Misturas ".normalizarSge()).isEqualTo("separacao de misturas")
    }

    @Test
    fun rotuloUsaPrimeiroObjetoDoComponente() {
        val h = Habilidade("1-EM13CNT301-2", "1ª", "EM13CNT301", "Vida, Terra e Cosmos", "Construir questões…", null,
            mapOf(Componente.QUIMICA to listOf("Funções Inorgânicas: Ácidos, Bases, Sais e Óxidos")))
        assertThat(h.rotulo(Componente.QUIMICA)).isEqualTo("EM13CNT301 · Funções Inorgânicas: Ácidos, Bases, Sais e Óxidos")
        assertThat(h.rotulo(Componente.FISICA)).isEqualTo("EM13CNT301 · Construir questões…")
        assertThat(h.textoSge).isEqualTo("(EM13CNT301) Construir questões…")
    }
}

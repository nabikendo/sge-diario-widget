package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Páginas de exemplo (sem dados de alunos) para cada tela lida do SGE: a versão conhecida e uma versão que o SGE "mudou". */
class SgeTelasExemploTest {
    private val hoje = LocalDate.of(2026, 11, 2)
    private val popupSemAula = "&lt;table&gt;&lt;tr&gt;&lt;td&gt;QUÍMICA&lt;/td&gt;&lt;td&gt;&lt;i class=&quot;fa fa-plus-circle&quot;&gt;&lt;/i&gt;&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;"
    private val conteudosV1 = """<div class="calendario"><div class="semana"><div class="vermelho" title="29/10/2026" data-content="$popupSemAula"></div><div class="vermelho" title="30/10/2026" data-content="$popupSemAula"></div></div></div>"""
    private val conteudosMudou = """<section class="agenda"><ul><li class="atrasado" data-dia="29/10/2026">Química</li></ul></section>"""
    private val frequenciaV1 = """<table id="gridFrequencia"><thead><tr><th class="cabecalhoMes" title="29/10/2026"></th><th class="cabecalhoMes" title="30/10/2026"></th></tr></thead></table>"""
    private val frequenciaMudou = """<table id="grade-presenca"><thead><tr><th data-dia="29/10/2026"></th></tr></thead></table>"""
    private val planosV1 = """<table id="tabela_planejamentos"><thead><tr><th>Período</th><th>Situação</th></tr></thead><tbody><tr><td>1º Bimestre</td><td>Aprovado</td></tr></tbody></table>"""
    private val planosMudou = """<table id="tabela_planejamentos"><thead><tr><th>Período</th><th>Status do envio</th></tr></thead><tbody><tr><td>1º Bimestre</td><td>OK</td></tr></tbody></table>"""

    @Test fun conteudosVersaoConhecida() { assertEquals(2, SgeParsing.conteudos(conteudosV1, "QUÍMICA", hoje).quantidade) }
    @Test fun frequenciaVersaoConhecida() { assertEquals(2, SgeParsing.frequencia(frequenciaV1, hoje).quantidade) }
    @Test fun planosVersaoConhecida() { assertEquals(0, SgeParsing.planos(planosV1).quantidade) }
    @Test fun conteudosComTelaNovaFalhaEmVezDeMostrarZero() { assertThrows(IllegalStateException::class.java) { SgeParsing.conteudos(conteudosMudou, "QUÍMICA", hoje) } }
    @Test fun frequenciaComTelaNovaFalhaEmVezDeMostrarZero() { assertThrows(IllegalStateException::class.java) { SgeParsing.frequencia(frequenciaMudou, hoje) } }
    @Test fun planosComColunaNovaFalhaEmVezDeMostrarZero() { assertThrows(IllegalStateException::class.java) { SgeParsing.planos(planosMudou) } }

    @Test fun duasTelasNovasNoMesmoPainelAcendemOAvisoDeSgeMudou() {
        fun falha(msg: String) = ResultadoModulo(detalhe = "Não verificado: $msg")
        val rs = (1..2).map { ResultadoTurma(TurmaMonitorada("$it", "$it/EM", "Escola", "Química"), listOf(falha("Calendário ausente"), falha("Grade de frequência ausente"), falha("Grade de planos ausente"), falha("Grade de planos ausente"), falha("Grade de planos ausente")), "02/11 10:00") }
        assertTrue(SgeSaude.sgeMudou(rs))
    }
    @Test fun iniciaisEscondemNomeSemExporParticulas() {
        assertEquals("M. S.", SgePrivacidade.iniciais("Maria da Silva")); assertEquals("J. P. S.", SgePrivacidade.iniciais("  João  Pedro dos Santos "))
        assertEquals("—", SgePrivacidade.iniciais(" "))
    }
}

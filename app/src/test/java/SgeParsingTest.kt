package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SgeParsingTest {
    @Test fun periodosMensaisESemestresUsamDatasReais() {
        val julho = SgeParsing.janela("Julho de 2026", "202607", 2026)
        assertEquals(LocalDate.of(2026, 7, 1), julho.first)
        assertEquals(LocalDate.of(2026, 7, 31), julho.second)
        assertEquals(LocalDate.of(2026, 7, 1), SgeParsing.janela("Segundo Semestre", "1", 2026).first)
    }
    @Test fun mesSemAulaNaoCriaObrigacaoDePlano() {
        val html = """<div class="calendario">
            <div class="mes"><div class="mes-titulo">JULHO</div><div class="mes-subtitulo">Nenhum registro a ser feito</div>
            <div class="semana"><div title="01/07/2026"></div></div></div>
            <div class="mes"><div class="mes-titulo">AGOSTO</div><div class="mes-subtitulo">0 de 8 registrados</div></div>
            </div>"""
        val meses = SgeParsing.mesesComAula(org.jsoup.Jsoup.parse(html))
        assertEquals(setOf(java.time.YearMonth.of(2026, 8)), meses)
    }
    @Test fun conteudosExcluemFuturoEOutrosComponentes() {
        val html = """<div class="calendario"><div class="semana">
          <div class="vermelho" title="29/10/2026" data-content="&lt;table&gt;&lt;tr&gt;&lt;td&gt;QUÍMICA&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;"></div>
          <div class="vermelho" title="03/11/2026" data-content="&lt;table&gt;&lt;tr&gt;&lt;td&gt;QUÍMICA&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;"></div>
          <div class="vermelho" title="28/10/2026" data-content="&lt;table&gt;&lt;tr&gt;&lt;td&gt;FÍSICA&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;"></div>
        </div></div>"""
        assertEquals(1, SgeParsing.conteudos(html, "QUÍMICA", LocalDate.of(2026, 10, 30)).quantidade)
    }
    @Test(expected = IllegalStateException::class) fun paginaVaziaNaoSignificaZero() {
        SgeParsing.conteudos("<html></html>", "QUÍMICA", LocalDate.now())
    }
    @Test fun frequenciaContaDiasDistintosAteHoje() {
        val html = """<table id="gridFrequencia"><thead><tr>
            <th class="cabecalhoMes" title="29/10/2026"></th>
            <th class="cabecalhoMes" title="29/10/2026"></th>
            <th class="cabecalhoMes" title="03/11/2026"></th>
        </tr></thead></table>"""
        assertEquals(1, SgeParsing.frequencia(html, LocalDate.of(2026, 10, 30)).quantidade)
    }
    @Test fun planosNaoConfundemAvaliacaoComAcaoDoProfessor() {
        val html = """<table id="tabela_planejamentos"><thead><tr><th>Período</th><th>Situação</th></tr></thead><tbody>
          <tr><td>A</td><td>Não Enviado</td></tr><tr><td>B</td><td>Reprovado</td></tr>
          <tr><td>C</td><td>Aguardando Avaliação</td></tr><tr><td>D</td><td>Aprovado</td></tr>
        </tbody></table>"""
        val m = SgeParsing.planos(html)
        assertEquals(2, m.quantidade)
        assertEquals(1, m.aguardando)
        assertEquals(1, m.aprovados)
        assertFalse(m.coberturaCompleta)
        assertEquals("2*", m.resumo())
    }
    @Test(expected = IllegalStateException::class) fun estadoNovoNaoViraZero() {
        SgeParsing.planos("""<table id="tabela_planejamentos"><thead><tr><th>Situação</th></tr></thead>
        <tbody><tr><td>Estado desconhecido</td></tr></tbody></table>""")
    }
    @Test fun resultadoDesconhecidoPersisteComoDesconhecido() {
        val m = ResultadoModulo.ler(ResultadoModulo().json())
        assertNull(m.quantidade)
        assertEquals("—", m.resumo())
    }
    @Test fun turmasPreservamDisciplinaEEscola() {
        val r = ResultadoTurma(TurmaMonitorada("123", "33.03", "Escola A", "FÍSICA"), List(5) { ResultadoModulo() }, "30/10 12:00")
        assertEquals(r, ResultadoTurma.ler(r.json()))
    }
    @Test fun conteudoLancadoNaoContaQuandoOutraDisciplinaDoDiaEstaPendente() {
        val popup = "&lt;table&gt;&lt;tr&gt;&lt;td&gt;QUÍMICA&lt;/td&gt;&lt;td&gt;&lt;i class=&quot;fa fa-edit&quot;&gt;&lt;/i&gt;&lt;/td&gt;&lt;/tr&gt;" +
            "&lt;tr&gt;&lt;td&gt;MATEMÁTICA&lt;/td&gt;&lt;td&gt;&lt;i class=&quot;fa fa-plus-circle&quot;&gt;&lt;/i&gt;&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;"
        val html = """<div class="calendario"><div class="semana"><div class="vermelho" title="29/10/2026" data-content="$popup"></div></div></div>"""
        assertEquals(0, SgeParsing.conteudos(html, "QUÍMICA", LocalDate.of(2026, 10, 30)).quantidade)
        val mat = SgeParsing.conteudos(html, "MATEMÁTICA", LocalDate.of(2026, 10, 30))
        assertEquals(1, mat.quantidade)
        assertTrue(mat.detalhe.contains("29/10"))
    }
    @Test fun coordenacaoListaSoAulasComIconeMais() {
        val popup = "&lt;table&gt;&lt;tr&gt;&lt;td&gt;QUÍMICA&lt;/td&gt;&lt;td&gt;07:00&lt;/td&gt;&lt;td&gt;&lt;i class=&quot;fa fa-edit&quot;&gt;&lt;/i&gt;&lt;/td&gt;&lt;/tr&gt;" +
            "&lt;tr&gt;&lt;td&gt;MATEMÁTICA&lt;/td&gt;&lt;td&gt;07:50&lt;/td&gt;&lt;td&gt;&lt;i class=&quot;fa fa-plus-circle&quot;&gt;&lt;/i&gt;&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;"
        val html = """<div class="calendario"><div class="semana"><div class="vermelho" title="29/10/2026" data-content="$popup"></div></div></div>"""
        val l = SgeParsing.pendenciasCoordenacao(html, LocalDate.of(2026, 10, 30))
        assertEquals(listOf(PendenciaDia("2026-10-29", "MATEMÁTICA", "07:50")), l)
    }
    @Test fun periodosEmFormatosComunsSaoIdentificadosSemEstimar() {
        assertEquals(LocalDate.of(2026, 6, 1), SgeParsing.janelaOuNull("Jun/2026", "5", 2026)!!.first)
        assertEquals(LocalDate.of(2026, 6, 30), SgeParsing.janelaOuNull("06/2026", "5", 2026)!!.second)
        assertEquals(LocalDate.of(2026, 6, 1), SgeParsing.janelaOuNull("Período letivo", "202606", 2026)!!.first)
        assertEquals(LocalDate.of(2026, 10, 26) to LocalDate.of(2026, 11, 1), SgeParsing.janelaOuNull("26/10 a 01/11", "9", 2026))
        assertEquals(LocalDate.of(2026, 7, 1), SgeParsing.janelaOuNull("2º semestre", "2", 2026)!!.first)
        assertEquals(null, SgeParsing.janelaOuNull("Período especial", "19", 2026))
        assertEquals(LocalDate.of(2026, 3, 23) to LocalDate.of(2026, 3, 29), SgeParsing.janelaOuNull("13ª Semana de 2026", "202613", 2026))
        assertEquals(LocalDate.of(2026, 2, 2) to LocalDate.of(2026, 2, 8), SgeParsing.janelaOuNull("6ª Semana de 2026", "202606", 2026))
        assertEquals(null, SgeParsing.janelaOuNull("60ª Semana de 2026", "202660", 2026))
    }
    @Test fun numeroDoBimestreAceitaFormasComuns() {
        listOf("1º Bimestre" to 1, "2ª bimestre" to 2, "Bimestre 3" to 3, "IV Bimestre" to 4, "Quarto Bimestre" to 4, "3o bim." to 3, "Bimestre - 2" to 2, "Bimestre I" to 1)
            .forEach { (d, n) -> assertEquals(d, n, SgeParsing.numeroBimestre(d)) }
        assertEquals(null, SgeParsing.numeroBimestre("Período especial")); assertEquals(null, SgeParsing.numeroBimestre("5º Bimestre"))
    }
}

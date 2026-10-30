package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SgeParsingTest {
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
}

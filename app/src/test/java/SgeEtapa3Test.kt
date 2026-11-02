package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class SgeEtapa3Test {
    private val agora = LocalDateTime.of(2026, 11, 2, 15, 0)
    private fun mod(q: Int?, completa: Boolean = true, detalhe: String = "") = ResultadoModulo(q, detalhe, coberturaCompleta = completa)
    private fun r(id: String, disc: String, atualizado: String = "02/11 14:00", ms: List<ResultadoModulo> = List(5) { mod(0) }) =
        ResultadoTurma(TurmaMonitorada(id, "$id/EM", "Escola", disc), ms, atualizado)

    @Test fun idadeDoDadoEFaixasDeCor() {
        assertEquals(1L, SgeIdade.horas("02/11 14:00", agora)); assertEquals(30L, SgeIdade.horas("01/11 09:00", agora))
        assertEquals(SgeIdade.Faixa.RECENTE, SgeIdade.faixa(5)); assertEquals(SgeIdade.Faixa.MEDIA, SgeIdade.faixa(6))
        assertEquals(SgeIdade.Faixa.MEDIA, SgeIdade.faixa(23)); assertEquals(SgeIdade.Faixa.ANTIGA, SgeIdade.faixa(24))
        assertEquals("há 3 h", SgeIdade.texto(3)); assertEquals("há 3 dias", SgeIdade.texto(80))
    }
    @Test fun dataFuturaVoltaParaOAnoAnteriorENaoQuebraComTextoInvalido() {
        assertEquals(2025, SgeIdade.parse("30/12 10:00", agora)!!.year); assertNull(SgeIdade.parse("sem data", agora)); assertNull(SgeIdade.parse("31/02 10:00", agora))
    }
    @Test fun cartaoUsaAConsultaMaisAntiga() {
        assertEquals(30L, SgeIdade.maisAntiga(listOf(r("1", "Química"), r("2", "Física", "01/11 09:00")), agora))
    }
    @Test fun buscaGlobalExigeTodosOsTermosSemAcentoENomesDeProfessor() {
        val texto = "33.01 Química Maria da Silva"
        assertTrue(SgeBusca.combina("quimica 33.01", texto)); assertTrue(SgeBusca.combina("MARIA silva", texto))
        assertFalse(SgeBusca.combina("fisica", texto)); assertTrue(SgeBusca.combina("", texto))
    }
    @Test fun indiceDeProfessoresUsaVinculosELocais() {
        val ind = SgeBusca.indiceProfessores(listOf(VinculoDocente("1", "Química", DocenteRef("9", "Ana Costa"))), emptyMap())
        assertEquals("Ana Costa", ind(r("1", "Química"))); assertEquals("", ind(r("2", "Química")))
    }
    @Test fun csvTemBomPontoEVirgulaEEscapaAspas() {
        val g = GrupoPendencias("p", "João \"Zé\"", "x", listOf(r("33.01", "Química", ms = listOf(mod(2), mod(0), ResultadoModulo(0, "A periodicidade precisa ser definida pelo professor"), mod(0), mod(null)))), setOf("33.01"))
        val csv = SgeCsv.gerar(listOf(g), agora)
        assertTrue(csv.startsWith("\uFEFF\"Professor\";\"Turma\";\"Componente\""))
        val linha = csv.trimEnd().lines().last()
        assertTrue(linha.contains("\"João \"\"Zé\"\"\"")); assertTrue(linha.contains("\"n/c\"")); assertTrue(linha.contains("\"Pendências\"")); assertTrue(linha.contains("\"1\""))
    }
    @Test fun sgeMudouSoQuandoMuitasTelasNaoReconhecidasEFalhaNaoForDeRede() {
        val estrutura = List(5) { mod(null, false, "Não verificado: Listagem ausente") }
        assertTrue(SgeSaude.sgeMudou(listOf(r("1", "Química", ms = estrutura))))
        assertFalse(SgeSaude.sgeMudou(listOf(r("1", "Química", ms = List(5) { mod(null, false, "Não verificado: tempo esgotado") }))))
        assertFalse(SgeSaude.sgeMudou(listOf(r("1", "Química", ms = List(5) { mod(0) }))))
        assertFalse(SgeSaude.sgeMudou(listOf(r("1", "Química", ms = estrutura.take(3) + List(2) { mod(0) }))))
    }
}

package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test

class SgeDashboardTest {
    private fun r(id: String, disciplina: String = "Química", primeiro: ResultadoModulo = ResultadoModulo(0, coberturaCompleta = true)) =
        ResultadoTurma(TurmaMonitorada(id, "$id/EM", "Escola", disciplina), listOf(primeiro) + List(4) { ResultadoModulo(0, coberturaCompleta = true) }, "01/11 10:00")
    @Test fun desconhecidoNaoViraZeroNoResumo() {
        val m = SgeDashboard.resumir(listOf(r("1", primeiro = ResultadoModulo())))[0]
        assertNull(m.quantidade); assertEquals("—", m.valor()); assertFalse(m.completa)
    }
    @Test fun valorConhecidoSomadoContinuaParcialQuandoFaltaUmComponente() {
        val m = SgeDashboard.resumir(listOf(r("1", primeiro = ResultadoModulo(2, coberturaCompleta = true)), r("2", primeiro = ResultadoModulo())))[0]
        assertEquals(2, m.quantidade); assertEquals("2*", m.valor()); assertEquals(1, m.aConferir)
    }
    @Test fun zeroParcialNaoRecebeEmDia() {
        val g = SgeDashboard.agrupar(listOf(r("1", primeiro = ResultadoModulo(0))), Agrupamento.TURMA).single()
        assertEquals(EstadoGrupo.CONFERIR, g.estado)
    }
    @Test fun pendenciaConhecidaNaoEscondeNecessidadeDeConferencia() {
        val g = SgeDashboard.agrupar(listOf(r("1", primeiro = ResultadoModulo(2))), Agrupamento.TURMA).single()
        assertEquals(EstadoGrupo.PENDENTE, g.estado); assertTrue(g.exigeConferencia)
    }
    @Test fun aguardandoAvaliacaoFicaForaDasPendencias() {
        val g = SgeDashboard.agrupar(listOf(r("1", primeiro = ResultadoModulo(0, aguardando = 6, coberturaCompleta = true))), Agrupamento.TURMA).single()
        assertEquals(EstadoGrupo.EM_DIA, g.estado); assertEquals(6, g.modulos[0].aguardando)
    }
    @Test fun docentesCompartilhadosNaoDuplicamResultado() {
        val rs = listOf(r("1", primeiro = ResultadoModulo(3, coberturaCompleta = true)))
        val links = listOf(VinculoDocente("1", "Química", DocenteRef("a", "Ana")), VinculoDocente("1", "Química", DocenteRef("b", "Bia")))
        val gs = SgeDashboard.agrupar(rs, Agrupamento.PROFESSOR, links)
        assertEquals(1, gs.size); assertTrue(gs.single().compartilhado); assertEquals(3, gs.single().modulos[0].quantidade)
    }
    @Test fun professorDesconhecidoFicaEmGrupoProprio() {
        val gs = SgeDashboard.agrupar(listOf(r("1"), r("2")), Agrupamento.PROFESSOR,
            listOf(VinculoDocente("1", "Química", DocenteRef("a", "Ana"))))
        assertEquals(2, gs.size); assertEquals(1, gs.single { it.chave == "professor:desconhecido" }.resultados.size)
    }
    @Test fun vinculoLocalTemPrioridadeSemAlterarResultadoOriginal() {
        val resultado = r("1")
        val gs = SgeDashboard.agrupar(listOf(resultado), Agrupamento.PROFESSOR,
            listOf(VinculoDocente("1", "Química", DocenteRef("a", "Ana"))),
            mapOf(chaveResultado(resultado) to listOf(DocenteRef("b", "Bia"))))
        assertEquals("Bia", gs.single().titulo); assertTrue(gs.single().vinculoLocal); assertSame(resultado, gs.single().resultados.single())
    }
    @Test fun mesmoNomeComIdsDiferentesNaoMesclaProfessores() {
        val gs = SgeDashboard.agrupar(listOf(r("1"), r("2")), Agrupamento.PROFESSOR,
            listOf(VinculoDocente("1", "Química", DocenteRef("a", "Ana")), VinculoDocente("2", "Química", DocenteRef("b", "Ana"))))
        assertEquals(2, gs.size)
    }
    @Test fun disciplinasDaMesmaTurmaFicamNoMesmoCartao() {
        val gs = SgeDashboard.agrupar(listOf(r("1"), r("1", "Física")), Agrupamento.TURMA)
        assertEquals(1, gs.size); assertEquals(2, gs.single().resultados.size)
    }
    @Test fun atualizacaoMantemCacheValidoAteChegarResultadoNovo() {
        val antiga = r("1", primeiro = ResultadoModulo(5, coberturaCompleta = true))
        val outra = r("2")
        val nova = r("1", primeiro = ResultadoModulo(2, coberturaCompleta = true))
        assertEquals(listOf(antiga), SgeDashboard.progresso(listOf(antiga, outra), emptyList(), setOf(chaveResultado(antiga))))
        assertEquals(listOf(nova), SgeDashboard.progresso(listOf(antiga, outra), listOf(nova), setOf(chaveResultado(antiga))))
    }
    @Test fun professoresLocaisFicamDisponiveisParaOutrasTurmasComOMesmoId() {
        val docente = DocenteRef("local:1", "Docente local")
        val p = PainelDocentes(locais = mapOf("turma1|quimica" to listOf(docente), "turma2|fisica" to listOf(docente)))
        assertEquals(listOf(docente), p.opcoesDocentes())
    }
    @Test fun linhasRepetidasNaoInflamTotais() {
        val resultado = r("1", primeiro = ResultadoModulo(2, coberturaCompleta = true))
        assertEquals(2, SgeDashboard.resumir(listOf(resultado, resultado))[0].quantidade)
    }
    @Test fun comparaSomenteVinculosIdenticosEContagensCompletas() {
        val antes = r("1", primeiro = ResultadoModulo(4, coberturaCompleta = true))
        val depois = r("1", primeiro = ResultadoModulo(2, coberturaCompleta = true))
        val mudanca = SgeComparacao.calcular(listOf(antes), listOf(depois)).single { it.indice == 0 }
        assertEquals(-2, mudanca.delta)
        assertEquals(emptyList<MudancaModulo>(), SgeComparacao.calcular(listOf(antes), listOf(r("1", "Física"))))
        assertFalse(SgeComparacao.calcular(listOf(antes), listOf(r("1", primeiro = ResultadoModulo(0)))).any { it.indice == 0 })
        assertFalse(SgeComparacao.calcular(listOf(antes.copy(modulos = listOf(ResultadoModulo(0, detalhe = "periodicidade precisa ser definida")) + antes.modulos.drop(1))), listOf(depois)).any { it.indice == 0 })
    }
    @Test fun comparaModulosSeparadamenteEIncluiIgualdadeSomenteNoResumo() {
        val antes = r("1", primeiro = ResultadoModulo(3, coberturaCompleta = true))
        val depois = r("1", primeiro = ResultadoModulo(1, coberturaCompleta = true))
        val mudancas = SgeComparacao.calcular(listOf(antes), listOf(depois))
        assertEquals(-2, mudancas.single { it.indice == 0 }.delta)
        assertEquals(0, mudancas.single { it.indice == 1 }.delta)
        assertTrue(mudancas.filter { it.indice >= 2 }.all { it.anterior == it.atual })
    }
}

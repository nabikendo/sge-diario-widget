package br.to.seduc.sgediariowidget.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SgeOrganizacaoTest {
    private fun resultado(id: String, nome: String) = ResultadoTurma(
        TurmaMonitorada(id, nome, "Escola", "Química"),
        List(5) { ResultadoModulo(0, coberturaCompleta = true) },
        "01/11 10:00"
    )

    @Test fun filtroDeRevisaoIncluiCandidatosAmbiguosSemTrocarVinculoAutomaticamente() {
        val docente = DocenteRef("d1", "Ana")
        val ambiguidade = DiagnosticoHorario(
            professorId = "d2", professorNome = "Bia", turmaHorario = "13.01", componente = "Química",
            candidatos = listOf("t1" to "13.01/EM", "t2" to "13.01/EJA"), tipo = "ambiguo"
        )
        // t2 possui um vínculo válido de outro docente; isso não elimina a necessidade de revisar o conflito.
        val painel = PainelDocentes(
            vinculos = listOf(VinculoDocente("t2", "Química", docente)),
            diagnosticos = listOf(ambiguidade)
        )
        val revisao = SgeOrganizacao.filtrar(
            listOf(resultado("t1", "13.01/EM"), resultado("t2", "13.01/EJA")),
            FiltroAvancado(qualificador = "sem_vinculo"), emptyMap(), painel
        )
        assertEquals(setOf("t1", "t2"), revisao.map { it.turma.id }.toSet())
        assertEquals(listOf(docente), SgeOrganizacao.professores(revisao.single { it.turma.id == "t2" }, painel))
    }

    @Test fun progressoDaFilaIncluiIgnoradasConcluidasEFalhasSemExcederCemPorCento() {
        val fila = FilaCoord(
            ids = listOf("em-andamento", "pendente"), total = 4, pausada = false, puladas = 1,
            mediaMs = 60_000L, concluidas = listOf("ok"), falhas = listOf("falhou")
        )
        assertEquals(3, fila.resolvidas)
        assertEquals(.75f, fila.progresso, .001f)
        assertEquals(1f, fila.copy(concluidas = listOf("1", "2", "3", "4", "5")).progresso, .001f)
    }
}

package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test

class SgeDocentesParsingTest {
    @Test fun professoresVemDeOptionsComIdsReais() {
        val ps = SgeDocentesParsing.professores("<select id='ProfessorId'><option value=''>Selecione</option><option value='a'>Ana</option><option value='b'>Bia</option></select>")
        assertEquals(listOf("a", "b"), ps.map { it.id })
    }
    @Test fun horarioAssociaSomenteTurmaUnicaComComponenteExplicito() {
        val html = "<table><tr><td>07:00 às 07:50</td><td>QUÍMICA</td><td>13.01</td></tr><tr><td>08:00 às 08:50</td><td>FÍSICA</td><td>Outra</td></tr></table>"
        val vs = SgeDocentesParsing.vinculos(html, DocenteRef("a", "Ana"), listOf("t1" to "13.01/EM"))
        assertEquals(1, vs.size); assertEquals("t1", vs.single().turmaId); assertEquals("QUÍMICA", vs.single().componente)
    }
    @Test fun nomesDeTurmaAmbiguosNaoGeramVinculo() {
        val html = "<table><tr><td>07:00 às 07:50</td><td>Química</td><td>13.01</td></tr></table>"
        assertTrue(SgeDocentesParsing.vinculos(html, DocenteRef("a", "Ana"), listOf("t1" to "13.01/EM", "t2" to "13.01/EJA")).isEmpty())
    }
    @Test(expected = IllegalStateException::class) fun paginaSemHorarioNaoApagaVinculosComoSeFosseVazia() {
        SgeDocentesParsing.vinculos("<div>Sessão indisponível</div>", DocenteRef("a", "Ana"), listOf("t1" to "13.01"))
    }
    @Test fun tipoVemDaSelecaoDoFormulario() {
        assertEquals("real", SgeDocentesParsing.tipoHorario("<select id='Tipo'><option value='x'>X</option><option value='real' selected>Ativo</option></select>"))
    }
}

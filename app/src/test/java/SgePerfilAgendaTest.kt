package br.to.seduc.sgediariowidget.data

import br.to.seduc.sgediariowidget.worker.SgeAgendaNoturna
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class SgePerfilAgendaTest {
    @Test fun perfilUsaFlagsExplicitas() {
        assertEquals(PerfilSge.COORDENACAO, SgePerfil.ler("<input id='ehPerfilEscola' value='true'><input id='ehPerfilProfessor' value='false'>"))
        assertEquals(PerfilSge.PROFESSOR, SgePerfil.ler("<input id='ehPerfilEscola' value='false'><input id='ehPerfilProfessor' value='true'>"))
    }
    @Test fun seletorDeProfessoresNaoComprovaPerfil() {
        assertEquals(PerfilSge.DESCONHECIDO, SgePerfil.ler("<select id='ProfessorId'>" + "<option>Professor</option>".repeat(21) + "</select>"))
        assertEquals(PerfilSge.DESCONHECIDO, SgePerfil.ler("<input id='ehPerfilEscola' value='true'><input id='ehPerfilProfessor' value='true'>"))
    }
    @Test fun horarioNoturnoUsaFusoDoAparelhoEProximoDia() {
        assertEquals(60 * 60 * 1000L, SgeAgendaNoturna.atraso(ZonedDateTime.parse("2026-11-01T02:00:00-03:00[America/Fortaleza]")))
        assertEquals(24 * 60 * 60 * 1000L, SgeAgendaNoturna.atraso(ZonedDateTime.parse("2026-11-01T03:00:00-03:00[America/Fortaleza]")))
        assertEquals(23 * 60 * 60 * 1000L, SgeAgendaNoturna.atraso(ZonedDateTime.parse("2026-12-31T04:00:00Z[UTC]")))
    }
    @Test fun horarioEscolhidoUsaProximaOcorrenciaNoFusoLocal() {
        assertEquals(2*60*60*1000L,SgeAgendaNoturna.atraso(ZonedDateTime.parse("2026-11-01T02:00:00-03:00[America/Fortaleza]"),4))
    }

}

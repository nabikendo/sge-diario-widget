package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test

class SgeRelatorioTextoTest {
    @Test fun relatorioIncluiTurmaSemExporDetalhesDeAlunos() {
        val r = ResultadoTurma(TurmaMonitorada("1", "13.01/EM", "", "Química"), listOf(
            ResultadoModulo(2, coberturaCompleta = true), ResultadoModulo(),
            ResultadoModulo(0, "ALUNO PRIVADO", aguardando = 3), ResultadoModulo(), ResultadoModulo()), "01/11 10:00")
        val g = SgeDashboard.agrupar(listOf(r), Agrupamento.TURMA).single()
        val texto = SgeRelatorioTexto.gerar(g.copy(resultados = listOf(r, r)))
        assertTrue(texto.contains("Frequência: —")); assertTrue(texto.contains("Plano de aula: 0* documentos; aguardando avaliação: 3"))
        assertFalse(texto.contains("ALUNO PRIVADO"))
        assertEquals(1, texto.split("13.01/EM · Química").size - 1)
        assertTrue(texto.lineSequence().filter { it.startsWith("Conteúdos:") }.all { !it.contains("aguardando") })
    }

    @Test fun mensagemProfessorDestacaPendenciasConfirmadasSemDadosDeAlunos() {
        val r = ResultadoTurma(TurmaMonitorada("1", "13.01/EM", "", "Química"), listOf(
            ResultadoModulo(1, coberturaCompleta = true), ResultadoModulo(0, coberturaCompleta = true),
            ResultadoModulo(2, coberturaCompleta = true), ResultadoModulo(0, coberturaCompleta = true),
            ResultadoModulo(0, "NOME DE ALUNO", coberturaCompleta = true)), "01/11 10:00")
        val grupo = SgeDashboard.agrupar(listOf(r), Agrupamento.TURMA).single()
            .copy(chave = "professor:42", titulo = "Ana Silva")
        val mensagem = SgeRelatorioTexto.mensagemProfessor(grupo)
        assertTrue(mensagem.contains("Olá, Prof. Ana Silva"))
        assertTrue(mensagem.contains("13.01/EM · Química"))
        assertTrue(mensagem.contains("Plano de aula: 2 documentos pendente(s)"))
        assertFalse(mensagem.contains("NOME DE ALUNO"))
    }
}

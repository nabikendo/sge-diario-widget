package br.to.seduc.sgediariowidget.data

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SgeConferenciaSemanalTest {
    private val segunda = LocalDate.of(2026, 11, 9)
    private val turma = TurmaMonitorada("1", "23.01", "Escola", "QUÍMICA")

    @Test fun distingueDatasComPendenciaRegistroEConsultaIncompleta() {
        val conteudo = ResultadoModulo(1, coberturaCompleta = true,
            datas = DatasModulo(segunda, segunda.plusDays(3), listOf(segunda), listOf(segunda.plusDays(1))))
        val resultado = ResultadoTurma(turma, listOf(conteudo), "")
        val painel = CoordPainel(listOf(CoordTurma(turma.id, turma.nome, listOf(resultado))))
        val prof = DocenteRef("2", "Pessoa docente")
        val docentes = PainelDocentes(vinculos = listOf(VinculoDocente(turma.id, turma.disciplina, prof)))
        val grupo = SgeConferenciaSemanal.montar(painel, emptyList(), docentes, segunda, segunda.plusDays(3)).single()
        assertEquals(prof.nome, grupo.professor)
        assertEquals(segunda, grupo.linhas.single { it.situacao == SituacaoSemanal.PENDENTE }.data)
        assertEquals(segunda.plusDays(1), grupo.linhas.single { it.situacao == SituacaoSemanal.REVISAR }.data)
        assertTrue(grupo.linhas.any { it.modulo == "Frequência" && it.situacao == SituacaoSemanal.INCOMPLETA })
        assertTrue(SgeConferenciaSemanal.texto(grupo, segunda).contains("09/11/2026"))
    }

    @Test fun comparacaoDistingueDatasQueMudaramMesmoComContagemIgual() {
        fun amostra(dia: LocalDate, pendente: LocalDate, consulta: Long): DiaHistorico {
            val m = ResultadoModulo(1, coberturaCompleta = true, atualizadoMs = consulta,
                datas = DatasModulo(segunda, dia, listOf(pendente)))
            return DiaHistorico(dia, FontePainel.COORDENACAO, listOf(
                MedicaoHistorica(chaveVinculo(turma.id, turma.disciplina), turma.id, listOf(m), turma.nome)))
        }
        val anterior = amostra(segunda, segunda, 1)
        val atual = amostra(segunda.plusDays(1), segunda.plusDays(1), 2)
        val linhas = compararConsultas(anterior, atual).filter { it.indice == 0 }
        assertEquals(listOf(TipoVariacaoConsulta.NOVAS, TipoVariacaoConsulta.RESOLVIDAS), linhas.map { it.tipo })
        assertEquals(listOf("10/11/2026", "09/11/2026"), linhas.map { it.descricao })
        val ausente = atual.copy(medicoes = emptyList())
        assertEquals(TipoVariacaoConsulta.SEM_VERIFICACAO, compararConsultas(anterior, ausente).first().tipo)
    }

    @Test fun naoAfirmaResolucaoQuandoMudouRecorteDaConsulta() {
        val m1 = ResultadoModulo(1, coberturaCompleta = true, atualizadoMs = 1,
            datas = DatasModulo(segunda, segunda, listOf(segunda)))
        val m2 = ResultadoModulo(0, coberturaCompleta = true, atualizadoMs = 2,
            datas = DatasModulo(segunda.plusDays(1), segunda.plusDays(1)))
        fun amostra(dia: LocalDate, m: ResultadoModulo) = DiaHistorico(dia, FontePainel.PESSOAL,
            listOf(MedicaoHistorica(chaveVinculo(turma.id, turma.disciplina), turma.id, listOf(m), turma.nome)))
        val linhas = compararConsultas(amostra(segunda, m1), amostra(segunda.plusDays(1), m2))
        assertEquals(TipoVariacaoConsulta.SEM_VERIFICACAO, linhas.first { it.indice == 0 }.tipo)
    }

    @Test fun apontaComponenteSemConsultaMesmoComOutraDisciplinaConsultada() {
        val resultado = ResultadoTurma(turma, emptyList(), "")
        val painel = CoordPainel(listOf(CoordTurma(turma.id, turma.nome, listOf(resultado))))
        val prof = DocenteRef("2", "Pessoa docente")
        val docentes = PainelDocentes(vinculos = listOf(VinculoDocente(turma.id, "FÍSICA", prof)))
        val grupo = SgeConferenciaSemanal.montar(painel, emptyList(), docentes, segunda, segunda).first { it.id == prof.id }
        assertEquals("FÍSICA", grupo.linhas.single().componente)
        assertEquals(SituacaoSemanal.INCOMPLETA, grupo.linhas.single().situacao)
    }

    @Test fun variacaoDeDocumentosMostraSoSaldoEExigeDataDeConsulta() {
        fun amostra(dia: LocalDate, quantidade: Int, consulta: Long): DiaHistorico {
            val m = ResultadoModulo(quantidade, coberturaCompleta = true, atualizadoMs = consulta)
            return DiaHistorico(dia, FontePainel.PESSOAL, listOf(
                MedicaoHistorica(chaveVinculo(turma.id, turma.disciplina), turma.id, List(5) { m }, turma.nome)))
        }
        val atual = amostra(segunda.plusDays(1), 1, 2)
        val tipo = compararConsultas(amostra(segunda, 3, 1), atual).single { it.indice == 2 }.tipo
        assertEquals(TipoVariacaoConsulta.SALDO_DOCUMENTOS, tipo)
        val legado = compararConsultas(amostra(segunda, 3, 0), atual).single { it.indice == 2 }.tipo
        assertEquals(TipoVariacaoConsulta.SEM_VERIFICACAO, legado)
    }
}

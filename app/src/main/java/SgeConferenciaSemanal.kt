package br.to.seduc.sgediariowidget.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Somente datas que o SGE devolveu para a consulta. Não representa o horário oficial completo. */
data class DatasModulo(
    val inicio: LocalDate?,
    val fim: LocalDate,
    val pendentes: List<LocalDate> = emptyList(),
    val registradas: List<LocalDate> = emptyList()
) {
    fun json() = JSONObject().put("inicio", inicio?.toString()).put("fim", fim.toString())
        .put("pendentes", JSONArray(pendentes.map(LocalDate::toString)))
        .put("registradas", JSONArray(registradas.map(LocalDate::toString)))

    companion object {
        fun ler(j: JSONObject): DatasModulo? = runCatching {
            fun datas(chave: String) = j.optJSONArray(chave)?.let { a ->
                (0 until a.length()).map { LocalDate.parse(a.getString(it)) }.distinct().sorted()
            }.orEmpty()
            val inicio = j.optString("inicio").takeIf(String::isNotBlank)?.let(LocalDate::parse)
            DatasModulo(inicio, LocalDate.parse(j.getString("fim")), datas("pendentes"), datas("registradas"))
        }.getOrNull()
    }
}

enum class SituacaoSemanal(val titulo: String) {
    PENDENTE("Pendência confirmada"),
    INCOMPLETA("Consulta incompleta"),
    REVISAR("Registro a revisar")
}

data class LinhaSemanal(
    val turma: String,
    val componente: String,
    val modulo: String,
    val situacao: SituacaoSemanal,
    val data: LocalDate?,
    val descricao: String
)

data class GrupoSemanal(val id: String, val professor: String, val linhas: List<LinhaSemanal>)

object SgeConferenciaSemanal {
    val formato: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    fun inicio(data: LocalDate): LocalDate = data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    const val nota = "Usa as datas retornadas pelo SGE e os vínculos docentes atuais. Confira o horário oficial, o plano de aula, o PEI e a habilidade da matriz em suas fontes. As datas ausentes exigem conferência na origem."

    fun montar(coord: CoordPainel, conhecidas: List<Pair<String, String>>, docentes: PainelDocentes,
               semana: LocalDate, hoje: LocalDate = LocalDate.now()): List<GrupoSemanal> {
        val primeiro = inicio(semana)
        val ultimo = minOf(primeiro.plusDays(6), hoje)
        val periodo = primeiro..ultimo
        val resultados = coord.turmas.flatMap { it.disciplinas }.distinctBy(::chaveResultado)
        val linhas = mutableListOf<Pair<DocenteRef?, LinhaSemanal>>()
        resultados.forEach { r ->
            val professores: List<DocenteRef?> = SgeOrganizacao.professores(r, docentes).let { it.ifEmpty { listOf(null) } }
            val compartilhada = professores.size > 1
            fun adicionar(i: Int, estado: SituacaoSemanal, data: LocalDate?, texto: String) {
                val vinculo = if (compartilhada) " Docência compartilhada." else ""
                val linha = LinhaSemanal(r.turma.nome, r.turma.disciplina, SgeDashboard.nomes[i], estado, data, texto + vinculo)
                professores.forEach { linhas += it to linha }
            }
            SgeDashboard.nomes.indices.forEach { i ->
                val m = r.modulos.getOrNull(i)
                val datas = m?.datas
                if (i <= 1) {
                    val antigas = if (datas == null && m != null && (m.quantidade ?: 0) > 0 &&
                        (m.detalhe.startsWith("Dias pendentes no mapa") || m.detalhe.startsWith("Dias do filtro oficial")))
                        SgeMapaDias.diasPendentes(m.detalhe).second else emptyList()
                    val pendentes = datas?.pendentes ?: antigas
                    pendentes.filter { it in periodo }.forEach { dia ->
                        adicionar(i, SituacaoSemanal.PENDENTE, dia,
                            if (i == 0) "Dia com aula sem registro de conteúdo." else "Frequência não concluída no filtro do SGE.")
                    }
                    if (i == 0) datas?.registradas.orEmpty().filter { it in periodo && it !in pendentes }.forEach { dia ->
                        adicionar(i, SituacaoSemanal.REVISAR, dia, "Há conteúdo lançado. Confira a habilidade e a coerência com a matriz e o plano.")
                    }
                    if (m == null || !m.coberturaCompleta || datas?.inicio == null || datas.inicio.isAfter(primeiro) || datas.fim.isBefore(ultimo)) {
                        adicionar(i, SituacaoSemanal.INCOMPLETA, null, "A consulta salva não confirma cobertura de toda a semana.")
                    }
                } else {
                    val consultado = m?.atualizadoMs?.takeIf { it > 0L }?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    val qtd = m?.quantidade
                    when {
                        m == null || consultado == null || consultado !in periodo || qtd == null ->
                            adicionar(i, SituacaoSemanal.INCOMPLETA, null, "Sem consulta identificada nesta semana.")
                        !m.coberturaCompleta ->
                            adicionar(i, SituacaoSemanal.INCOMPLETA, consultado, "Consulta de documentos com cobertura parcial (${m.resumo()}).")
                        qtd > 0 ->
                            adicionar(i, SituacaoSemanal.PENDENTE, consultado, "$qtd documento(s) pendente(s) na data da consulta.")
                        else ->
                            adicionar(i, SituacaoSemanal.REVISAR, consultado, "Situação do envio conferida. Revise a adequação do documento quando aplicável.")
                    }
                }
            }
        }
        val todasTurmas = conhecidas.toMap() + coord.turmas.associate { it.id to it.nome }
        val chavesConsultadas = resultados.map(::chaveResultado).toSet()
        val turmasConsultadas = resultados.map { it.turma.id }.toSet()
        val falhas = coord.turmas.filter { it.erro != null }.map { it.id }.toSet()
        val vinculosPorTurma = docentes.vinculos.groupBy { it.turmaId }
        todasTurmas.forEach { (id, turma) ->
            val vinculos = vinculosPorTurma[id].orEmpty().groupBy { chaveVinculo(id, it.componente) }
            val naoConsultados = vinculos.filterKeys { it !in chavesConsultadas }
            if (naoConsultados.isEmpty() && (id !in turmasConsultadas || id in falhas)) {
                linhas += null to LinhaSemanal(turma, "", "Consulta da turma", SituacaoSemanal.INCOMPLETA, null,
                    "Há dados da turma sem verificação.")
            }
            naoConsultados.forEach { (chave, porComponente) ->
                val professores: List<DocenteRef?> = (docentes.locais[chave] ?: porComponente.map { it.docente })
                    .distinctBy { it.id }.ifEmpty { listOf(null) }
                professores.forEach { professor ->
                    linhas += professor to LinhaSemanal(turma, porComponente.first().componente, "Consulta da turma",
                        SituacaoSemanal.INCOMPLETA, null, "Componente sem consulta salva.")
                }
            }
        }
        return linhas.groupBy({ it.first }, { it.second }).map { (prof, linhas) ->
            GrupoSemanal(prof?.id.orEmpty(), prof?.nome ?: "Professor não identificado", linhas.distinct().sortedWith(
                compareBy({ it.situacao.ordinal }, { it.data }, { it.turma }, { it.componente }, { it.modulo })))
        }.sortedWith(compareBy({ it.id.isEmpty() }, { normalizarSge(it.professor) }))
    }

    fun texto(grupo: GrupoSemanal, semana: LocalDate): String = buildString {
        val primeira = inicio(semana)
        appendLine("Conferência semanal · ${primeira.format(formato)} a ${primeira.plusDays(6).format(formato)}")
        appendLine(grupo.professor)
        grupo.linhas.groupBy { it.situacao }.forEach { (estado, itens) ->
            appendLine()
            appendLine(estado.titulo)
            itens.forEach { linha ->
                val dia = linha.data?.format(formato)?.let { " • $it" }.orEmpty()
                appendLine("• ${linha.turma} ${linha.componente} · ${linha.modulo}$dia. ${linha.descricao}")
            }
        }
        appendLine()
        append(nota)
    }
}

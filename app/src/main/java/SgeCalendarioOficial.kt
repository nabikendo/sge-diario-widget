package br.to.seduc.sgediariowidget.data

import java.time.LocalDate

/** Calendário oficial lido do TEXTO da tela Calendário Escolar do SGE (não do JSON). */
data class BimestreOficial(val numero: Int, val inicio: LocalDate, val fim: LocalDate, val diasLetivos: Int)
data class FeriadoOficial(val data: LocalDate, val nome: String)
data class CalendarioOficial(
    val bimestres: List<BimestreOficial>,
    val feriados: List<FeriadoOficial>,
    val diasLetivosPorMes: Map<Int, Int>,
) {
    val totalDiasLetivos: Int get() = bimestres.sumOf { it.diasLetivos }
    /** Dia letivo oficial: não é feriado/recesso e cai dentro de um bimestre com dias letivos. */
    fun ehDiaLetivo(d: LocalDate): Boolean =
        bimestres.any { !d.isBefore(it.inicio) && !d.isAfter(it.fim) } &&
            feriados.none { it.data == d } && d.dayOfWeek !in setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)
}

object SgeCalendarioOficial {
    private val meses = mapOf("janeiro" to 1, "fevereiro" to 2, "março" to 3, "abril" to 4, "maio" to 5, "junho" to 6,
        "julho" to 7, "agosto" to 8, "setembro" to 9, "outubro" to 10, "novembro" to 11, "dezembro" to 12)
    private val reMes = Regex("""(\p{L}+) de (\d{4}) - (\d+) dias letivos""", RegexOption.IGNORE_CASE)
    private val reBimestre = Regex("""In[ií]cio - Fim (\d)º Bimestre: (\d{2}/\d{2}/\d{4}) à (\d{2}/\d{2}/\d{4})""")
    private val reDias = Regex("""Dias Letivos (\d)º Bimestre (\d+)""")
    private val reFeriado = Regex("""^(\d{2})/(\d{2}) - (.+)$""")

    private fun data(s: String): LocalDate = LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    /** Lê as partes que o app usa: dias letivos por mês, bimestres, feriados e recessos. Ignora o que não reconhece. */
    fun ler(texto: String): CalendarioOficial {
        val linhas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val porMes = linhas.mapNotNull { l ->
            reMes.find(l)?.let { m -> meses[m.groupValues[1].lowercase()]?.let { mes -> mes to m.groupValues[3].toInt() } }
        }.toMap()
        val inicios = linhas.mapNotNull { l -> reBimestre.find(l)?.let { m ->
            Triple(m.groupValues[1].toInt(), data(m.groupValues[2]), data(m.groupValues[3])) } }
        val dias = linhas.mapNotNull { l -> reDias.find(l)?.let { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() } }.toMap()
        val bimestres = inicios.map { (n, ini, fim) -> BimestreOficial(n, ini, fim, dias[n] ?: 0) }.sortedBy { it.numero }
        // Feriados ficam depois de "EVENTOS" e antes de "DIVISÃO DE BIMESTRE". Ano vem do próprio bimestre.
        val anoRef = bimestres.firstOrNull()?.inicio?.year ?: LocalDate.now().year
        val ini = linhas.indexOfFirst { it.equals("EVENTOS", ignoreCase = true) }
        val fimLista = linhas.indexOfFirst { it.startsWith("DIVISÃO DE BIMESTRE", ignoreCase = true) }
        val feriados = if (ini >= 0 && fimLista > ini) linhas.subList(ini + 1, fimLista).mapNotNull { l ->
            reFeriado.find(l)?.let { m -> runCatching { LocalDate.of(anoRef, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
                ?.let { FeriadoOficial(it, m.groupValues[3].trim()) } }
        } else emptyList()
        return CalendarioOficial(bimestres, feriados, porMes)
    }
}

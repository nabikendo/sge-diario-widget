package br.to.seduc.sgediariowidget.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Dias úteis aproximados: ignora sábados, domingos e feriados nacionais e do Tocantins.
 * Recessos e dias letivos específicos da escola não são conhecidos e não entram na conta.
 */
object SgeCalendario {
    private val cache = HashMap<Int, Set<LocalDate>>()
    fun pascoa(ano: Int): LocalDate {
        val a = ano % 19; val b = ano / 100; val c = ano % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val mes = (h + l - 7 * m + 114) / 31; val dia = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(ano, mes, dia)
    }
    fun feriados(ano: Int): Set<LocalDate> = cache.getOrPut(ano) {
        val p = pascoa(ano)
        val fixos = listOf(1 to 1, 18 to 3, 21 to 4, 1 to 5, 7 to 9, 5 to 10, 12 to 10, 2 to 11, 15 to 11, 20 to 11, 25 to 12)
            .map { (dia, mes) -> LocalDate.of(ano, mes, dia) }
        (fixos + listOf(p.minusDays(48), p.minusDays(47), p.minusDays(2), p.plusDays(60))).toSet()
    }
    /** Calendário oficial lido do SGE. Quando existe, substitui a estimativa de feriados. */
    @Volatile var oficial: CalendarioOficial? = null

    fun util(d: LocalDate): Boolean {
        val o = oficial
        if (o != null && o.bimestres.any { !d.isBefore(it.inicio) && !d.isAfter(it.fim) }) return o.ehDiaLetivo(d)
        return d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && d !in feriados(d.year)
    }

    /** Aviso para lançar justificativa numa data: null quando a data é dia letivo (ou o calendário oficial não foi lido). */
    fun avisoData(d: LocalDate): String? {
        val o = oficial ?: return null
        o.feriados.firstOrNull { it.data == d }?.let { return "Feriado ou recesso: ${it.nome}" }
        if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) return "Fim de semana"
        if (o.bimestres.none { !d.isBefore(it.inicio) && !d.isAfter(it.fim) }) return "Fora de bimestre letivo"
        return if (o.ehDiaLetivo(d)) null else "Fora de dia letivo"
    }
    /** Dias úteis transcorridos depois de [desde], até [ate] (inclusive). */
    fun diasUteisEntre(desde: LocalDate, ate: LocalDate): Int {
        var n = 0; var d = desde.plusDays(1)
        while (!d.isAfter(ate)) { if (util(d)) n++; d = d.plusDays(1) }
        return n
    }
}

package br.to.seduc.sgediariowidget.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Idade do dado de cada turma: o SGE Diário guarda "dd/MM HH:mm" da última consulta. */
object SgeIdade {
    enum class Faixa { RECENTE, MEDIA, ANTIGA }
    private val padrao = Regex("""(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{2})""")

    fun parse(s: String, agora: LocalDateTime): LocalDateTime? {
        val (d, mo, h, mi) = padrao.find(s)?.destructured ?: return null
        return runCatching {
            var t = LocalDateTime.of(agora.year, mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
            if (t.isAfter(agora.plusMinutes(1))) t = t.minusYears(1)
            t
        }.getOrNull()
    }
    fun horas(s: String, agora: LocalDateTime): Long? = parse(s, agora)?.let { Duration.between(it, agora).toHours().coerceAtLeast(0) }
    fun horas(timestampMs: Long, agoraMs: Long = System.currentTimeMillis()): Long? =
        timestampMs.takeIf { it > 0L }?.let { Duration.between(Instant.ofEpochMilli(it), Instant.ofEpochMilli(agoraMs)).toHours().coerceAtLeast(0) }
    fun dataHora(timestampMs: Long): String = Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    /** Verde até 6 h, âmbar até 24 h, vermelho acima disso. */
    fun faixa(horas: Long) = if (horas < 6) Faixa.RECENTE else if (horas < 24) Faixa.MEDIA else Faixa.ANTIGA
    fun texto(horas: Long) = if (horas < 1) "há menos de 1 h" else if (horas < 48) "há $horas h" else "há ${horas / 24} dias"
    /** Num cartão com várias consultas vale a mais antiga: é a que limita a confiança no resumo. */
    fun maisAntiga(rs: List<ResultadoTurma>, agora: LocalDateTime): Long? = rs.mapNotNull { horas(it.atualizado, agora) }.maxOrNull()
    fun maisRecente(rs: List<ResultadoTurma>, agora: LocalDateTime): Long? = rs.mapNotNull { horas(it.atualizado, agora) }.minOrNull()
    fun maisAntigaPorModulo(rs: List<ResultadoTurma>, agora: LocalDateTime): Long? = rs.flatMap { r ->
        (0..4).mapNotNull { i -> r.modulos.getOrNull(i)?.atualizadoMs?.takeIf { it > 0L }?.let { horas(it) }
            ?: horas(r.atualizado, agora) }
    }.maxOrNull()
}

/** Busca global: todos os termos precisam aparecer em turma, componente ou professor. */
object SgeBusca {
    fun indiceProfessores(vinculos: List<VinculoDocente>, locais: Map<String, List<DocenteRef>>): (ResultadoTurma) -> String {
        val porVinculo = vinculos.groupBy { chaveVinculo(it.turmaId, it.componente) }
        return { r -> (locais[chaveResultado(r)] ?: porVinculo[chaveResultado(r)].orEmpty().map { it.docente }).joinToString(" ") { it.nome } }
    }
    fun combina(consulta: String, texto: String): Boolean {
        val termos = normalizarSge(consulta).split(' ').filter { it.isNotBlank() }
        val alvo = normalizarSge(texto)
        return termos.all { alvo.contains(it) }
    }
}

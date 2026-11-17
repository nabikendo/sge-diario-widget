package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Preferências dos recursos extras (alertas, resumo, visões e economia de dados). Tudo desligado por padrão. */
data class ExtrasSge(
    val alertaAntiga: Boolean = false,
    val diasAntiga: Int = 7,
    val avisoZerou: Boolean = false,
    val resumoSemanal: Boolean = false,
    val lembreteBimestre: Boolean = false,
    val soMeu: Boolean = false,
    val economiaDados: Boolean = false,
)

object SgeExtras {
    private const val A = "extras_alerta_antiga"
    private const val D = "extras_dias_antiga"
    private const val Z = "extras_aviso_zerou"
    private const val R = "extras_resumo_semanal"
    private const val B = "extras_lembrete_bimestre"
    private const val M = "extras_so_meu"
    private const val E = "extras_economia_dados"
    val chaves = setOf(A, D, Z, R, B, M, E)
    val diasPossiveis = listOf(3, 7, 15, 30)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun ler(ctx: Context): ExtrasSge = prefs(ctx).let {
        ExtrasSge(it.getBoolean(A, false), it.getInt(D, 7).takeIf { d -> d in diasPossiveis } ?: 7, it.getBoolean(Z, false),
            it.getBoolean(R, false), it.getBoolean(B, false), it.getBoolean(M, false), it.getBoolean(E, false))
    }

    fun salvar(ctx: Context, e: ExtrasSge) {
        prefs(ctx).edit().putBoolean(A, e.alertaAntiga).putInt(D, e.diasAntiga.takeIf { it in diasPossiveis } ?: 7)
            .putBoolean(Z, e.avisoZerou).putBoolean(R, e.resumoSemanal).putBoolean(B, e.lembreteBimestre)
            .putBoolean(M, e.soMeu).putBoolean(E, e.economiaDados).apply()
    }
}

/** Regras puras (sem Android) dos alertas, para facilitar o teste. Resultado ausente ou parcial nunca vira "zerado". */
object SgeAlertas {
    private fun nome(r: ResultadoTurma) = r.turma.nome.substringBefore('/') + " · " + r.turma.disciplina

    private fun comPendencia(r: ResultadoTurma) = r.modulos.any { (it.quantidade ?: 0) > 0 }

    private fun emDiaConfirmado(r: ResultadoTurma) = r.modulos.isNotEmpty() &&
        r.modulos.all { it.quantidade == 0 && (it.coberturaCompleta || it.naoConfigurado) }

    /** Turmas/componentes que tinham pendência na consulta anterior e, agora, estão confirmadamente em dia. */
    fun turmasZeradas(antes: List<ResultadoTurma>, depois: List<ResultadoTurma>): List<String> {
        val anteriores = antes.associateBy(::chaveResultado)
        return depois.filter { emDiaConfirmado(it) && anteriores[chaveResultado(it)]?.let(::comPendencia) == true }
            .map(::nome).distinct().sorted()
    }

    /** Pendências de Conteúdos/Frequência cuja data mais antiga já passou de [dias]. Devolve texto pronto. */
    fun pendenciasAntigas(rs: List<ResultadoTurma>, hoje: LocalDate, dias: Int): List<String> =
        rs.distinctBy(::chaveResultado).mapNotNull { r ->
            val partes = listOf(0 to "Conteúdos", 1 to "Frequência").mapNotNull { (i, rotulo) ->
                val mais = r.modulos.getOrNull(i)?.datas?.pendentes?.minOrNull() ?: return@mapNotNull null
                val idade = ChronoUnit.DAYS.between(mais, hoje)
                if (idade >= dias) "$rotulo há $idade dias" else null
            }
            if (partes.isEmpty()) null else nome(r) + " — " + partes.joinToString(", ")
        }.sorted()

    /** Resumo curto por módulo, só com o que tem pendência. */
    fun resumoSemanal(rs: List<ResultadoTurma>): String {
        val unicos = rs.distinctBy(::chaveResultado)
        if (unicos.isEmpty()) return "Sem consulta recente. Abra o app e atualize o diário."
        val turmas = unicos.map { it.turma.nome.substringBefore('/') }.distinct()
        val pend = unicos.filter(::comPendencia)
        val emDia = unicos.count(::emDiaConfirmado)
        val modulos = (0..4).mapNotNull { i ->
            val n = unicos.count { (it.modulos.getOrNull(i)?.quantidade ?: 0) > 0 }
            if (n == 0) null else "${SgeDashboard.nomes[i]}: $n"
        }
        return buildString {
            append("${turmas.size} turma(s) consultada(s); ${pend.size} com pendência e $emDia em dia.")
            if (modulos.isNotEmpty()) append(" ").append(modulos.joinToString(" · "))
        }
    }

    /** Início aproximado dos bimestres (calendário estadual varia): 1º 01/02, 2º 20/04, 3º 27/07, 4º 05/10. */
    private val inicios = listOf(2 to 1, 4 to 20, 7 to 27, 10 to 5)

    /** Número do bimestre que começa em [dias] dias a partir de [hoje], ou null. */
    fun bimestreComecandoEm(hoje: LocalDate, dias: Long = 7): Int? =
        inicios.withIndex().firstOrNull { (_, mes) -> LocalDate.of(hoje.year, mes.first, mes.second).minusDays(dias) == hoje }?.index?.plus(1)
}

/** Motivos de falha em linguagem simples, a partir do texto técnico guardado em cada módulo. */
object SgeFalhas {
    fun motivo(detalhe: String): String {
        val d = detalhe.lowercase()
        return when {
            "reset" in d || "unexpected end" in d || "broken pipe" in d -> "A conexão com o SGE foi interrompida"
            "timeout" in d || "tempo" in d || "timed out" in d -> "O SGE demorou demais para responder"
            "unable to resolve" in d || "unknownhost" in d || "network" in d || "rede" in d -> "Sem conexão com a internet"
            "sessão" in d || "sessao" in d || "login" in d -> "A sessão do SGE expirou"
            "perfil" in d -> "O perfil ativo no SGE não é o esperado"
            else -> "O SGE não respondeu como esperado"
        }
    }

    /** Pares (motivo, quantidade de consultas) dos módulos não verificados. */
    fun resumo(rs: List<ResultadoTurma>): List<Pair<String, Int>> =
        rs.flatMap { r -> r.modulos.filter { it.quantidade == null } }
            .groupingBy { motivo(it.detalhe) }.eachCount().toList().sortedByDescending { it.second }
}

/** Pendências marcadas como feitas no widget. Valem até a próxima consulta: se o SGE ainda mostrar pendência, a marca cai. */
object SgeResolvidas {
    private const val CHAVE = "widget_resolvidas_v1"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun ler(ctx: Context): Set<String> = runCatching {
        val a = JSONArray(prefs(ctx).getString(CHAVE, "[]"))
        (0 until a.length()).map { a.getString(it) }.toSet()
    }.getOrDefault(emptySet())

    fun marcar(ctx: Context, chaveGrupo: String) {
        prefs(ctx).edit().putString(CHAVE, JSONArray((ler(ctx) + chaveGrupo).toList()).toString()).apply()
    }

    fun desmarcar(ctx: Context, chaveGrupo: String) {
        prefs(ctx).edit().putString(CHAVE, JSONArray((ler(ctx) - chaveGrupo).toList()).toString()).apply()
    }

    /** Chamado quando uma consulta nova termina: os dados novos substituem qualquer marca local. */
    fun limpar(ctx: Context) { prefs(ctx).edit().remove(CHAVE).apply() }
}

/** Lançamentos preparados sem internet. O app guarda o texto; o lançamento no SGE é feito por você. */
data class Rascunho(val id: String, val tipo: String, val turma: String, val data: String, val texto: String,
    val criadoMs: Long, val lancado: Boolean = false) {
    fun json() = JSONObject().put("id", id).put("tipo", tipo).put("turma", turma).put("data", data)
        .put("texto", texto).put("criadoMs", criadoMs).put("lancado", lancado)
}

object SgeRascunhos {
    private const val CHAVE = "rascunhos_v1"
    val tipos = listOf("Conteúdo", "Frequência")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun ler(ctx: Context): List<Rascunho> = runCatching {
        val a = JSONArray(prefs(ctx).getString(CHAVE, "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Rascunho(it.getString("id"), it.optString("tipo", "Conteúdo"), it.optString("turma"), it.optString("data"),
                it.optString("texto"), it.optLong("criadoMs"), it.optBoolean("lancado"))
        } }
    }.getOrDefault(emptyList())

    private fun gravar(ctx: Context, lista: List<Rascunho>) {
        prefs(ctx).edit().putString(CHAVE, JSONArray().also { a -> lista.forEach { a.put(it.json()) } }.toString()).apply()
    }

    fun adicionar(ctx: Context, tipo: String, turma: String, data: String, texto: String): List<Rascunho> {
        val novo = Rascunho(java.util.UUID.randomUUID().toString(), tipo, turma.trim(), data.trim(), texto.trim(), System.currentTimeMillis())
        return (ler(ctx) + novo).also { gravar(ctx, it) }
    }

    fun marcarLancado(ctx: Context, id: String, lancado: Boolean = true): List<Rascunho> =
        ler(ctx).map { if (it.id == id) it.copy(lancado = lancado) else it }.also { gravar(ctx, it) }

    fun remover(ctx: Context, id: String): List<Rascunho> = ler(ctx).filterNot { it.id == id }.also { gravar(ctx, it) }

    fun limparLancados(ctx: Context): List<Rascunho> = ler(ctx).filterNot { it.lancado }.also { gravar(ctx, it) }
}

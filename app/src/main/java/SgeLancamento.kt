package br.to.seduc.sgediariowidget

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
 * Lançamento de Conteúdo Aplicado a partir da Matriz de Recomposição (SEDUC-TO 2026).
 * O rascunho fica no aparelho; na sincronização o app preenche a janela do SGE via JavaScript
 * (assets/sge_automacao.js) e NUNCA toca em Salvar — o professor confere e salva.
 * Persistência segue o padrão do app: SharedPreferences + org.json.
 */

fun String.normalizarSge(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

// ───────────────────────── Modelos ─────────────────────────

enum class Componente(val rotulo: String) {
    QUIMICA("Química"), FISICA("Física"), BIOLOGIA("Biologia");

    companion object {
        /** "QUÍMICA" (texto do SGE) -> QUIMICA */
        fun deTexto(texto: String?): Componente? =
            texto?.normalizarSge()?.uppercase()?.let { t -> entries.firstOrNull { it.name == t } }
    }
}

data class Habilidade(
    val id: String,                    // ex.: 1-EM13CNT301-2 (o PDF repete códigos)
    val serie: String,
    val codigo: String,
    val unidadeTematica: String,
    val texto: String,
    val observacao: String?,
    val objetos: Map<Componente, List<String>>,
) {
    /** Texto para ItemDoAgrupador_2521. */
    val textoSge get() = "($codigo) $texto"

    /** No seletor: código + 1º objeto do componente (diferencia códigos repetidos). */
    fun rotulo(c: Componente) = "$codigo · " + (objetos[c]?.firstOrNull() ?: texto)
}

data class TurmaAutomacao(
    val id: String,
    val descricao: String,
    val grades: Map<Componente, String>,   // componente -> GradeEscolarId
) {
    /** 13.02 -> "1ª"; null quando não dá para deduzir (EJA, multisseriada…). */
    val serieDeduzida: String?
        get() = Regex("^\\s*([123])\\d\\.").find(descricao)?.groupValues?.get(1)?.let { "${it}ª" }
}

object StatusLancamento {
    const val PENDENTE = "PENDENTE"
    const val PREENCHIDO = "PREENCHIDO"   // janela preenchida; falta o professor tocar em Salvar
    const val ENVIADO = "ENVIADO"         // salvo e confirmado no mapa
    const val ERRO = "ERRO"
}

data class LancamentoMatriz(
    val id: String,
    val turmaId: String,
    val turmaDescricao: String,
    val gradeEscolarId: String,
    val componente: Componente,
    val dataIso: String,
    val serie: String,
    val habilidadeId: String,
    val objetoUm: String?,
    val objetoDois: String?,
    val outros: String?,
    val atividadesObs: String,
    val status: String = StatusLancamento.PENDENTE,
    val erro: String? = null,
    val criadoEm: Long = System.currentTimeMillis(),
) {
    fun json() = JSONObject()
        .put("id", id).put("turmaId", turmaId).put("turmaDescricao", turmaDescricao)
        .put("gradeEscolarId", gradeEscolarId).put("componente", componente.name).put("dataIso", dataIso)
        .put("serie", serie).put("habilidadeId", habilidadeId).put("objetoUm", objetoUm ?: JSONObject.NULL)
        .put("objetoDois", objetoDois ?: JSONObject.NULL).put("outros", outros ?: JSONObject.NULL)
        .put("atividadesObs", atividadesObs).put("status", status).put("erro", erro ?: JSONObject.NULL)
        .put("criadoEm", criadoEm)

    companion object {
        fun de(o: JSONObject) = LancamentoMatriz(
            id = o.getString("id"), turmaId = o.optString("turmaId"), turmaDescricao = o.optString("turmaDescricao"),
            gradeEscolarId = o.optString("gradeEscolarId"),
            componente = Componente.deTexto(o.optString("componente")) ?: Componente.QUIMICA,
            dataIso = o.optString("dataIso"), serie = o.optString("serie"), habilidadeId = o.optString("habilidadeId"),
            objetoUm = o.optString("objetoUm").takeIf { !o.isNull("objetoUm") && it.isNotBlank() },
            objetoDois = o.optString("objetoDois").takeIf { !o.isNull("objetoDois") && it.isNotBlank() },
            outros = o.optString("outros").takeIf { !o.isNull("outros") && it.isNotBlank() },
            atividadesObs = o.optString("atividadesObs"), status = o.optString("status", StatusLancamento.PENDENTE),
            erro = o.optString("erro").takeIf { !o.isNull("erro") && it.isNotBlank() },
            criadoEm = o.optLong("criadoEm"),
        )
    }
}

// ───────────────────────── Matriz (assets/matriz_cn.json) ─────────────────────────

object SgeMatriz {
    @Volatile private var cache: List<Habilidade>? = null

    fun todas(ctx: Context): List<Habilidade> = cache ?: synchronized(this) {
        cache ?: runCatching {
            val raiz = JSONObject(ctx.assets.open("matriz_cn.json").bufferedReader().use { it.readText() })
            val arr = raiz.getJSONArray("habilidades")
            (0 until arr.length()).map { i ->
                val h = arr.getJSONObject(i)
                val obj = h.optJSONObject("objetos") ?: JSONObject()
                Habilidade(
                    id = h.getString("id"), serie = h.getString("serie"), codigo = h.getString("codigo"),
                    unidadeTematica = h.optString("unidadeTematica"), texto = h.optString("texto"),
                    observacao = h.optString("observacao").takeIf { !h.isNull("observacao") && it.isNotBlank() },
                    objetos = Componente.entries.associateWith { obj.optJSONArray(it.name).strings() }
                        .filterValues { it.isNotEmpty() },
                )
            }
        }.getOrDefault(emptyList()).also { cache = it }
    }

    fun daSerie(ctx: Context, serie: String) = todas(ctx).filter { it.serie == serie }
    fun porId(ctx: Context, id: String) = todas(ctx).firstOrNull { it.id == id }
}

// ───────────────────────── Armazenamento local ─────────────────────────

private const val PREFS = "sge_lancamento_matriz"

object SgeLancamentosStore {
    private const val CHAVE = "lancamentos_v1"

    fun ler(ctx: Context): List<LancamentoMatriz> = runCatching {
        val arr = JSONArray(prefs(ctx).getString(CHAVE, "[]"))
        (0 until arr.length()).map { LancamentoMatriz.de(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList()).sortedWith(compareBy({ it.dataIso }, { it.criadoEm }))

    @Synchronized
    private fun gravar(ctx: Context, lista: List<LancamentoMatriz>) {
        prefs(ctx).edit().putString(CHAVE, JSONArray(lista.map { it.json() }).toString()).apply()
    }

    @Synchronized
    fun adicionar(ctx: Context, l: LancamentoMatriz) = (ler(ctx) + l).also { gravar(ctx, it) }

    @Synchronized
    fun atualizarStatus(ctx: Context, id: String, status: String, erro: String?) =
        ler(ctx).map { if (it.id == id) it.copy(status = status, erro = erro) else it }.also { gravar(ctx, it) }

    @Synchronized
    fun remover(ctx: Context, id: String) = ler(ctx).filterNot { it.id == id }.also { gravar(ctx, it) }

    @Synchronized
    fun limparEnviados(ctx: Context) = ler(ctx).filterNot { it.status == StatusLancamento.ENVIADO }.also { gravar(ctx, it) }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Turmas vistas no SGE com o GradeEscolarId de cada componente (vem do Mapa de Registros). */
object SgeTurmasAutomacao {
    private const val CHAVE = "turmas_v1"

    fun ler(ctx: Context): List<TurmaAutomacao> = runCatching {
        val o = JSONObject(prefs(ctx).getString(CHAVE, "{}"))
        o.keys().asSequence().map { id ->
            val t = o.getJSONObject(id)
            val g = t.optJSONObject("grades") ?: JSONObject()
            TurmaAutomacao(id, t.optString("descricao", id),
                g.keys().asSequence().mapNotNull { k -> Componente.deTexto(k)?.let { it to g.getString(k) } }.toMap())
        }.toList().sortedBy { it.descricao }
    }.getOrDefault(emptyList())

    /** Junta com o que já existia: uma turma pode mostrar só alguns componentes pendentes no mês. */
    @Synchronized
    fun registrar(ctx: Context, turmaId: String, descricao: String?, grades: Map<Componente, String>) {
        val o = runCatching { JSONObject(prefs(ctx).getString(CHAVE, "{}")) }.getOrDefault(JSONObject())
        val t = o.optJSONObject(turmaId) ?: JSONObject()
        val g = t.optJSONObject("grades") ?: JSONObject()
        grades.forEach { (c, id) -> g.put(c.name, id) }
        t.put("descricao", descricao?.takeIf { it.isNotBlank() } ?: t.optString("descricao", turmaId)).put("grades", g)
        o.put(turmaId, t)
        prefs(ctx).edit().putString(CHAVE, o.toString()).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Opções dos selects do SGE (Select2), capturadas com SgeAutomacao.exportarOpcoes(). */
object SgeOpcoesStore {
    private const val CHAVE = "opcoes_v1"

    fun salvar(ctx: Context, gradeEscolarId: String, campos: Map<String, List<Pair<String, String>>>) {
        val o = runCatching { JSONObject(prefs(ctx).getString(CHAVE, "{}")) }.getOrDefault(JSONObject())
        campos.forEach { (campo, ops) ->
            o.put("$gradeEscolarId|$campo", JSONArray(ops.map { (v, t) -> JSONObject().put("value", v).put("texto", t) }))
        }
        prefs(ctx).edit().putString(CHAVE, o.toString()).apply()
    }

    fun valuePorTexto(ctx: Context, gradeEscolarId: String, campo: String, texto: String): String? = runCatching {
        val arr = JSONObject(prefs(ctx).getString(CHAVE, "{}")).optJSONArray("$gradeEscolarId|$campo") ?: return null
        val alvo = texto.normalizarSge()
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .firstOrNull { it.optString("texto").normalizarSge() == alvo }?.optString("value")
    }.getOrNull()

    fun total(ctx: Context): Int = runCatching {
        val o = JSONObject(prefs(ctx).getString(CHAVE, "{}"))
        o.keys().asSequence().sumOf { o.getJSONArray(it).length() }
    }.getOrDefault(0)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// ───────────────────────── Ponte JS -> app ─────────────────────────

data class ResultadoSge(
    val requestId: String,
    /** ok | parcial | erro | salvo | nao_salvo | nao_confirmado */
    val status: String,
    val codigo: String? = null,
    val mensagem: String? = null,
    val naoEncontrados: List<String> = emptyList(),
    val sugestoes: Map<String, List<String>> = emptyMap(),
    val avisos: List<String> = emptyList(),
    val dados: JSONObject? = null,
) {
    val sucesso get() = status == "ok" || status == "parcial"

    companion object {
        fun de(texto: String): ResultadoSge = runCatching {
            val o = JSONObject(texto)
            val sug = o.optJSONObject("sugestoes")
            ResultadoSge(
                requestId = o.optString("requestId"),
                status = o.optString("status", "erro"),
                codigo = o.optString("codigo").takeIf { !o.isNull("codigo") && it.isNotBlank() },
                mensagem = o.optString("mensagem").takeIf { !o.isNull("mensagem") && it.isNotBlank() },
                naoEncontrados = o.optJSONArray("naoEncontrados").strings(),
                sugestoes = sug?.keys()?.asSequence()?.associateWith { sug.optJSONArray(it).strings() }.orEmpty(),
                avisos = o.optJSONArray("avisos").strings(),
                dados = o.optJSONObject("dados"),
            )
        }.getOrElse { ResultadoSge("", "erro", "json_invalido", it.message) }
    }
}

/**
 * window.SgeBridge no JavaScript. Roda numa thread do WebView: só lê e repassa.
 * Ignora mensagens se a página principal não for do SGE.
 */
class SgeBridge(private val urlAtual: () -> String?, private val aoReceber: (ResultadoSge) -> Unit) {
    @JavascriptInterface
    fun resultado(mensagem: String) {
        val host = urlAtual()?.let { Uri.parse(it).host } ?: return
        if (host != HOST_SGE) return
        aoReceber(ResultadoSge.de(mensagem))
    }

    companion object {
        val HOST_SGE: String = Uri.parse(SgeSyncWorker.BASE).host.orEmpty()
    }
}

/**
 * Criado em MainActivity ANTES do primeiro loadUrl (addJavascriptInterface vale a partir da próxima carga).
 * MainActivity chama aoMudarUrl() em onPageFinished.
 */
class SgeInjetor(private val web: WebView, ctx: Context) {
    private val script = ctx.assets.open("sge_automacao.js").bufferedReader().use { it.readText() }
    private val aguardando = ConcurrentHashMap<String, CompletableDeferred<ResultadoSge>>()
    private val _eventos = MutableSharedFlow<ResultadoSge>(extraBufferCapacity = 32)

    /** Mensagens que chegam depois da resposta principal (ex.: "salvo"). */
    val eventos: SharedFlow<ResultadoSge> = _eventos

    @Volatile private var urlAtual: String? = null
    fun aoMudarUrl(url: String?) { urlAtual = url }

    init {
        web.addJavascriptInterface(SgeBridge({ urlAtual }) { r ->
            val espera = aguardando.remove(r.requestId)
            if (espera != null) espera.complete(r) else _eventos.tryEmit(r)
        }, "SgeBridge")
    }

    suspend fun preencherConteudo(payload: JSONObject) =
        chamar(payload.getString("requestId"), "preencherConteudo($payload)")

    suspend fun marcarFaltas(horarioId: String, dataIso: String?, alunoIds: List<String>): ResultadoSge {
        val id = novoId("faltas")
        val p = JSONObject().put("requestId", id).put("horarioId", horarioId)
            .put("dataIso", dataIso ?: JSONObject.NULL).put("alunoIds", JSONArray(alunoIds))
        return chamar(id, "marcarFaltas($p)")
    }

    suspend fun exportarOpcoes(): ResultadoSge { val id = novoId("opcoes"); return chamar(id, "exportarOpcoes(${JSONObject.quote(id)})") }
    suspend fun listarAulas(): ResultadoSge { val id = novoId("aulas"); return chamar(id, "listarAulas(${JSONObject.quote(id)})") }

    private suspend fun chamar(requestId: String, chamada: String, tempoMs: Long = 45_000): ResultadoSge {
        val espera = CompletableDeferred<ResultadoSge>()
        aguardando[requestId] = espera
        withContext(Dispatchers.Main) {
            // O script se protege contra carga dupla (confere a versão).
            web.evaluateJavascript("$script\n;window.SgeAutomacao.$chamada;", null)
        }
        return try {
            withTimeout(tempoMs) { espera.await() }
        } catch (e: TimeoutCancellationException) {
            ResultadoSge(requestId, "erro", "sem_resposta", "O SGE não respondeu a tempo. A página do SGE está aberta?")
        } finally {
            aguardando.remove(requestId)
        }
    }

    private fun novoId(prefixo: String) = "$prefixo-${UUID.randomUUID()}"
}

// ───────────────────────── Sincronização ─────────────────────────

object SgeLancamentoSync {
    const val CAMPO_OBJETO_1 = "ItemDoAgrupador_2516"
    const val CAMPO_OBJETO_2 = "ItemDoAgrupador_2525"
    private const val PREFIXO = "conteudo-"

    fun payload(ctx: Context, l: LancamentoMatriz): JSONObject {
        val hab = SgeMatriz.porId(ctx, l.habilidadeId)
        fun item(campo: String, texto: String?): Any = texto?.takeIf { it.isNotBlank() }?.let {
            JSONObject().put("texto", it)
                .put("sgeValue", SgeOpcoesStore.valuePorTexto(ctx, l.gradeEscolarId, campo, it) ?: JSONObject.NULL)
        } ?: JSONObject.NULL
        return JSONObject()
            .put("requestId", PREFIXO + l.id)
            .put("turmaId", l.turmaId)
            .put("gradeEscolarId", l.gradeEscolarId)
            .put("componente", l.componente.rotulo)
            .put("dataIso", l.dataIso)
            .put("habilidadeTexto", hab?.textoSge ?: JSONObject.NULL)
            .put("objetoUm", item(CAMPO_OBJETO_1, l.objetoUm))
            .put("objetoDois", item(CAMPO_OBJETO_2, l.objetoDois))
            .put("outros", l.outros ?: JSONObject.NULL)
            .put("atividadesObs", l.atividadesObs)
    }

    /** Preenche UM lançamento na janela do SGE. Não salva. */
    suspend fun preencher(ctx: Context, inj: SgeInjetor, l: LancamentoMatriz): ResultadoSge {
        val r = inj.preencherConteudo(payload(ctx, l))
        when (r.status) {
            "ok" -> SgeLancamentosStore.atualizarStatus(ctx, l.id, StatusLancamento.PREENCHIDO, null)
            "parcial" -> SgeLancamentosStore.atualizarStatus(ctx, l.id, StatusLancamento.PREENCHIDO,
                "Escolha no SGE: " + r.naoEncontrados.joinToString())
            else -> SgeLancamentosStore.atualizarStatus(ctx, l.id, StatusLancamento.ERRO, r.mensagem ?: r.codigo)
        }
        return r
    }

    /** Confirmações que chegam depois que o professor toca em Salvar. */
    suspend fun acompanhar(ctx: Context, inj: SgeInjetor, aviso: (String) -> Unit) {
        inj.eventos.collect { ev ->
            if (!ev.requestId.startsWith(PREFIXO)) return@collect
            val id = ev.requestId.removePrefix(PREFIXO)
            when (ev.status) {
                "salvo" -> { SgeLancamentosStore.atualizarStatus(ctx, id, StatusLancamento.ENVIADO, null); aviso("Conteúdo salvo e confirmado no SGE.") }
                "nao_salvo", "nao_confirmado" -> {
                    SgeLancamentosStore.atualizarStatus(ctx, id, StatusLancamento.PREENCHIDO, ev.mensagem)
                    ev.mensagem?.let(aviso)
                }
            }
        }
    }

    /** Lê o Mapa de Registros da tela Conteúdo Aplicado e guarda a turma com seus componentes. */
    suspend fun atualizarAulas(ctx: Context, inj: SgeInjetor): String {
        val r = inj.listarAulas()
        if (r.status != "ok") return r.mensagem ?: "Não foi possível ler as aulas."
        val d = r.dados ?: return "O SGE não devolveu dados."
        val turmaId = d.optString("turmaId").takeIf { it.isNotBlank() }
            ?: return "Abra Conteúdo Aplicado de uma turma no SGE e tente de novo."
        val aulas = d.optJSONArray("aulas") ?: JSONArray()
        val grades = (0 until aulas.length()).map { aulas.getJSONObject(it) }.mapNotNull { a ->
            val c = Componente.deTexto(a.optString("componente")) ?: return@mapNotNull null
            a.optString("gradeEscolarId").takeIf { it.isNotBlank() && it != "null" }?.let { c to it }
        }.toMap()
        SgeTurmasAutomacao.registrar(ctx, turmaId, d.optString("turmaDescricao"), grades)
        val nomes = grades.keys.joinToString { it.rotulo }.ifBlank { "nenhum componente com aula pendente no mês" }
        return "Turma ${d.optString("turmaDescricao", turmaId)}: $nomes."
    }

    /** Captura as opções dos selects da janela Adicionar Conteúdo aberta. */
    suspend fun capturarOpcoes(ctx: Context, inj: SgeInjetor): String {
        val r = inj.exportarOpcoes()
        if (r.status != "ok") return r.mensagem ?: "Não foi possível ler as opções."
        val d = r.dados ?: return "O SGE não devolveu dados."
        val grade = d.optString("gradeEscolarId").takeIf { it.isNotBlank() }
            ?: return "Escolha o componente na janela antes de capturar."
        val campos = d.optJSONObject("campos") ?: JSONObject()
        val mapa = listOf(CAMPO_OBJETO_1, CAMPO_OBJETO_2).associateWith { c ->
            val arr = campos.optJSONArray(c) ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it) }.map { it.optString("value") to it.optString("texto") }
        }
        SgeOpcoesStore.salvar(ctx, grade, mapa)
        return "Opções capturadas: ${mapa[CAMPO_OBJETO_1]?.size ?: 0} + ${mapa[CAMPO_OBJETO_2]?.size ?: 0} (componente $grade)."
    }
}

/** Ligação entre a Activity (dona do WebView) e as telas Compose. */
object SgeAutomacaoHost {
    @Volatile var injetor: SgeInjetor? = null
    @Volatile var escopo: CoroutineScope? = null

    /** Roda no escopo da Activity (não morre ao trocar de tela) e mostra o resultado num aviso. */
    fun executar(ctx: Context, bloco: suspend (SgeInjetor) -> String) {
        val app = ctx.applicationContext
        val inj = injetor
        val esc = escopo
        if (inj == null || esc == null) {
            Toast.makeText(app, "Abra o SGE Web no app e tente de novo.", Toast.LENGTH_LONG).show()
            return
        }
        esc.launch {
            val msg = try { bloco(inj) } catch (e: CancellationException) { throw e } catch (e: Exception) { "Falhou: ${e.message}" }
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }
}

package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.Instant

/** Backup portátil sem sessão, cookies, senha, diagnóstico ou autorização biométrica. */
object SgeBackup {
    const val LIMITE = 10 * 1024 * 1024
    val textos = SgeFiltrosStore.chaves + setOf("painel_v2", "painel_coord_v2", "vinculos_docentes_v1", "historico_semanal_v1", "turmas_escola", "turmas_contexto", SgeOrganizacao.CHAVE)
    val booleanos = setOf("lembrete", "coord_noturna", "economia_wifi", "economia_carregando", "economia_bateria")
    val inteiros = setOf("tema", "estilo", "papel", "economia_horas", "economia_hora_noturna")
    val chaves = textos + booleanos + inteiros
    data class Validado(val dados: JSONObject, val criado: String, val versao: String, val resultados: Int)
    fun criar(valores: Map<String, *>, versao: String): String = JSONObject().put("formato", "sge-diario-backup").put("schema", 1).put("criado", Instant.now().toString()).put("versao", versao)
        .put("dados", JSONObject().also { j -> chaves.forEach { chave -> valores[chave]?.let { j.put(chave, it) } } }).toString(2)
    fun ler(entrada: InputStream): Validado {
        val bytes = entrada.readBytesLimitados(LIMITE)
        return validar(bytes.toString(Charsets.UTF_8))
    }
    fun validar(texto: String): Validado {
        require(texto.toByteArray(Charsets.UTF_8).size <= LIMITE) { "Backup maior que 10 MB." }
        val raiz = JSONObject(texto)
        require(raiz.getString("formato") == "sge-diario-backup" && raiz.getInt("schema") == 1) { "Formato de backup incompatível." }
        Instant.parse(raiz.getString("criado"))
        val dados = JSONObject(raiz.getJSONObject("dados").toString())
        require(dados.keys().asSequence().all { it in chaves }) { "O backup contém campos não permitidos." }
        var total = 0
        fun resultado(j: JSONObject) {
            val r = ResultadoTurma.ler(j)
            require(r.turma.id.isNotBlank() && r.modulos.size == 5 && r.modulos.all { (it.quantidade ?: 0) >= 0 && it.aguardando >= 0 && it.aprovados >= 0 }) { "Resultado inválido no backup." }
            total++
        }
        dados.keys().asSequence().toList().forEach { chave ->
            val v = dados.get(chave)
            when (chave) {
                in booleanos -> require(v is Boolean)
                in inteiros -> {
                    require(v is Int)
                    val n = dados.getInt(chave)
                    require(when (chave) { "economia_horas" -> n in listOf(2,6,12,24); "economia_hora_noturna" -> n in 0..23; "estilo" -> n in 0..1; else -> n in 0..2 })
                }
                in textos -> {
                    require(v is String)
                    when (chave) {
                        "painel_v2", "painel_coord_v2" -> {
                            val j = JSONObject(v); val a = j.getJSONArray("turmas")
                            for (i in 0 until a.length()) {
                                val t = a.getJSONObject(i)
                                if (chave == "painel_v2") resultado(t) else {
                                    require(t.getString("id").isNotBlank()); t.getString("nome")
                                    val ds = t.getJSONArray("disc"); for (k in 0 until ds.length()) resultado(ds.getJSONObject(k))
                                }
                            }
                            j.put("sincronizando", false).put("inicio", 0).put("inicioConsulta", 0).put("sessaoAtiva", false)
                                .put("mensagem", "Backup restaurado. Dados da última consulta; entre no SGE para atualizar.")
                            dados.put(chave, j.toString())
                        }
                        "vinculos_docentes_v1" -> {
                            val j = JSONObject(v); val ps = j.getJSONArray("professores"); val vs = j.getJSONArray("vinculos"); val ls = j.getJSONObject("locais")
                            fun docente(p: JSONObject) { require(p.getString("id").isNotBlank()); p.getString("nome") }
                            for (i in 0 until ps.length()) docente(ps.getJSONObject(i))
                            for (i in 0 until vs.length()) vs.getJSONObject(i).let { require(it.getString("turma").isNotBlank()); it.getString("componente"); docente(it.getJSONObject("professor")) }
                            ls.keys().forEach { key -> val a = ls.getJSONArray(key); for (i in 0 until a.length()) docente(a.getJSONObject(i)) }
                            j.put("sincronizando", false).put("inicio", 0); dados.put(chave, j.toString())
                        }
                        "historico_semanal_v1" -> { val a = JSONArray(v); require(a.length() <= 32); require(SgeHistorico.ler(v).let { ds -> ds.size == a.length() && ds.all { d -> d.medicoes.all { m -> m.modulos.size == 5 && m.modulos.all { (it.quantidade ?: 0) >= 0 && it.aguardando >= 0 && it.aprovados >= 0 } } } }) { "Histórico inválido." } }
                        SgeOrganizacao.CHAVE -> SgeOrganizacao.ler(v)
                        in SgeFiltrosStore.chaves -> SgeFiltrosStore.ler(v)
                        else -> { val a = JSONArray(v); for (i in 0 until a.length()) { val j = a.getJSONObject(i); require(j.getString("v").isNotBlank()); j.getString(if (chave == "turmas_escola") "t" else "d") } }
                    }
                }
            }
        }
        return Validado(dados, raiz.getString("criado"), raiz.getString("versao"), total)
    }
    fun restaurar(ctx: Context, b: Validado) {
        // Uma única transação, somente depois de validar todo o documento.
        val validado = validar(JSONObject().put("formato", "sge-diario-backup").put("schema", 1).put("criado", b.criado).put("versao", b.versao).put("dados", b.dados).toString())
        val e = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, 0).edit()
        chaves.forEach { e.remove(it) }
        validado.dados.keys().forEach { k -> when (val v = validado.dados.get(k)) { is Boolean -> e.putBoolean(k,v); is Int -> e.putInt(k,v); is String -> e.putString(k,v) } }
        check(e.commit()) { "Não foi possível gravar o backup." }
    }
}
fun InputStream.readBytesLimitados(limite: Int): ByteArray {
    val saida = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(saida.size() + n <= limite) { "Arquivo maior que o limite permitido." }; saida.write(buffer, 0, n) }
    return saida.toByteArray()
}

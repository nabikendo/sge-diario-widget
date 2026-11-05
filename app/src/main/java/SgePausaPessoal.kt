package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Pausa da atualização do "Meu diário": quais turmas/componentes já foram lidos nesta rodada e se o usuário pediu para parar. */
data class PausaPessoal(val feitas: Set<String>, val pausada: Boolean)

object SgePausaPessoal {
    private const val KEY = "pausa_pessoal_v1"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized fun ler(ctx: Context): PausaPessoal? = runCatching {
        val j = JSONObject(prefs(ctx).getString(KEY, null) ?: return null)
        val a = j.optJSONArray("feitas") ?: JSONArray()
        PausaPessoal((0 until a.length()).map { a.getString(it) }.toSet(), j.optBoolean("pausada"))
    }.getOrNull()

    @Synchronized private fun gravar(ctx: Context, e: PausaPessoal?) {
        val ed = prefs(ctx).edit()
        if (e == null) ed.remove(KEY) else ed.putString(KEY, JSONObject().put("feitas", JSONArray(e.feitas.toList())).put("pausada", e.pausada).toString())
        ed.commit()
    }

    /** O usuário tocou em Pausar: a consulta em andamento para na próxima etapa e guarda o que já foi lido. */
    @Synchronized fun pedirPausa(ctx: Context) = gravar(ctx, PausaPessoal(ler(ctx)?.feitas.orEmpty(), true))

    /** Libera a consulta; false se não havia nada pausado. */
    @Synchronized fun retomar(ctx: Context): Boolean {
        val e = ler(ctx) ?: return false
        gravar(ctx, e.copy(pausada = false)); return true
    }

    @Synchronized fun marcarFeita(ctx: Context, chave: String) {
        val e = ler(ctx) ?: PausaPessoal(emptySet(), false)
        gravar(ctx, e.copy(feitas = e.feitas + chave))
    }

    @Synchronized fun limpar(ctx: Context) = gravar(ctx, null)
}

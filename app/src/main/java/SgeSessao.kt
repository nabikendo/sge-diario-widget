package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.jsoup.Jsoup
import kotlinx.coroutines.asContextElement

enum class PerfilSge(val titulo: String) { DESCONHECIDO("Perfil não confirmado"), PROFESSOR("Servidor"), COORDENACAO("Unidade escolar"), EXPIRADO("Sessão encerrada") }
object SgePerfil {
    fun ler(html: String): PerfilSge {
        val d = Jsoup.parse(html)
        val escola = d.selectFirst("#ehPerfilEscola")?.attr("value")?.equals("true", true) == true
        val professor = d.selectFirst("#ehPerfilProfessor")?.attr("value")?.equals("true", true) == true
        return when { escola && !professor -> PerfilSge.COORDENACAO; professor && !escola -> PerfilSge.PROFESSOR; else -> PerfilSge.DESCONHECIDO }
    }
}
object SgeSessaoStore {
    private val trava = Any()
    private val consulta = ThreadLocal<Long?>()
    fun contextoConsulta(ctx: Context) = consulta.asContextElement(geracao(ctx))
    /** O teste da geração e a gravação são atômicos em relação a sair/limpar/restaurar. */
    fun gravarSeAtual(ctx: Context, acao: ()->Unit): Boolean = synchronized(trava) {
        val origem = consulta.get()
        if (origem != null && origem != geracao(ctx)) false else { acao(); true }
    }
    fun geracao(ctx: Context) = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getLong("sessao_geracao", 0L)
    fun invalidar(ctx: Context) { synchronized(trava) { check(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().putLong("sessao_geracao", geracao(ctx) + 1).commit()) } }

    fun perfil(ctx: Context): PerfilSge = runCatching { PerfilSge.valueOf(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        .getString("perfil_observado", PerfilSge.DESCONHECIDO.name)!!) }.getOrDefault(PerfilSge.DESCONHECIDO)
    fun salvar(ctx: Context, perfil: PerfilSge) {
        val prefs = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        if (SgeSessaoStore.perfil(ctx) == perfil && System.currentTimeMillis() - prefs.getLong("perfil_observado_em", 0L) < 60_000L) return
        gravarSeAtual(ctx) { ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString("perfil_observado", perfil.name).putLong("perfil_observado_em", System.currentTimeMillis()).apply() } }
    fun protegida(ctx: Context) = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getBoolean("coord_biometria", false)
}

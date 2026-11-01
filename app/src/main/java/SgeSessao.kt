package br.to.seduc.sgediariowidget.data

import android.content.Context
import org.jsoup.Jsoup

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
    fun perfil(ctx: Context): PerfilSge = runCatching { PerfilSge.valueOf(ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
        .getString("perfil_observado", PerfilSge.DESCONHECIDO.name)!!) }.getOrDefault(PerfilSge.DESCONHECIDO)
    fun salvar(ctx: Context, perfil: PerfilSge) { ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString("perfil_observado", perfil.name).putLong("perfil_observado_em", System.currentTimeMillis()).apply() }
    fun protegida(ctx: Context) = ctx.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getBoolean("coord_biometria", false)
}

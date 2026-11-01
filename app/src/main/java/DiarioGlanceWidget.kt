package br.to.seduc.sgediariowidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import br.to.seduc.sgediariowidget.MainActivity
import br.to.seduc.sgediariowidget.R
import br.to.seduc.sgediariowidget.data.*
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker
import kotlinx.coroutines.CancellationException

open class SgeWidgetReceiver(private val tipo: TipoWidget) : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = SgeWidgets.instancia(tipo)
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        SgeWidgetStore.remover(context, appWidgetIds); super.onDeleted(context, appWidgetIds)
    }
}
class DiarioGlanceWidgetReceiver : SgeWidgetReceiver(TipoWidget.TURMAS)
class ResumoWidgetReceiver : SgeWidgetReceiver(TipoWidget.RESUMO)
class ComponenteWidgetReceiver : SgeWidgetReceiver(TipoWidget.COMPONENTES)
class CoordenacaoWidgetReceiver : SgeWidgetReceiver(TipoWidget.COORDENACAO)
class ProfessorWidgetReceiver : SgeWidgetReceiver(TipoWidget.PROFESSORES)
class FocoWidgetReceiver : SgeWidgetReceiver(TipoWidget.FOCO)

class DiarioGlanceWidget : SgeBaseWidget(TipoWidget.TURMAS)
class ResumoGlanceWidget : SgeBaseWidget(TipoWidget.RESUMO)
class ComponenteGlanceWidget : SgeBaseWidget(TipoWidget.COMPONENTES)
class CoordenacaoGlanceWidget : SgeBaseWidget(TipoWidget.COORDENACAO)
class ProfessorGlanceWidget : SgeBaseWidget(TipoWidget.PROFESSORES)
class FocoGlanceWidget : SgeBaseWidget(TipoWidget.FOCO)

object SgeWidgets {
    fun instancia(tipo: TipoWidget): GlanceAppWidget = when(tipo) {
        TipoWidget.RESUMO -> ResumoGlanceWidget(); TipoWidget.TURMAS -> DiarioGlanceWidget()
        TipoWidget.COMPONENTES -> ComponenteGlanceWidget(); TipoWidget.COORDENACAO -> CoordenacaoGlanceWidget()
        TipoWidget.PROFESSORES -> ProfessorGlanceWidget(); TipoWidget.FOCO -> FocoGlanceWidget()
    }
    fun receptor(tipo: TipoWidget): Class<out GlanceAppWidgetReceiver> = when(tipo) {
        TipoWidget.RESUMO -> ResumoWidgetReceiver::class.java; TipoWidget.TURMAS -> DiarioGlanceWidgetReceiver::class.java
        TipoWidget.COMPONENTES -> ComponenteWidgetReceiver::class.java; TipoWidget.COORDENACAO -> CoordenacaoWidgetReceiver::class.java
        TipoWidget.PROFESSORES -> ProfessorWidgetReceiver::class.java; TipoWidget.FOCO -> FocoWidgetReceiver::class.java
    }
    fun tipo(context: Context, id: Int): TipoWidget {
        val name = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className
        return TipoWidget.values().firstOrNull { receptor(it).name == name } ?: TipoWidget.TURMAS
    }
    suspend fun atualizar(context: Context) {
        TipoWidget.values().forEach { t -> try { instancia(t).updateAll(context) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("SgeWidgets", "Falha ao atualizar ${t.name}", e) } }
    }
    fun fixar(context: Context, tipo: TipoWidget): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, receptor(tipo)), null, null)
    }
}

private val deltaKey = ActionParameters.Key<Int>("sge_delta")
class AlternarFocoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val tipo = SgeWidgets.tipo(context, id); val config = SgeWidgetStore.ler(context, id, tipo)
        val gs = SgeWidgetStore.grupos(context, config)
        if (gs.isNotEmpty()) {
            val atual = gs.indexOfFirst { it.chave == SgeWidgetStore.foco(context, id) }.coerceAtLeast(0)
            val proximo = (atual + (parameters[deltaKey] ?: 1) + gs.size) % gs.size
            SgeWidgetStore.salvarFoco(context, id, gs[proximo].chave)
            SgeWidgets.instancia(tipo).update(context, glanceId)
        }
    }
}
class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val c = SgeWidgetStore.ler(context, id, SgeWidgets.tipo(context, id))
        if (c.fonte == FontePainel.PESSOAL) {
            val p = SgePainelStore.ler(context)
            if (!p.sincronizando) {
                SgePainelStore.salvar(context, p.copy(sincronizando = true, inicioConsulta = System.currentTimeMillis(), mensagem = "Atualizando o diário…"))
                SgeSyncWorker.sincronizarAgora(context)
            }
        } else {
            val p = SgeCoordStore.ler(context)
            val ids = if (c.grupo.isBlank()) SgeWidgetStore.conhecidas(context).map { it.first }
                else SgeWidgetStore.grupos(context, c.copy(soAtencao = false)).flatMap { it.turmaIds }.distinct()
            if (ids.isNotEmpty() && !p.sincronizando) {
                SgeCoordStore.salvar(context, p.copy(sincronizando = true, inicioConsulta = System.currentTimeMillis(), mensagem = "Atualizando ${ids.size} turma(s)…"))
                SgeSyncWorker.verificarTurmas(context, ids)
            }
        }
        SgeWidgets.atualizar(context)
    }
}

private data class CoresWidget(val fundo: Color, val cartao: Color, val texto: Color, val secundario: Color, val chip: Color,
    val acento: Color, val pendente: Color, val conferir: Color, val ok: Color)
private fun cores(context: Context): CoresWidget {
    val tema = context.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE).getInt("tema", 0)
    val escuro = when(tema) { 1 -> false; 2 -> true; else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
    return if (escuro) CoresWidget(Color(0xFF14171D), Color(0xFF20252E), Color(0xFFF2F3F6), Color(0xFFA7AFBD),
        Color(0xFF2B323D), Color(0xFF9DBFFF), Color(0xFFF3A698), Color(0xFFE0BB78), Color(0xFF9DCEB4))
    else CoresWidget(Color(0xFFF2F4F7), Color.White, Color(0xFF171B23), Color(0xFF6C7583),
        Color(0xFFECEFF4), Color(0xFF316BCD), Color(0xFFB34A42), Color(0xFF92651E), Color(0xFF347B5C))
}
private fun estadoTexto(e: EstadoGrupo) = when(e) {
    EstadoGrupo.PENDENTE -> "Pendências"; EstadoGrupo.CONFERIR -> "A conferir"; EstadoGrupo.EM_DIA -> "Em dia"; else -> "Sem consulta"
}
private fun corEstado(e: EstadoGrupo, c: CoresWidget) = when(e) {
    EstadoGrupo.PENDENTE -> c.pendente; EstadoGrupo.EM_DIA -> c.ok; else -> c.conferir
}
private fun abrir(context: Context, config: ConfigWidget, grupo: String = ""): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra("sge_fonte", config.fonte.name).putExtra("sge_por", config.agrupamento.name).putExtra("sge_grupo", grupo))

open class SgeBaseWidget(private val tipo: TipoWidget) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            // As sessões Glance em andamento recebem alterações de dados sem depender de recriar provideGlance.
            val revisao by produceState(0, widgetId) {
                val prefs = context.getSharedPreferences(SgePainelStore.PREFS_NAME, Context.MODE_PRIVATE)
                val configs = context.getSharedPreferences("sge_widgets_v2", Context.MODE_PRIVATE)
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value++ }
                prefs.registerOnSharedPreferenceChangeListener(listener); configs.registerOnSharedPreferenceChangeListener(listener)
                awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); configs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val config = remember(revisao) { SgeWidgetStore.ler(context, widgetId, tipo) }
            val grupos = remember(revisao) { SgeWidgetStore.grupos(context, config) }
            val c = remember(revisao) { cores(context) }
            val pessoal = SgePainelStore.ler(context); val coord = SgeCoordStore.ler(context)
            val ocupado = if (config.fonte == FontePainel.PESSOAL) pessoal.sincronizando else coord.sincronizando
            val size = LocalSize.current
            val focoIndice = grupos.indexOfFirst { it.chave == SgeWidgetStore.foco(context, widgetId) }.coerceAtLeast(0)
            val ajustar = actionStartActivity(Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            Column(GlanceModifier.fillMaxSize().background(c.fundo).cornerRadius(28.dp).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().clickable(abrir(context, config))) {
                        Text(if (config.fonte == FontePainel.COORDENACAO) "SGE · ESCOLA" else "SGE · MEU DIÁRIO",
                            style = TextStyle(color = ColorProvider(c.secundario), fontSize = 9.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        Text(when(tipo) { TipoWidget.FOCO -> "Em foco · ${if (grupos.isEmpty()) 0 else focoIndice + 1}/${grupos.size}"
                            TipoWidget.RESUMO -> if (config.fonte == FontePainel.COORDENACAO) "Minha escola" else "Meu diário"; else -> tipo.titulo },
                            style = TextStyle(color = ColorProvider(c.texto), fontSize = if (size.width < 200.dp) 13.sp else 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    if (tipo == TipoWidget.FOCO && grupos.size > 1) {
                        IconeWidget(R.drawable.ic_widget_up, "Recorte anterior", c.texto, actionRunCallback<AlternarFocoAction>(actionParametersOf(deltaKey to -1)))
                        IconeWidget(R.drawable.ic_widget_down, "Próximo recorte", c.texto, actionRunCallback<AlternarFocoAction>(actionParametersOf(deltaKey to 1)))
                    }
                    IconeWidget(R.drawable.ic_widget_settings, "Ajustar widget", c.secundario, ajustar)
                    if (size.width >= 230.dp && tipo != TipoWidget.FOCO) IconeWidget(R.drawable.ic_widget_refresh, "Atualizar dados", c.acento, actionRunCallback<RefreshActionCallback>())
                }
                when {
                    tipo == TipoWidget.RESUMO -> ResumoWidget(grupos, config, c, context, size.height < 180.dp, ocupado)
                    grupos.isEmpty() -> Column(GlanceModifier.fillMaxSize().clickable(ajustar).padding(top = 14.dp)) {
                        Text(if (config.grupo.isNotBlank()) "Este recorte não está disponível na última consulta."
                            else if (config.soAtencao) "Nenhum resultado neste filtro." else "Atualize o painel no aplicativo.",
                            style = TextStyle(color = ColorProvider(c.secundario), fontSize = 12.sp), maxLines = 3)
                        Text("Toque para ajustar", GlanceModifier.padding(top = 8.dp), style = TextStyle(color = ColorProvider(c.acento), fontSize = 11.sp))
                    }
                    tipo == TipoWidget.FOCO -> {
                        val indice = focoIndice
                        val grupo = grupos[indice]
                        Column(GlanceModifier.defaultWeight().padding(top = 10.dp)) {
                            CartaoWidget(grupo, if (config.ocultarNomes) "Recorte ${indice + 1}" else grupo.titulo,
                                config, c, context, compacto = size.height < 215.dp)
                        }
                    }
                    else -> {
                        LazyColumn(GlanceModifier.defaultWeight().padding(top = 9.dp)) {
                            grupos.forEachIndexed { index, grupo ->
                                item(itemId = index.toLong()) {
                                    Column(GlanceModifier.padding(bottom = 8.dp)) {
                                        CartaoWidget(grupo, if (config.ocultarNomes) "Recorte ${index + 1}" else grupo.titulo,
                                            config, c, context, compacto = size.height < 200.dp)
                                    }
                                }
                            }
                        }
                        Text(if (ocupado) "Atualizando…" else "Deslize para ver outros cartões · toque para detalhes",
                            style = TextStyle(color = ColorProvider(c.secundario), fontSize = 9.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable private fun IconeWidget(recurso: Int, texto: String, cor: Color, acao: Action) {
    Image(ImageProvider(recurso), texto, GlanceModifier.size(36.dp).clickable(acao).padding(8.dp),
        colorFilter = ColorFilter.tint(ColorProvider(cor)))
}

@Composable private fun CartaoWidget(grupo: GrupoPendencias, titulo: String, config: ConfigWidget, c: CoresWidget, context: Context, compacto: Boolean) {
    Column(GlanceModifier.fillMaxWidth().background(c.cartao).cornerRadius(20.dp).clickable(abrir(context, config, grupo.chave)).padding(13.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, GlanceModifier.defaultWeight(), style = TextStyle(color = ColorProvider(c.texto), fontSize = if (compacto) 14.sp else 17.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Text("›", GlanceModifier.padding(start = 5.dp), style = TextStyle(color = ColorProvider(c.secundario), fontSize = 20.sp))
        }
        if (!config.ocultarNomes && !compacto) Text(grupo.subtitulo, style = TextStyle(color = ColorProvider(c.secundario), fontSize = 10.sp), maxLines = 1)
        Text(estadoTexto(grupo.estado), GlanceModifier.padding(top = 5.dp), style = TextStyle(color = ColorProvider(corEstado(grupo.estado, c)), fontSize = 11.sp, fontWeight = FontWeight.Medium))
        val ms = grupo.modulos
        if (!compacto) {
            Row(GlanceModifier.fillMaxWidth().padding(top = 10.dp)) { ValorWidget("Conteúdos", ms[0], c); ValorWidget("Frequência", ms[1], c) }
            Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) { ValorWidget("Aula", ms[2], c); ValorWidget("Curso", ms[3], c); ValorWidget("PEI", ms[4], c) }
        } else Text(ms.filter { (it.quantidade ?: 0) > 0 || !it.completa }.take(2).joinToString(" · ") {
            "${SgeDashboard.nomes[it.indice]} ${it.valor()}"
        }.ifBlank { "Cinco módulos verificados" }, GlanceModifier.padding(top = 6.dp), style = TextStyle(color = ColorProvider(c.secundario), fontSize = 10.sp), maxLines = 2)
        if (ms.any { !it.completa } && !compacto) Text("* parcial · — não verificado", GlanceModifier.padding(top = 7.dp), style = TextStyle(color = ColorProvider(c.conferir), fontSize = 9.sp))
    }
}

@Composable private fun RowScope.ValorWidget(nome: String, m: ResumoModulo, c: CoresWidget) {
    Column(GlanceModifier.defaultWeight().padding(end = 5.dp)) {
        Text(nome, style = TextStyle(color = ColorProvider(c.secundario), fontSize = 9.sp), maxLines = 1)
        Text(m.valor(), style = TextStyle(color = ColorProvider(if ((m.quantidade ?: 0) > 0) c.pendente else if (!m.completa) c.conferir else c.texto),
            fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable private fun ColumnScope.ResumoWidget(grupos: List<GrupoPendencias>, config: ConfigWidget, c: CoresWidget, context: Context, compacto: Boolean, ocupado: Boolean) {
    val rs = grupos.flatMap { it.resultados }.distinctBy(::chaveResultado)
    val turmas = SgeDashboard.agrupar(rs, Agrupamento.TURMA)
    val semConsulta = if (config.fonte == FontePainel.COORDENACAO && config.grupo.isBlank()) SgeWidgetStore.conhecidas(context).count { (id, _) -> turmas.none { id in it.turmaIds } } else 0
    Column(GlanceModifier.defaultWeight().fillMaxWidth().padding(top = 13.dp).clickable(abrir(context, config, config.grupo))) {
        if (turmas.isEmpty() && semConsulta == 0) Text("Atualize o painel para ver seu resumo.", style = TextStyle(color = ColorProvider(c.secundario), fontSize = 12.sp), maxLines = 3)
        else {
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(turmas.count { it.estado == EstadoGrupo.PENDENTE }.toString(), style = TextStyle(color = ColorProvider(c.pendente), fontSize = if (compacto) 29.sp else 36.sp, fontWeight = FontWeight.Bold))
                    Text("turmas pendentes", style = TextStyle(color = ColorProvider(c.secundario), fontSize = 10.sp), maxLines = 2)
                }
                Column(GlanceModifier.defaultWeight()) {
                    Text((turmas.count { it.exigeConferencia } + semConsulta).toString(), style = TextStyle(color = ColorProvider(c.conferir), fontSize = if (compacto) 29.sp else 36.sp, fontWeight = FontWeight.Bold))
                    Text("a conferir", style = TextStyle(color = ColorProvider(c.secundario), fontSize = 10.sp))
                }
            }
            if (!compacto && !config.soAtencao) Text("${contarSge(turmas.count { it.estado == EstadoGrupo.EM_DIA }, "turma", "turmas")} com todos os módulos em dia", GlanceModifier.padding(top = 10.dp),
                style = TextStyle(color = ColorProvider(c.ok), fontSize = 11.sp), maxLines = 2)
        }
    }
    Text(if (ocupado) "Atualizando…" else "Toque para abrir o painel", style = TextStyle(color = ColorProvider(c.secundario), fontSize = 9.sp), maxLines = 1)
}

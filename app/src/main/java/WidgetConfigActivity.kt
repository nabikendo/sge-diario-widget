package br.to.seduc.sgediariowidget.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import br.to.seduc.sgediariowidget.CoordProtecao
import br.to.seduc.sgediariowidget.TelaCoordenacaoProtegida
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import br.to.seduc.sgediariowidget.Segmentado
import br.to.seduc.sgediariowidget.SgeIcons
import br.to.seduc.sgediariowidget.SgeTema
import br.to.seduc.sgediariowidget.data.*
import kotlinx.coroutines.launch

class WidgetConfigActivity : FragmentActivity() {
    private val liberado = mutableStateOf(false)
    private lateinit var protecao: CoordProtecao
    override fun onStop() { liberado.value = false; super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        protecao = CoordProtecao(this)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val resposta = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(Activity.RESULT_CANCELED, resposta)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || AppWidgetManager.getInstance(this).getAppWidgetInfo(id) == null) { finish(); return }
        val tipo = SgeWidgets.tipo(this, id)
        val inicial = SgeWidgetStore.ler(this, id, tipo)
        val prefs = getSharedPreferences(SgePainelStore.PREFS_NAME, MODE_PRIVATE)
        setContent {
            var salvando by remember { mutableStateOf(false) }
            SgeTema(prefs.getInt("tema", 0), prefs.getInt("estilo", 0)) {
                if (SgeSessaoStore.protegida(this) && !liberado.value) TelaCoordenacaoProtegida { protecao.autenticar { liberado.value = true } }
                else TelaConfig(tipo, inicial, salvando, { finish() }) { config ->
                    salvando = true
                    SgeWidgetStore.salvar(this, id, config)
                    lifecycleScope.launch {
                        try {
                            SgeWidgets.instancia(tipo).update(this@WidgetConfigActivity, GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(id))
                            setResult(Activity.RESULT_OK, resposta); finish()
                        } catch (_: Exception) {
                            salvando = false
                            Toast.makeText(this@WidgetConfigActivity, "Não foi possível atualizar este widget. Tente novamente.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelaConfig(tipo: TipoWidget, inicial: ConfigWidget, salvando: Boolean, cancelar: () -> Unit, salvar: (ConfigWidget) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var fonte by rememberSaveable { mutableStateOf(inicial.fonte) }
    var por by rememberSaveable { mutableStateOf(inicial.agrupamento) }
    var grupo by rememberSaveable { mutableStateOf(inicial.grupo) }
    var atencao by rememberSaveable { mutableStateOf(inicial.soAtencao) }
    var ocultar by rememberSaveable { mutableStateOf(inicial.ocultarNomes) }
    var busca by rememberSaveable { mutableStateOf("") }
    val origens = if (tipo == TipoWidget.COORDENACAO || tipo == TipoWidget.PROFESSORES) listOf(FontePainel.COORDENACAO)
        else FontePainel.values().toList()
    val opcoes = when(tipo) {
        TipoWidget.TURMAS -> listOf(Agrupamento.TURMA)
        TipoWidget.COMPONENTES -> listOf(Agrupamento.COMPONENTE)
        TipoWidget.PROFESSORES -> listOf(Agrupamento.PROFESSOR)
        else -> if (fonte == FontePainel.COORDENACAO) Agrupamento.values().toList() else listOf(Agrupamento.TURMA, Agrupamento.COMPONENTE)
    }
    val efetiva = if (fonte in origens) fonte else origens.first()
    val agrupamento = if (por in opcoes) por else opcoes.first()
    val config = ConfigWidget(efetiva, agrupamento)
    val revisao by produceState(0) {
        val prefs = context.getSharedPreferences(SgePainelStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val grupos = remember(revisao, config) { SgeWidgetStore.grupos(context, config) }
    val valido = grupo.isBlank() || grupos.any { it.chave == grupo }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = cancelar) { Icon(SgeIcons.Close, "Cancelar") }
            Text("Ajustar widget", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            item {
                Text(tipo.titulo, style = MaterialTheme.typography.headlineMedium)
                Text(tipo.descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (origens.size > 1) item {
                Text("Origem dos dados", style = MaterialTheme.typography.titleSmall)
                Segmentado(listOf("Meu diário", "Coordenação"), origens.indexOf(efetiva)) {
                    fonte = origens[it]; grupo = ""; if (fonte == FontePainel.PESSOAL && por == Agrupamento.PROFESSOR) por = Agrupamento.TURMA
                }
            }
            if (opcoes.size > 1) item {
                Text("Agrupar por", style = MaterialTheme.typography.titleSmall)
                Segmentado(opcoes.map { it.titulo }, opcoes.indexOf(agrupamento)) { por = opcoes[it]; grupo = "" }
            }
            item {
                Text("Recorte", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(busca, { busca = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Buscar um cartão") }, leadingIcon = { Icon(SgeIcons.Search, null, Modifier.size(18.dp)) })
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { grupo = "" }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(grupo.isBlank(), { grupo = "" })
                    Text("Todos os cartões deste agrupamento", style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(grupos.filter { normalizarSge(it.titulo + " " + it.subtitulo).contains(normalizarSge(busca)) }, key = { it.chave }) { g ->
                Row(Modifier.fillMaxWidth().clickable { grupo = g.chave }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(grupo == g.chave, { grupo = g.chave })
                    Column { Text(g.titulo, style = MaterialTheme.typography.bodyMedium)
                        Text(g.subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (grupos.isEmpty()) item { Text("Ainda não há dados para este agrupamento. Você pode adicionar o widget e atualizar o painel depois.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!valido) item { Text("O recorte salvo não está disponível. Escolha Todos ou outro cartão.", color = MaterialTheme.colorScheme.error) }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item {
                Row(Modifier.fillMaxWidth().clickable { atencao = !atencao }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Somente atenção", style = MaterialTheme.typography.titleSmall)
                        Text("Mostra pendências e dados a conferir.", style = MaterialTheme.typography.bodySmall) }
                    Switch(atencao, { atencao = it })
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { ocultar = !ocultar }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Ocultar nomes", style = MaterialTheme.typography.titleSmall)
                        Text("Útil para usar um resumo na tela bloqueada.", style = MaterialTheme.typography.bodySmall) }
                    Switch(ocultar, { ocultar = it })
                }
            }
            item { Text("Esta configuração vale apenas para este widget. Toque no cartão para abrir os detalhes no app. As cores acompanham o tema escolhido no aplicativo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Button(onClick = { salvar(ConfigWidget(efetiva, agrupamento, grupo, atencao, ocultar)) }, enabled = valido && !salvando,
            modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text(if (salvando) "Salvando…" else "Salvar widget") }
    }
}

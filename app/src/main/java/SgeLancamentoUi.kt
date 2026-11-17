package br.to.seduc.sgediariowidget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FormaCampo = RoundedCornerShape(14.dp)
private val PtBr: Locale = Locale.forLanguageTag("pt-BR")

// ───────────────────────── Componentes ─────────────────────────

/** Lista suspensa Material 3. Com muitas opções vira busca (ignora acentos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownCampo(
    rotulo: String,
    opcoes: List<T>,
    selecionado: T?,
    textoDe: (T) -> String,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    comBusca: Boolean = opcoes.size > 8,
    textoVazio: String = "Nenhuma opção",
    onSelecionar: (T) -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    var busca by remember { mutableStateOf("") }
    val filtradas = remember(opcoes, busca) {
        if (busca.isBlank()) opcoes
        else busca.normalizarSge().let { alvo -> opcoes.filter { textoDe(it).normalizarSge().contains(alvo) } }
    }
    val expandido = aberto && habilitado
    ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = { if (habilitado) aberto = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (expandido && comBusca) busca else selecionado?.let(textoDe).orEmpty(),
            onValueChange = { busca = it },
            readOnly = !comBusca,
            enabled = habilitado,
            singleLine = true,
            label = { Text(rotulo) },
            placeholder = if (expandido && comBusca) { { Text("Buscar…") } } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            shape = FormaCampo,
            modifier = Modifier
                .menuAnchor(if (comBusca) MenuAnchorType.PrimaryEditable else MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { aberto = false; busca = "" }) {
            if (filtradas.isEmpty()) DropdownMenuItem(text = { Text(textoVazio) }, onClick = {}, enabled = false)
            filtradas.forEach { opcao ->
                DropdownMenuItem(
                    text = {
                        Text(textoDe(opcao), maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = if (opcao == selecionado) MaterialTheme.colorScheme.primary else Color.Unspecified)
                    },
                    onClick = { onSelecionar(opcao); aberto = false; busca = "" },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Escolha única em pílulas (mesmo visual dos filtros do app). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipsGrupo(
    rotulo: String,
    opcoes: List<T>,
    selecionado: T?,
    textoDe: (T) -> String = { it.toString() },
    habilitado: (T) -> Boolean = { true },
    modifier: Modifier = Modifier,
    onSelecionar: (T) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            opcoes.forEach { opcao ->
                val ativo = habilitado(opcao)
                Box(Modifier.alpha(if (ativo) 1f else .38f)) {
                    PilulaFiltro(opcao == selecionado, { if (ativo) onSelecionar(opcao) }) { Text(textoDe(opcao)) }
                }
            }
        }
    }
}

/** Campo de data com calendário. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoData(rotulo: String, data: LocalDate?, modifier: Modifier = Modifier, onSelecionar: (LocalDate) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    val formato = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy (EEE)", PtBr) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(value = data?.format(formato).orEmpty(), onValueChange = {}, readOnly = true,
            label = { Text(rotulo) }, shape = FormaCampo, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().clickable { aberto = true })
    }
    if (aberto) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = (data ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { aberto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onSelecionar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    aberto = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { aberto = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }
}

// ───────────────────────── Tela ─────────────────────────

/**
 * Lançamento de Conteúdo com a Matriz. [aoAbrirSge] leva o professor ao SGE Web;
 * o preenchimento roda pelo SgeAutomacaoHost (sobrevive à troca de tela).
 */
@Composable
fun TelaLancamentoMatriz(aoAbrirSge: () -> Unit) {
    val ctx = LocalContext.current
    var turmas by remember { mutableStateOf(SgeTurmasAutomacao.ler(ctx)) }
    var lista by remember { mutableStateOf(SgeLancamentosStore.ler(ctx)) }
    val opcoesCapturadas = remember { SgeOpcoesStore.total(ctx) }

    var turma by remember { mutableStateOf(turmas.singleOrNull()) }
    var serie by remember { mutableStateOf(turma?.serieDeduzida) }
    var data by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var componente by remember { mutableStateOf(Componente.QUIMICA) }
    var habilidade by remember { mutableStateOf<Habilidade?>(null) }
    var objetoUm by remember { mutableStateOf<String?>(null) }
    var objetoDois by remember { mutableStateOf<String?>(null) }
    var atividades by remember { mutableStateOf("") }
    var outros by remember { mutableStateOf("") }
    var mostrarOutros by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val habilidades = remember(serie) { serie?.let { SgeMatriz.daSerie(ctx, it) }.orEmpty() }
    val objetos = habilidade?.objetos?.get(componente).orEmpty()
    val gradeEscolarId = turma?.grades?.get(componente)
    val formatoLista = remember { DateTimeFormatter.ofPattern("dd/MM (EEE)", PtBr) }

    fun limparHabilidade() { habilidade = null; objetoUm = null; objetoDois = null }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Conteúdo com a Matriz", style = MaterialTheme.typography.displaySmall)
                Text("Escolha a habilidade da Matriz de Recomposição e escreva o que foi feito. No SGE, o app preenche a janela " +
                    "de Conteúdo Aplicado; você confere e toca em Salvar.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (turmas.isEmpty()) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nenhuma turma ainda", style = MaterialTheme.typography.titleMedium)
                    Text("No SGE Web, abra a turma > Conteúdo Aplicado e toque em Opções > Atualizar aulas da turma. " +
                        "Repita para cada turma.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = aoAbrirSge) { Text("Abrir SGE Web") }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DropdownCampo("Turma", turmas, turma, { it.descricao }, habilitado = turmas.isNotEmpty(), comBusca = false) {
                    turma = it; serie = it.serieDeduzida ?: serie; limparHabilidade()
                }
                CampoData("Data da aula", data) { data = it }

                ChipsGrupo("Série", listOf("1ª", "2ª", "3ª"), serie, { "$it série" }) { serie = it; limparHabilidade() }
                if (turma != null && turma?.serieDeduzida == null) {
                    Text("Não deu para saber a série pelo nome da turma. Escolha acima qual parte da Matriz usar.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                ChipsGrupo("Componente", Componente.entries, componente, { it.rotulo },
                    habilitado = { t -> turma == null || turma!!.grades.containsKey(t) }) { componente = it; objetoUm = null; objetoDois = null }
                if (turma != null && gradeEscolarId == null) {
                    Text("O app ainda não conhece ${componente.rotulo} nessa turma. No SGE, use Opções > Atualizar aulas da turma.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                DropdownCampo(
                    rotulo = serie?.let { "Habilidade ($it série)" } ?: "Habilidade",
                    opcoes = habilidades, selecionado = habilidade, textoDe = { it.rotulo(componente) },
                    habilitado = serie != null, comBusca = true, textoVazio = "Nada encontrado na Matriz",
                ) { habilidade = it; objetoUm = null; objetoDois = null }
                habilidade?.let { h ->
                    Text(h.textoSge, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5, overflow = TextOverflow.Ellipsis)
                    DropdownCampo("Objeto de conhecimento 1", objetos, objetoUm, { it }, comBusca = false,
                        textoVazio = "A Matriz não traz ${componente.rotulo} nessa habilidade") { objetoUm = it }
                    DropdownCampo("Objeto de conhecimento 2 (opcional)", objetos.filter { it != objetoUm }, objetoDois, { it },
                        comBusca = false) { objetoDois = it }
                }

                OutlinedTextField(value = atividades, onValueChange = { atividades = it },
                    label = { Text("Atividades / observações") }, placeholder = { Text("O que foi feito na aula") },
                    minLines = 5, shape = FormaCampo, modifier = Modifier.fillMaxWidth())
                if (mostrarOutros) {
                    OutlinedTextField(value = outros, onValueChange = { outros = it }, label = { Text("Outros") },
                        minLines = 2, shape = FormaCampo, modifier = Modifier.fillMaxWidth())
                } else TextButton(onClick = { mostrarOutros = true }, contentPadding = PaddingValues(0.dp)) { Text("+ Campo “Outros”") }

                val valido = turma != null && data != null && gradeEscolarId != null && serie != null &&
                    habilidade != null && atividades.isNotBlank()
                Button(
                    enabled = valido,
                    onClick = {
                        val t = turma!!
                        lista = SgeLancamentosStore.adicionar(ctx, LancamentoMatriz(
                            id = java.util.UUID.randomUUID().toString(), turmaId = t.id, turmaDescricao = t.descricao,
                            gradeEscolarId = gradeEscolarId!!, componente = componente, dataIso = data!!.toString(),
                            serie = serie!!, habilidadeId = habilidade!!.id, objetoUm = objetoUm, objetoDois = objetoDois,
                            outros = outros.trim().takeIf { it.isNotBlank() }, atividadesObs = atividades.trim()))
                        atividades = ""; outros = ""
                        aviso = "Rascunho salvo no aparelho."
                    },
                    shape = FormaCampo, modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Salvar rascunho") }
                aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }

        if (lista.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Rascunhos", style = MaterialTheme.typography.titleLarge)
                Text("Antes de preencher, deixe o SGE Web na turma certa, em Conteúdo Aplicado, com o mês da aula no Mapa de Registros.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(lista, key = { it.id }) { l ->
            val h = remember(l.habilidadeId) { SgeMatriz.porId(ctx, l.habilidadeId) }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dia = runCatching { LocalDate.parse(l.dataIso).format(formatoLista) }.getOrDefault(l.dataIso)
                        Text("$dia · ${l.turmaDescricao}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        SeloStatus(l.status)
                    }
                    Text("${l.componente.rotulo} · ${h?.codigo ?: "?"}" + (l.objetoUm?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(l.atividadesObs, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    l.erro?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (l.status != StatusLancamento.ENVIADO) TextButton(onClick = {
                            aoAbrirSge()
                            SgeAutomacaoHost.executar(ctx) { inj -> SgeLancamentoSync.preencher(ctx, inj, l).let { r ->
                                r.mensagem ?: if (r.sucesso) "Preenchido. Confira e toque em Salvar." else "Não foi possível preencher." } }
                        }) { Text(if (l.status == StatusLancamento.PENDENTE) "Preencher no SGE" else "Preencher de novo") }
                        TextButton(onClick = { lista = SgeLancamentosStore.remover(ctx, l.id) }) { Text("Excluir") }
                    }
                }
            }
        }
        if (lista.any { it.status == StatusLancamento.ENVIADO }) item {
            TextButton(onClick = { lista = SgeLancamentosStore.limparEnviados(ctx) }) { Text("Limpar enviados") }
        }
        item {
            Column {
                Text("Matriz: ${SgeMatriz.todas(ctx).size} habilidades · opções do SGE capturadas: $opcoesCapturadas",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { turmas = SgeTurmasAutomacao.ler(ctx); lista = SgeLancamentosStore.ler(ctx) }) { Text("Recarregar") }
            }
        }
    }
}

@Composable
private fun SeloStatus(status: String) {
    val (texto, cor) = when (status) {
        StatusLancamento.ENVIADO -> "Salvo" to Color(0xFF34C759)
        StatusLancamento.PREENCHIDO -> "Falta salvar" to Color(0xFFFF9500)
        StatusLancamento.ERRO -> "Erro" to Color(0xFFFF3B30)
        else -> "No aparelho" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = cor.copy(alpha = .14f)) {
        Text(texto, color = cor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Itens do menu do SGE Web ligados à automação (precisam da página do SGE aberta). */
@Composable
fun ItensMenuAutomacao(fechar: () -> Unit, aoAbrirTela: () -> Unit) {
    val ctx = LocalContext.current
    DropdownMenuItem(text = { Text("Atualizar aulas da turma") }, onClick = {
        fechar(); SgeAutomacaoHost.executar(ctx) { SgeLancamentoSync.atualizarAulas(ctx, it) }
    })
    DropdownMenuItem(text = { Text("Capturar opções do Conteúdo") }, onClick = {
        fechar(); SgeAutomacaoHost.executar(ctx) { SgeLancamentoSync.capturarOpcoes(ctx, it) }
    })
    DropdownMenuItem(text = { Text("Conteúdo com a Matriz") }, onClick = { fechar(); aoAbrirTela() })
}

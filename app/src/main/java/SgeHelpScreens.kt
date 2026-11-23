package br.to.seduc.sgediariowidget

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.EstadoGrupo
import br.to.seduc.sgediariowidget.data.SgeDashboard
import br.to.seduc.sgediariowidget.widget.TipoWidget

@Composable private fun LinhaExplicacao(icone: ImageVector, titulo: String, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icone, null, Modifier.padding(11.dp).size(21.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TelaIntroducao(papel: Int, aoPapel: (Int) -> Unit, concluir: (Boolean) -> Unit) {
    var pagina by rememberSaveable { mutableStateOf(0) }
    val escopo = rememberCoroutineScope()
    // Deslocamento da página em px: o dedo o atualiza na hora; ao soltar, o Animatable leva até o alvo.
    var deslPx by remember { mutableFloatStateOf(0f) }
    var largura by remember { mutableFloatStateOf(0f) }
    val anim = remember { Animatable(0f) }
    val ocupado = remember { mutableStateOf(false) }
    val arrastoEstado = rememberDraggableState { dx -> if (!ocupado.value) deslPx += dx }
    suspend fun animarPara(alvo: Float, spec: AnimationSpec<Float>) {
        anim.snapTo(deslPx)
        anim.animateTo(alvo, spec) { deslPx = value }
    }
    suspend fun trocar(nova: Int) {
        if (ocupado.value || nova !in 0..2 || nova == pagina) return
        val sentido = if (nova > pagina) 1 else -1
        ocupado.value = true
        try {
            animarPara(-sentido * largura, tween(SgeMotion.PAGINA, easing = SgeMotion.Saida))
            pagina = nova
            deslPx = sentido * largura
            animarPara(0f, tween(SgeMotion.PAGINA, easing = SgeMotion.Entrada))
        } finally {
            ocupado.value = false
        }
    }
    // Fundo praticamente opaco (e fosco sobre a tela borrada, no Android 12+) para o texto ficar sempre legível.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = if (Build.VERSION.SDK_INT >= 31) .92f else 1f))
        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f), MaterialTheme.colorScheme.background.copy(alpha = 0f)))),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SGE DIÁRIO", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { concluir(false) }) { Text("Agora não") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()
            .onSizeChanged { largura = it.width.toFloat() }
            .clipToBounds()
            .draggable(orientation = Orientation.Horizontal, state = arrastoEstado, onDragStopped = {
                if (!ocupado.value) {
                    val limite = largura * .25f
                    when {
                        deslPx < -limite && pagina < 2 -> trocar(pagina + 1)
                        deslPx > limite && pagina > 0 -> trocar(pagina - 1)
                        else -> animarPara(0f, tween(SgeMotion.PADRAO, easing = SgeMotion.Padrao))
                    }
                }
            })) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize().align(Alignment.TopCenter)
                .graphicsLayer { translationX = deslPx }
                .verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (pagina == 0) CartoesIntroducao() else Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                    Icon(if (pagina == 1) SgeIcons.Globe else SgeIcons.Check, null,
                        Modifier.padding(24.dp).size(52.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(when(pagina) { 0 -> "Seu diário,\ncom clareza."; 1 -> "Conecte. Consulte.\nAcompanhe."; else -> "Cada estado\ntem um significado." }, style = MaterialTheme.typography.displaySmall)
                Text(when(pagina) {
                    0 -> "Acompanhe conteúdos, frequência e planejamentos em cartões. Toque em uma turma para abrir os detalhes dos cinco módulos."
                    1 -> "O app consulta o SGE usando a sessão do SGE Web. A senha fica na página de login; o app não pede nem guarda sua senha."
                    else -> "Os números mostram a última consulta. Uma informação não verificada nunca aparece como uma pendência igual a zero."
                }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                        when(pagina) {
                            0 -> {
                                Text("COMO VOCÊ NAVEGA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Escalonado(0) { LinhaExplicacao(SgeIcons.Cards, "Seu diário", "Suas turmas e componentes, com busca e filtros por situação.") }
                                Escalonado(1) { LinhaExplicacao(SgeIcons.People, "Coordenação", "Visões por turma, componente e vínculo do professor.") }
                                Escalonado(2) { LinhaExplicacao(SgeIcons.Settings, "Widgets", "Escolha o recorte e o formato para cada widget.") }
                            }
                            1 -> {
                                Escalonado(0) { LinhaExplicacao(SgeIcons.Globe, "1. Entre no SGE Web", "Use Servidor para seu diário ou Unidade escolar para a coordenação.") }
                                Escalonado(1) { LinhaExplicacao(SgeIcons.Refresh, "2. Atualize os dados", "Na coordenação, abra Escola → Conteúdo Aplicado e carregue as turmas primeiro.") }
                                Escalonado(2) { LinhaExplicacao(SgeIcons.Book, "3. Resolva no SGE", "O aplicativo só consulta. Os lançamentos e correções continuam no SGE.") }
                            }
                            else -> {
                                Escalonado(0) { EstadoExplicado(EstadoGrupo.PENDENTE, "Há algo para resolver no SGE.") }
                                Escalonado(1) { EstadoExplicado(EstadoGrupo.CONFERIR, "Leitura parcial: confira antes de confiar. * é contagem parcial; — não há contagem confirmada.") }
                                Escalonado(2) { EstadoExplicado(EstadoGrupo.EM_DIA, "Módulos consultados sem nenhuma pendência.") }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(3) { i -> Box(Modifier.padding(4.dp).size(if (i == pagina) 9.dp else 7.dp).background(
                    if (i == pagina) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)) }
            }
            if (pagina < 2) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pagina > 0) OutlinedButton(onClick = { escopo.launch { trocar(pagina - 1) } }) { Text("Voltar") }
                Button(onClick = { escopo.launch { trocar(pagina + 1) } }, modifier = Modifier.weight(1f)) { Text("Continuar") }
            } else {
                // Perfil escolhido aqui (Professor = 1, Coordenador = 2): define o que aparece no app e qual perfil entrar no SGE Web.
                Text("Você usa o app como:", style = MaterialTheme.typography.labelLarge)
                Segmentado(listOf("Professor", "Coordenador"), when (papel) { 1 -> 0; 2 -> 1; else -> -1 }) { aoPapel(if (it == 0) 1 else 2) }
                Button(onClick = { concluir(true) }, enabled = papel in 1..2, modifier = Modifier.fillMaxWidth()) { Text("Conectar ao SGE") }
                TextButton(onClick = { concluir(false) }, modifier = Modifier.fillMaxWidth()) { Text("Ver meu painel") }
            }
        }
    }
}

/** Cada linha entra em sequência (fade e subida curta). Com animações desligadas, aparece pronta. */
@Composable
private fun Escalonado(indice: Int, conteudo: @Composable () -> Unit) {
    val contexto = LocalContext.current
    val densidade = LocalDensity.current
    val semMovimento = remember { Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val p = remember { Animatable(if (semMovimento) 1f else 0f) }
    LaunchedEffect(Unit) { if (!semMovimento) p.animateTo(1f, tween(360, delayMillis = 90 * indice, easing = SgeMotion.Entrada)) }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * with(densidade) { 16.dp.toPx() } }) { conteudo() }
}

/** Três cartões de estado que se alinham ao entrar. Decorativo: fica oculto para o TalkBack. */
@Composable
private fun CartoesIntroducao() {
    val contexto = LocalContext.current
    // Com animações desligadas nas configurações de acessibilidade, os cartões já aparecem prontos.
    val semMovimento = remember { Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var entrou by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrou = true }
    val p by animateFloatAsState(if (entrou || semMovimento) 1f else 0f, tween(600, easing = SgeMotion.Entrada), label = "cartoes")
    val cartoes = listOf(
        Triple("Pendências", MaterialTheme.colorScheme.error, "Chamada · 3 dias"),
        Triple("A conferir", corAlerta(), "Planos · leitura parcial"),
        Triple("Em dia", MaterialTheme.colorScheme.tertiary, "Conteúdo Aplicado"))
    // Posição final de cada cartão (dp) e rotação; partem empilhados no centro.
    val finais = listOf(Triple(14f, 26f, -6f), Triple(50f, 12f, -1f), Triple(86f, 0f, 4f))
    Box(Modifier.fillMaxWidth().height(170.dp).clearAndSetSemantics { }, contentAlignment = Alignment.TopStart) {
        cartoes.forEachIndexed { i, (titulo, cor, detalhe) ->
            val (x, y, giro) = finais[i]
            Surface(
                modifier = Modifier
                    .offset(x = (68f + (x - 68f) * p).dp, y = (30f + (y - 30f) * p).dp)
                    .width(200.dp)
                    .graphicsLayer { rotationZ = giro * p; alpha = (0.4f + 0.6f * p) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(titulo, style = MaterialTheme.typography.labelMedium, color = cor)
                    Text(detalhe, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable private fun EstadoExplicado(estado: EstadoGrupo, texto: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EmblemaEstado(estado)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TelaAjuda() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(19.dp)) {
        Text("Leia seu diário\ncom segurança.", style = MaterialTheme.typography.displaySmall)
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                EstadoExplicado(EstadoGrupo.PENDENTE, "Há pendências conhecidas. Abra o cartão para ver o módulo e o detalhe informado na consulta.")
                EstadoExplicado(EstadoGrupo.CONFERIR, "A consulta não cobre todos os dados. Isso não confirma ausência de pendências.")
                EstadoExplicado(EstadoGrupo.EM_DIA, "Todos os cinco módulos têm contagem verificada, cobertura completa e nenhuma pendência.")
                EstadoExplicado(EstadoGrupo.SEM_DADOS, "A turma aparece na lista da escola, mas ainda não tem uma consulta concluída.")
            }
        }
        Text("Uma turma pode ter pendências conhecidas e também dados a conferir. Por isso, ela pode aparecer nos dois contadores da visão geral.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Os cinco módulos", style = MaterialTheme.typography.titleLarge)
        SgeDashboard.nomes.forEachIndexed { i, nome ->
            LinhaExplicacao(iconeModulo(i), nome, SgeDashboard.descricoes[i])
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Símbolos e avaliação", style = MaterialTheme.typography.titleMedium)
                Text("—  Não verificado: nenhuma contagem é confirmada.\n\n*  Cobertura parcial: o valor conhecido pode aumentar quando o restante dos dados for conferido. Até 0* continua a conferir.\n\nAguardando avaliação: o documento está na etapa de avaliação e não conta como pendência de envio do professor.\n\nAprovado: documento aprovado pelo fluxo do SGE.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Agrupamento por professor", style = MaterialTheme.typography.titleLarge)
        Text("O app usa os vínculos do horário do SGE ou os vínculos locais que você informa. As consultas continuam sendo da turma e do componente. Docentes que compartilham um vínculo aparecem juntos para não duplicar o mesmo resultado. A autoria individual de cada registro não é inferida.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Dados e sincronização", style = MaterialTheme.typography.titleLarge)
        Text("Os painéis ficam no aparelho e mostram a última consulta. Conexão, sessão expirada e mudanças de perfil podem impedir uma atualização. A sincronização do diário pessoal é solicitada a cada duas horas; o Android define quando a tarefa realmente roda. As verificações da coordenação e dos vínculos são iniciadas por você.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TelaWidgets(adicionar: (TipoWidget) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Seu diário,\nà primeira vista.", style = MaterialTheme.typography.displaySmall)
        Text("Cada widget tem sua própria configuração. Escolha a origem, o agrupamento e um recorte específico no botão Ajustar do widget.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TipoWidget.values().forEach { tipo ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tipo.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                            Text(tipo.tamanho, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("PRÉVIA ILUSTRATIVA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(when(tipo) { TipoWidget.RESUMO -> "Turmas pendentes  ·  A conferir"
                                TipoWidget.FOCO -> "Turma selecionada  ·  Anterior / Próxima"
                                TipoWidget.PROFESSORES -> "Professor selecionado"
                                TipoWidget.COMPONENTES -> "Componente selecionado"
                                TipoWidget.COORDENACAO -> "Visão da escola"; else -> "Suas turmas" }, style = MaterialTheme.typography.titleSmall)
                            Text("Conteúdos   Frequência   Planos   PEI", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(tipo.descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { adicionar(tipo) }) { Icon(SgeIcons.Plus, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Adicionar widget") }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Na tela bloqueada", style = MaterialTheme.typography.titleMedium)
                Text("Comece por Resumo do diário para uma leitura discreta, ou Cartão em foco para acompanhar uma turma. A opção Ocultar nomes é útil nessa área. A disponibilidade, os gestos e o tamanho dependem do aparelho e de seu host de widgets; em Galaxy, pode ser necessário usar o LockStar.", style = MaterialTheme.typography.bodyMedium)
                Text("O app não mantém uma tela sobre o bloqueio e não exige permissão de sobreposição.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TelaAjustes(tema: Int, cor: Int, versao: String, aoTema: (Int) -> Unit, aoCor: (Int) -> Unit,
    aoWidgets: () -> Unit, aoAjuda: () -> Unit, aoIntroducao: () -> Unit, aoDiagnostico: () -> Unit,
    papel: Int, aoPapel: (Int) -> Unit, lembrete: Boolean, aoLembrete: (Boolean) -> Unit,
    aoSobre: () -> Unit, aoSair: () -> Unit, aoLimpar: () -> Unit,
    aoHistorico: () -> Unit = {}, bloqueio: Boolean = false, aoBloqueio: (Boolean) -> Unit = {},
    noturna: Boolean = false, aoNoturna: (Boolean) -> Unit = {}, statusNoturno: String = "", aoEconomia: ()->Unit = {}, aoBackup: ()->Unit = {}, aoAtualizacoes: ()->Unit = {},
    horarioLembrete: String = "17:00", aoEscolherHorarioLembrete: () -> Unit = {},
    bimestre: Int = 0, aoBimestre: (Int) -> Unit = {}, aoRascunhos: () -> Unit = {}, aoPreenchimento: () -> Unit = {},
    categoria: String? = null, aoCategoria: (String?) -> Unit = {}) {
    var confirmar by remember { mutableStateOf(0) }
    var bimestreEscolhido by remember { mutableStateOf(bimestre) }
    var saude by remember { mutableStateOf(false) }
    val ctxUi = LocalContext.current
    // Tela inicial curta (só as categorias); cada categoria abre a própria página.
    AnimatedContent(targetState = categoria, label = "ajustes",
        transitionSpec = {
            SgeMotion.lateral(if (targetState != null) 1 else -1, fracaoEntra = 3, fracaoSai = 5)
        }) { cat ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        when (cat) {
            null -> {
                Text("Do seu jeito.", style = MaterialTheme.typography.displaySmall)
                CartaoAjustes {
                    CategoriasAjustes.forEachIndexed { i, c ->
                        if (i > 0) DivisorAjuste()
                        LinhaAjuste(c.titulo, if (c.id == CategoriaAjustes.SOBRE) "SGE Diário $versao · atualizações" else c.resumo, c.icone) { aoCategoria(c.id) }
                    }
                }
                NotaAjustes("O aplicativo consulta os dados do SGE. Lançamentos e correções são feitos pelo usuário no próprio SGE.")
            }

            CategoriaAjustes.CONTA -> {
                GrupoAjustes("Dados") {
                    CartaoAjustes {
                        LinhaAjuste("Backup e restauração", "Salvar e recuperar dados sem senha ou sessão", SgeIcons.Export, aoBackup)
                        DivisorAjuste()
                        LinhaAjuste("Lançamentos offline", "Rascunhos de conteúdo e frequência para lançar depois", SgeIcons.Document, aoRascunhos)
                        DivisorAjuste()
                        LinhaAjuste("Histórico e ranking semanal", "Comparação por módulo nas últimas quatro semanas", SgeIcons.Clock, aoHistorico)
                    }
                }
                GrupoAjustes("Sessão e segurança") {
                    CartaoAjustes {
                        LinhaChave("Proteger coordenação", "Biometria ou credencial do aparelho para a coordenação, o SGE Web, os diagnósticos e os widgets.", bloqueio, aoBloqueio)
                        DivisorAjuste()
                        LinhaAjuste("Sair do SGE", "Encerra a sessão neste aparelho", SgeIcons.Globe) { confirmar = 1 }
                        DivisorAjuste()
                        LinhaAjuste("Apagar dados locais", "Painéis, histórico, vínculos e diagnósticos guardados aqui", SgeIcons.Close) { confirmar = 2 }
                    }
                }
            }

            CategoriaAjustes.SINCRONIZACAO -> {
                GrupoAjustes("Consultas") {
                    CartaoAjustes {
                        LinhaAjuste("Sincronização econômica", "Intervalo, rede, bateria e horário noturno", SgeIcons.Refresh, aoEconomia)
                        DivisorAjuste()
                        LinhaChave("Verificação noturna", "Consulta as turmas da escola no horário escolhido em Sincronização econômica. Exige o perfil Unidade escolar.", noturna, aoNoturna)
                        if (statusNoturno.isNotBlank()) Text(statusNoturno, Modifier.padding(12.dp, 0.dp, 12.dp, 10.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                GrupoAjustes("Lançamento") {
                    CartaoAjustes {
                        LinhaAjuste("Preenchimento automático", "Conteúdo com a Matriz: o app preenche a janela do SGE e você salva", SgeIcons.Book, aoPreenchimento)
                    }
                }
                GrupoAjustes("Turmas e bimestre") {
                    CartaoAjustes(Modifier.padding(11.dp), Arrangement.spacedBy(12.dp)) {
                        val ctxTurmas = LocalContext.current
                        Text("Escolarização (ex.: 13.01) entra sempre. As demais só se você ativar.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf("DIVERSIFICADA" to "Diversificadas (ex.: 115.09)", "AEE" to "AEE (ex.: 1111.01)").forEach { (categoriaTurma, titulo) ->
                            var ativa by remember { mutableStateOf(br.to.seduc.sgediariowidget.data.SgeEscopo.ativa(ctxTurmas, categoriaTurma)) }
                            val mudarEscopo: (Boolean) -> Unit = {
                                ativa = it
                                br.to.seduc.sgediariowidget.data.SgeEscopo.salvar(ctxTurmas, categoriaTurma, it)
                                br.to.seduc.sgediariowidget.worker.SgeSyncWorker.sincronizarAgora(ctxTurmas)
                            }
                            Row(Modifier.heightIn(min = 48.dp).linhaInterruptor(ativa, aoMudar = mudarEscopo), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(titulo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Switch(checked = ativa, onCheckedChange = null)
                            }
                        }
                        HorizontalDivider()
                        Text("Bimestre do painel", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Atual", 1 to "1º", 2 to "2º", 3 to "3º", 4 to "4º", 5 to "Todos").forEach { (valor, rotulo) ->
                                FilterChip(selected = bimestreEscolhido == valor, onClick = {
                                    bimestreEscolhido = valor; aoBimestre(valor)
                                    android.widget.Toast.makeText(ctxUi, "Bimestre: $rotulo. Toque em atualizar para carregar.", android.widget.Toast.LENGTH_SHORT).show()
                                }, label = { Text(rotulo) })
                            }
                        }
                    }
                }
                GrupoAjustes("Alertas") {
                    SecaoExtras()
                    CartaoAjustes {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Lembrete diário", style = MaterialTheme.typography.titleSmall)
                                Text("Resumo da última consulta, só quando há pendências.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = aoEscolherHorarioLembrete, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text("Horário: $horarioLembrete")
                                }
                            }
                            Switch(checked = lembrete, onCheckedChange = aoLembrete)
                        }
                    }
                }
            }

            CategoriaAjustes.PERSONALIZACAO -> {
                GrupoAjustes("Tela inicial") {
                    CartaoAjustes {
                        LinhaAjuste("Galeria de widgets", "Seis formatos e recortes independentes", SgeIcons.Cards, aoWidgets)
                    }
                }
                GrupoAjustes("Aparência") {
                    CartaoAjustes(Modifier.padding(11.dp), Arrangement.spacedBy(12.dp)) {
                        Text("Tema", style = MaterialTheme.typography.titleSmall)
                        Segmentado(listOf("Sistema", "Claro", "Escuro"), tema, aoTema)
                        Text("Cores", style = MaterialTheme.typography.titleSmall)
                        // Ordem na tela: Verde, Lavanda, Do sistema (valores gravados: 0, 2, 1).
                        val ordemCores = listOf(EstiloCor.VERDE, EstiloCor.LAVANDA, EstiloCor.SISTEMA)
                        Segmentado(listOf("Verde", "Lavanda", "Do sistema"), ordemCores.indexOf(cor).coerceAtLeast(0)) { aoCor(ordemCores[it]) }
                    }
                }
                GrupoAjustes("Como você usa o app") {
                    CartaoAjustes(Modifier.padding(11.dp), Arrangement.spacedBy(10.dp)) {
                        Segmentado(listOf("Ambos", "Professor", "Coordenação"), papel, aoPapel)
                        Text(when (papel) { 1 -> "Mostra só o seu diário. A aba Coordenação fica oculta."
                            2 -> "Mostra só a coordenação. O seu diário pessoal fica oculto."
                            else -> "Mostra o seu diário e a coordenação." },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                GrupoAjustes("Acessibilidade e privacidade") {
                    CartaoAjustes {
                        LinhaChave("Modo privacidade", "Esconde o app nos recentes, bloqueia capturas e mostra professores só com iniciais.", SgeUi.privacidade) { SgeUi.definir(ctxUi, SgeUi.PRIVACIDADE, it) }
                        DivisorAjuste()
                        LinhaChave("Fonte maior", "Aumenta os textos do app em cerca de 30%.", SgeUi.fonteGrande) { SgeUi.definir(ctxUi, SgeUi.FONTE_GRANDE, it) }
                        DivisorAjuste()
                        LinhaChave("Alto contraste", "Textos e contornos mais fortes.", SgeUi.altoContraste) { SgeUi.definir(ctxUi, SgeUi.ALTO_CONTRASTE, it) }
                    }
                    NotaAjustes("Teclado: Ctrl+R atualiza · Ctrl+F busca · Ctrl+1 Seu diário · Ctrl+2 Coordenação.")
                }
            }

            CategoriaAjustes.AJUDA -> {
                CartaoAjustes {
                    LinhaAjuste("Como interpretar", "Módulos, estados e símbolos", SgeIcons.Info, aoAjuda)
                    DivisorAjuste()
                    LinhaAjuste("Ver introdução", "O funcionamento do app em três passos", SgeIcons.Book, aoIntroducao)
                    DivisorAjuste()
                    LinhaAjuste("Saúde do app", "Última consulta por módulo, falhas recentes e botão reparar", SgeIcons.Alert) { saude = true }
                    DivisorAjuste()
                    LinhaAjuste("Diagnóstico único", "Professor, coordenação, perfil e agendamentos em um só texto", SgeIcons.Settings, aoDiagnostico)
                }
            }

            CategoriaAjustes.SOBRE -> {
                CartaoAjustes {
                    LinhaAjuste("Atualizações do app", "Verificar, baixar e instalar por aqui", SgeIcons.Refresh, aoAtualizacoes)
                    DivisorAjuste()
                    LinhaAjuste("Versão", "SGE Diário $versao · créditos, privacidade e licenças", SgeIcons.User, aoSobre)
                }
                NotaAjustes("Atalho rápido: mantenha pressionado o ícone do app ou adicione “Meu diário” ao Painel rápido do Android.")
            }
        }
    }
    }
    if (saude) SaudeDialog { saude = false }
    if (confirmar != 0) AlertDialog(onDismissRequest = { confirmar = 0 },
        title = { Text(if (confirmar == 1) "Sair do SGE?" else "Apagar tudo e recomeçar?") },
        text = { Text(if (confirmar == 1) "A sessão será encerrada neste aparelho e os dados de navegação do SGE Web serão removidos. Você precisará entrar de novo no SGE Web."
            else "Painéis, histórico, preferências e a sessão do SGE Web serão apagados neste aparelho. O app recomeça pela introdução e você escolhe de novo se é professor ou coordenador. O SGE não é alterado.") },
        confirmButton = { Button(onClick = { if (confirmar == 1) aoSair() else aoLimpar(); confirmar = 0 }) { Text(if (confirmar == 1) "Sair" else "Apagar") } },
        dismissButton = { TextButton(onClick = { confirmar = 0 }) { Text("Cancelar") } })
}

@Composable private fun LinhaChave(titulo: String, texto: String, marcado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().linhaInterruptor(marcado, aoMudar = aoMudar).padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = marcado, onCheckedChange = null)
    }
}

data class CategoriaAjuste(val id: String, val titulo: String, val resumo: String, val icone: ImageVector)

object CategoriaAjustes {
    const val CONTA = "conta"
    const val SINCRONIZACAO = "sincronizacao"
    const val PERSONALIZACAO = "personalizacao"
    const val AJUDA = "ajuda"
    const val SOBRE = "sobre"
    fun titulo(id: String?) = CategoriasAjustes.firstOrNull { it.id == id }?.titulo
}

val CategoriasAjustes = listOf(
    CategoriaAjuste(CategoriaAjustes.CONTA, "Conta e dados", "Backup, lançamentos offline, histórico e sessão", SgeIcons.User),
    CategoriaAjuste(CategoriaAjustes.SINCRONIZACAO, "Sincronização", "Economia, preenchimento automático, turmas e alertas", SgeIcons.Refresh),
    CategoriaAjuste(CategoriaAjustes.PERSONALIZACAO, "Personalização", "Widgets, tema, uso do app e acessibilidade", SgeIcons.Cards),
    CategoriaAjuste(CategoriaAjustes.AJUDA, "Ajuda e diagnóstico", "Legendas, introdução, saúde do app e diagnóstico", SgeIcons.Info),
    CategoriaAjuste(CategoriaAjustes.SOBRE, "Sobre", "Atualizações e versão", SgeIcons.Book),
)

/** Grupo no estilo dos Ajustes do iOS/Android: título pequeno acima dos cartões. */
@Composable private fun GrupoAjustes(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo.uppercase(), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        conteudo()
    }
}

@Composable private fun CartaoAjustes(modifier: Modifier = Modifier.padding(8.dp),
    arranjo: Arrangement.Vertical = Arrangement.Top, conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier, verticalArrangement = arranjo, content = conteudo)
    }
}

@Composable private fun DivisorAjuste() {
    HorizontalDivider(Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}

@Composable private fun NotaAjustes(texto: String) {
    Text(texto, Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun LinhaAjuste(titulo: String, texto: String, icone: ImageVector, abrir: () -> Unit) {
    TextButton(onClick = abrir, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp, 14.dp)) {
        Icon(icone, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(SgeIcons.Chevron, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

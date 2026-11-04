package br.to.seduc.sgediariowidget

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
fun TelaIntroducao(concluir: (Boolean) -> Unit) {
    var pagina by rememberSaveable { mutableStateOf(0) }
    // Fundo praticamente opaco (e fosco sobre a tela borrada, no Android 12+) para o texto ficar sempre legível.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = if (Build.VERSION.SDK_INT >= 31) .92f else 1f))
        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f), MaterialTheme.colorScheme.background.copy(alpha = 0f)))),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SGE DIÁRIO", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { concluir(false) }) { Text("Agora não") }
        }
        AnimatedContent(targetState = pagina, label = "introducao", modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { etapa ->
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                    Icon(when(etapa) { 0 -> SgeIcons.Cards; 1 -> SgeIcons.Globe; else -> SgeIcons.Check }, null,
                        Modifier.padding(24.dp).size(52.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(when(etapa) { 0 -> "Seu diário,\ncom clareza."; 1 -> "Conecte. Consulte.\nAcompanhe."; else -> "Cada estado\ntem um significado." }, style = MaterialTheme.typography.displaySmall)
                Text(when(etapa) {
                    0 -> "Acompanhe conteúdos, frequência e planejamentos em cartões. Toque em uma turma para abrir os detalhes dos cinco módulos."
                    1 -> "O app consulta o SGE usando a sessão do SGE Web. A senha fica na página de login; o app não pede nem guarda sua senha."
                    else -> "Os números mostram a última consulta. Uma informação não verificada nunca aparece como uma pendência igual a zero."
                }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                        when(etapa) {
                            0 -> {
                                Text("COMO VOCÊ NAVEGA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinhaExplicacao(SgeIcons.Cards, "Seu diário", "Suas turmas e componentes, com busca e filtros por situação.")
                                LinhaExplicacao(SgeIcons.People, "Coordenação", "Visões por turma, componente e vínculo do professor.")
                                LinhaExplicacao(SgeIcons.Settings, "Widgets", "Escolha o recorte e o formato para cada widget.")
                            }
                            1 -> {
                                LinhaExplicacao(SgeIcons.Globe, "1. Entre no SGE Web", "Use Servidor para seu diário ou Unidade escolar para a coordenação.")
                                LinhaExplicacao(SgeIcons.Refresh, "2. Atualize os dados", "Na coordenação, abra Escola → Conteúdo Aplicado e carregue as turmas primeiro.")
                                LinhaExplicacao(SgeIcons.Book, "3. Resolva no SGE", "O aplicativo só consulta. Os lançamentos e correções continuam no SGE.")
                            }
                            else -> {
                                EstadoExplicado(EstadoGrupo.PENDENTE, "Há dias ou documentos pendentes conhecidos.")
                                EstadoExplicado(EstadoGrupo.CONFERIR, "A leitura é parcial ou falta verificar algum módulo. * indica contagem parcial; — indica que não há contagem confirmada.")
                                EstadoExplicado(EstadoGrupo.EM_DIA, "Todos os módulos consultados têm cobertura completa e nenhuma pendência.")
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
                if (pagina > 0) OutlinedButton(onClick = { pagina-- }) { Text("Voltar") }
                Button(onClick = { pagina++ }, modifier = Modifier.weight(1f)) { Text("Continuar") }
            } else {
                Button(onClick = { concluir(true) }, modifier = Modifier.fillMaxWidth()) { Text("Conectar ao SGE") }
                TextButton(onClick = { concluir(false) }, modifier = Modifier.fillMaxWidth()) { Text("Ver meu painel") }
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
    noturna: Boolean = false, aoNoturna: (Boolean) -> Unit = {}, statusNoturno: String = "", aoEconomia: ()->Unit = {}, aoBackup: ()->Unit = {}, aoAtualizacoes: ()->Unit = {}) {
    var confirmar by remember { mutableStateOf(0) }
    var saude by remember { mutableStateOf(false) }
    val ctxUi = LocalContext.current
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 320.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Do seu jeito.", style = MaterialTheme.typography.displaySmall)
                Text("Preferências reunidas por tema.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GrupoAjustes("Coordenação") {
                LinhaChave("Proteger coordenação", "Biometria e, no Android 11+, credencial do aparelho. Protege coordenação, SGE Web, diagnósticos e widgets; bloqueia novamente em segundo plano.", bloqueio, aoBloqueio)
                HorizontalDivider()
                LinhaChave("Verificação noturna", "No horário e nas condições escolhidos em Sincronização econômica, consulta turmas conhecidas. Exige sessão Unidade escolar; o Android pode adiar.", noturna, aoNoturna)
                if (statusNoturno.isNotBlank()) Text(statusNoturno, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GrupoAjustes("Aparência") {
                Text("Tema", style = MaterialTheme.typography.titleSmall)
                Segmentado(listOf("Sistema", "Claro", "Escuro"), tema, aoTema)
                Text("Cores", style = MaterialTheme.typography.titleSmall)
                Segmentado(listOf("Neutras", "Do sistema"), cor, aoCor)
            }
        }
        item {
            GrupoAjustes("Seu espaço") {
                Text("Como você usa o app", style = MaterialTheme.typography.titleSmall)
                Segmentado(listOf("Ambos", "Professor", "Coordenação"), papel, aoPapel)
                Text(when (papel) { 1 -> "Mostra só seu diário; Coordenação fica oculta."
                    2 -> "Mostra só Coordenação; seu diário pessoal fica oculto."
                    else -> "Mostra seu diário e Coordenação." },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GrupoAjustes("Lembrete diário") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Resumo às 17h", style = MaterialTheme.typography.titleSmall)
                        Text("Última consulta e variação desde o lembrete anterior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = lembrete, onCheckedChange = aoLembrete)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Acesso rápido", style = MaterialTheme.typography.titleLarge) }
        item { LinhaAjuste("Histórico semanal", "Consultas e contagens dos últimos sete dias", SgeIcons.Clock, aoHistorico) }
        item { LinhaAjuste("Galeria de widgets", "Seis formatos e recortes independentes", SgeIcons.Cards, aoWidgets) }
        item { LinhaAjuste("Como interpretar", "Módulos, estados e símbolos", SgeIcons.Info, aoAjuda) }
        item { LinhaAjuste("Ver introdução", "O funcionamento do app em três passos", SgeIcons.Book, aoIntroducao) }
        item { LinhaAjuste("Diagnóstico único", "Professor, coordenação, perfil e agendamentos", SgeIcons.Settings, aoDiagnostico) }
        item { LinhaAjuste("Saúde do app", "Última consulta, falhas recentes e reparo", SgeIcons.Info) { saude = true } }
        item { LinhaAjuste("Sincronização econômica", "Intervalo, rede, bateria e horário noturno", SgeIcons.Refresh, aoEconomia) }
        item { LinhaAjuste("Backup e restauração", "Salvar e recuperar dados sem senha ou sessão", SgeIcons.Export, aoBackup) }
        item { LinhaAjuste("Atualizações do app", "Verificar, baixar e instalar por aqui", SgeIcons.Refresh, aoAtualizacoes) }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Privacidade e acessibilidade", style = MaterialTheme.typography.titleLarge) }
        item { CartaoChave("Modo privacidade", "Esconde o app na tela de recentes, bloqueia capturas e mostra professores só com iniciais.", SgeUi.privacidade) { SgeUi.definir(ctxUi, SgeUi.PRIVACIDADE, it) } }
        item { CartaoChave("Fonte maior", "Aumenta os textos do app em cerca de 30%.", SgeUi.fonteGrande) { SgeUi.definir(ctxUi, SgeUi.FONTE_GRANDE, it) } }
        item { CartaoChave("Alto contraste", "Textos e contornos mais fortes; o estado também aparece em texto e ícone.", SgeUi.altoContraste) { SgeUi.definir(ctxUi, SgeUi.ALTO_CONTRASTE, it) } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Text("Teclado (notebook ou tablet): Ctrl+R atualiza · Ctrl+F busca · Ctrl+1 Seu diário · Ctrl+2 Coordenação.",
                    Modifier.fillMaxWidth().padding(15.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Sessão e dados locais", style = MaterialTheme.typography.titleLarge) }
        item { LinhaAjuste("Sair do SGE", "Encerra a sessão neste aparelho", SgeIcons.Globe, { confirmar = 1 }, perigo = true) }
        item { LinhaAjuste("Apagar dados locais", "Painéis, histórico, vínculos e diagnósticos guardados aqui", SgeIcons.Close, { confirmar = 2 }, perigo = true) }
        item { LinhaAjuste("Sobre", "Versão, créditos, privacidade e licenças", SgeIcons.User, aoSobre) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("SGE Diário · $versao", style = MaterialTheme.typography.labelLarge)
                Text("O aplicativo consulta os dados do SGE. Lançamentos e correções são feitos pelo usuário no próprio SGE.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (saude) SaudeDialog { saude = false }
    if (confirmar != 0) AlertDialog(onDismissRequest = { confirmar = 0 },
        title = { Text(if (confirmar == 1) "Sair do SGE?" else "Apagar dados locais?") },
        text = { Text(if (confirmar == 1) "A sessão será encerrada neste aparelho. Você precisará entrar de novo no SGE Web."
            else "Os painéis, histórico e vínculos guardados neste aparelho serão apagados. Suas preferências são mantidas. O SGE não é alterado.") },
        confirmButton = { Button(onClick = { if (confirmar == 1) aoSair() else aoLimpar(); confirmar = 0 }) { Text(if (confirmar == 1) "Sair" else "Apagar") } },
        dismissButton = { TextButton(onClick = { confirmar = 0 }) { Text("Cancelar") } })
}

@Composable private fun LinhaChave(titulo: String, texto: String, marcado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = marcado, onCheckedChange = aoMudar)
    }
}

@Composable private fun GrupoAjustes(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            conteudo()
        }
    }
}

@Composable private fun CartaoChave(titulo: String, texto: String, marcado: Boolean, aoMudar: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(15.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = marcado, onCheckedChange = aoMudar)
        }
    }
}

@Composable private fun LinhaAjuste(titulo: String, texto: String, icone: ImageVector, abrir: () -> Unit, perigo: Boolean = false) {
    val tintaIcone = if (perigo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(onClick = abrir, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(.6.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(min = 132.dp).padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icone, null, Modifier.size(21.dp), tint = tintaIcone)
                Spacer(Modifier.weight(1f))
                Icon(SgeIcons.Chevron, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = if (perigo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

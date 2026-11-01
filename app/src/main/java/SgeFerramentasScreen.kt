@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package br.to.seduc.sgediariowidget

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.to.seduc.sgediariowidget.data.*

data class RecursosSge(
    val organizacao: Map<String,DadosTurma> = emptyMap(), val economia: EconomiaSge = EconomiaSge(),
    val aoOrganizacao: (Map<String,DadosTurma>) -> Unit = {}, val aoEconomia: (EconomiaSge) -> Unit = {},
    val aoBackup: () -> Unit = {}, val aoRestaurar: () -> Unit = {}, val aoAtualizacoes: () -> Unit = {},
    val aoLote: (List<GrupoPendencias>,String,String,String) -> Unit = { _,_,_,_-> }
)

@Composable fun EscolhaFiltro(titulo: String, atual: String, opcoes: List<Pair<String,String>>, escolher: (String)->Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { menu = true },modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(titulo,style=MaterialTheme.typography.labelSmall)
                Text(opcoes.firstOrNull { it.first == atual }?.second ?: atual.ifBlank { "Todos" },maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(SgeIcons.Chevron,null,Modifier.size(16.dp))
        }
        DropdownMenu(menu,{ menu=false },modifier=Modifier.heightIn(max=340.dp)) { opcoes.forEach { (id,nome) -> DropdownMenuItem(text={ Text(nome) },onClick={ escolher(id);menu=false }) } }
    }
}
@Composable fun FiltrosDialog(atual: FiltroAvancado, rs: List<ResultadoTurma>, org: Map<String,DadosTurma>, docentes: PainelDocentes, fechar: ()->Unit, aplicar: (FiltroAvancado)->Unit) {
    var f by remember(atual) { mutableStateOf(atual) }
    fun opcoes(xs: List<String>) = listOf("" to "Todos") + xs.filter { it.isNotBlank() }.distinct().sorted().map { it to it }
    AlertDialog(onDismissRequest=fechar,title={ Text("Filtros avançados") },text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("O recorte vale para cartões, mapa e relatórios em lote. Série e turno vêm da organização local, sem deduzir pelo código da turma.",style=MaterialTheme.typography.bodySmall)
        EscolhaFiltro("Escola",f.escola,opcoes(rs.map { SgeOrganizacao.dados(it,org).escola })) { f=f.copy(escola=it) }
        EscolhaFiltro("Série / etapa",f.serie,opcoes(org.values.map { it.serie })) { f=f.copy(serie=it) }
        EscolhaFiltro("Turno",f.turno,opcoes(org.values.map { it.turno })) { f=f.copy(turno=it) }
        EscolhaFiltro("Componente",f.componente,opcoes(rs.map { it.turma.disciplina })) { f=f.copy(componente=it) }
        EscolhaFiltro("Professor",f.professor,listOf("" to "Todos") + docentes.opcoesDocentes().map { it.id to it.nome }) { f=f.copy(professor=it) }
        EscolhaFiltro("Módulo",f.modulo.toString(),listOf("-1" to "Todos") + SgeDashboard.nomes.mapIndexed { i,n-> i.toString() to n }) { f=f.copy(modulo=it.toInt()) }
        EscolhaFiltro("Situação",f.estado.toString(),listOf("0" to "Todas", "1" to "Com pendências", "2" to "A conferir", "3" to "Em dia no módulo/recorte")) { f=f.copy(estado=it.toInt()) }
        TextButton(onClick={ f=FiltroAvancado() }) { Text("Limpar filtros") }
    } },confirmButton={ Button(onClick={ aplicar(f);fechar() }) { Text("Aplicar filtros") } },dismissButton={ TextButton(onClick=fechar) { Text("Cancelar") } })
}

@Composable fun BarraRecorte(f: FiltroAvancado, filtrar: ()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(if(f.ativo) "Recorte ativo" else "Toda a escola",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick=filtrar) { Text("Filtros avançados") }
    }
}
fun descreverFiltro(f: FiltroAvancado, docentes: PainelDocentes): String = listOfNotNull(f.escola.takeIf { it.isNotBlank() },f.serie.takeIf { it.isNotBlank() },f.turno.takeIf { it.isNotBlank() },f.componente.takeIf { it.isNotBlank() },docentes.opcoesDocentes().firstOrNull { it.id==f.professor }?.nome,SgeDashboard.nomes.getOrNull(f.modulo),listOf("", "Com pendências", "A conferir", "Em dia").getOrElse(f.estado) { "" }.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "Todos os resultados consultados" }

@Composable fun TelaMapa(rs: List<ResultadoTurma>, conhecidas: List<Pair<String,String>>, docentes: PainelDocentes, filtro: FiltroAvancado, org: Map<String,DadosTurma>, filtrar: ()->Unit, organizar: ()->Unit, abrir: (GrupoPendencias,Int)->Unit) {
    var professores by remember { mutableStateOf(false) }
    val filtrados = SgeOrganizacao.filtrar(rs,filtro,org,docentes)
    val grupos = if(professores) SgeOrganizacao.porProfessor(filtrados,docentes,filtro.professor) else SgeDashboard.agrupar(filtrados,Agrupamento.TURMA) + if(!filtro.ativo) conhecidas.filter { (id,_)->filtrados.none { it.turma.id==id } }.map { (id,nome)->GrupoPendencias("turma:$id",nome,"Sem consulta",emptyList(),setOf(id)) } else emptyList()
    val scroll = rememberScrollState()
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Mapa da escola",style=MaterialTheme.typography.displaySmall) }
        item { Text("Visão das pendências por turma ou professor. Deslize na horizontal para ver os módulos e toque numa célula para abrir os detalhes.") }
        item { BarraRecorte(filtro,filtrar) }
        item { Segmentado(listOf("Turmas","Professores"),if(professores)1 else 0) { professores=it==1 } }
        item { TextButton(onClick=organizar) { Text("Organizar escola, série e turno") } }
        item { Text("— não verificado · * cobertura parcial · 0 só confirma em dia com cobertura completa. Conteúdos e frequência: dias; planos e PEI: documentos.",style=MaterialTheme.typography.bodySmall) }
        if(professores) item { Text("Docência compartilhada aparece para cada docente vinculado. Não some essas linhas como total da escola.",style=MaterialTheme.typography.bodySmall,color=corAlerta()) }
        item { Row(Modifier.horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(if(professores) "Professor(a)" else "Turma",Modifier.width(176.dp),style=MaterialTheme.typography.labelLarge)
            SgeDashboard.nomes.forEach { Text(it,Modifier.width(110.dp),style=MaterialTheme.typography.labelMedium) }
        } }
        items(grupos,key={it.chave}) { g ->
            Row(Modifier.horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.width(176.dp).clickable { abrir(g,-1) }.padding(vertical=10.dp)) { Text(g.titulo,style=MaterialTheme.typography.titleSmall); Text(g.subtitulo,style=MaterialTheme.typography.labelSmall,maxLines=3) }
                g.modulos.forEach { m -> val estado=when { (m.quantidade?:0)>0->EstadoGrupo.PENDENTE; !m.completa->EstadoGrupo.CONFERIR;else->EstadoGrupo.EM_DIA }
                    Surface(onClick={ abrir(g,m.indice) },modifier=Modifier.width(110.dp).heightIn(min=70.dp),color=corEstado(estado).copy(alpha=.09f),shape=MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(10.dp)) { Text(m.valor(),style=MaterialTheme.typography.titleLarge,color=corEstado(estado)); Text(if(m.quantidade==null) "A conferir" else m.unidade(),style=MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            HorizontalDivider()
        }
        if(grupos.isEmpty()) item { MensagemVazia("Sem resultados neste recorte","Consulte as turmas e atualize os vínculos docentes, ou altere os filtros.") }
    }
}

@Composable fun TelaOrganizacao(conhecidas: List<Pair<String,String>>, rs: List<ResultadoTurma>, org: Map<String,DadosTurma>, salvar: (Map<String,DadosTurma>)->Unit) {
    var editando by remember { mutableStateOf<Pair<String,String>?>(null) }
    val turmas=(conhecidas+rs.map { it.turma.id to it.turma.nome }).distinctBy { it.first }.sortedBy { it.second }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Organizar turmas",style=MaterialTheme.typography.headlineLarge); Text("Informações usadas apenas nos filtros deste app. Não alteram o SGE.") }
        items(turmas,key={it.first}) { (id,nome)-> val d=org[id]?:DadosTurma(escola=rs.firstOrNull {it.turma.id==id}?.turma?.escola.orEmpty())
            Surface(onClick={editando=id to nome},shape=MaterialTheme.shapes.medium) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(nome,style=MaterialTheme.typography.titleSmall);Text(listOf(d.escola,d.serie,d.turno).filter {it.isNotBlank()}.joinToString(" · ").ifBlank { "Toque para informar escola, série e turno" },style=MaterialTheme.typography.bodySmall) } }
        }
    }
    editando?.let { (id,nome)->
        var d by remember(id) { mutableStateOf(org[id]?:DadosTurma(escola=rs.firstOrNull {it.turma.id==id}?.turma?.escola.orEmpty())) }
        AlertDialog(onDismissRequest={ editando=null },title={Text(nome)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(d.escola,{d=d.copy(escola=it.take(200))},label={Text("Escola")},singleLine=true)
            OutlinedTextField(d.serie,{d=d.copy(serie=it.take(100))},label={Text("Série / etapa")},singleLine=true)
            OutlinedTextField(d.turno,{d=d.copy(turno=it.take(100))},label={Text("Turno")},singleLine=true)
        }},confirmButton={Button(onClick={salvar(org+(id to d));editando=null}){Text("Salvar")}},dismissButton={TextButton(onClick={editando=null}){Text("Cancelar")}})
    }
}

@Composable fun TelaLote(rs: List<ResultadoTurma>, docentes: PainelDocentes, filtro: FiltroAvancado, org: Map<String,DadosTurma>, filtrar: ()->Unit, gerar: (List<GrupoPendencias>,String,String,String)->Unit) {
    val filtrados=SgeOrganizacao.filtrar(rs,filtro,org,docentes)
    val grupos=SgeOrganizacao.porProfessor(filtrados,docentes,filtro.professor)
    var selecionados by remember(grupos.map { it.chave }) { mutableStateOf(grupos.map { it.chave }.toSet()) }
    var escola by remember { mutableStateOf(filtro.escola.ifBlank { filtrados.map { SgeOrganizacao.dados(it,org).escola }.distinct().singleOrNull().orEmpty() }) }
    var responsavel by remember { mutableStateOf("") }
    val semVinculo=filtrados.count { SgeOrganizacao.professores(it,docentes).isEmpty() }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Relatórios em lote",style=MaterialTheme.typography.displaySmall) }
        item { Text("Gera um PDF por professor, um PDF reunido para imprimir e folhas de recebimento e ciência com espaço para assinatura. Todos ficam em um ZIP.") }
        item { BarraRecorte(filtro,filtrar) }
        item { Text(descreverFiltro(filtro,docentes),style=MaterialTheme.typography.bodySmall) }
        if(semVinculo>0) item { Text("$semVinculo turma(s)/componente(s) sem professor identificado ficam fora do lote nominal. Ajuste os vínculos nos cartões.",color=corAlerta()) }
        item { OutlinedTextField(escola,{escola=it.take(200)},Modifier.fillMaxWidth(),label={Text("Escola no cabeçalho (opcional)")}) }
        item { OutlinedTextField(responsavel,{responsavel=it.take(200)},Modifier.fillMaxWidth(),label={Text("Quem entrega (opcional)")}) }
        item { Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(selecionados.size==grupos.size && grupos.isNotEmpty(),{ selecionados=if(it)grupos.map { g->g.chave }.toSet() else emptySet() });Text("Selecionar todos · ${selecionados.size}/${grupos.size}") } }
        items(grupos,key={it.chave}) { g-> Surface(shape=MaterialTheme.shapes.medium) { Row(Modifier.fillMaxWidth().clickable { selecionados=if(g.chave in selecionados)selecionados-g.chave else selecionados+g.chave }.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Checkbox(g.chave in selecionados,{selecionados=if(it)selecionados+g.chave else selecionados-g.chave});Column(Modifier.weight(1f)) { Text(g.titulo,style=MaterialTheme.typography.titleSmall);Text(g.subtitulo,style=MaterialTheme.typography.bodySmall);if(g.exigeConferencia)Text("Inclui itens a conferir",style=MaterialTheme.typography.labelSmall,color=corAlerta()) }
        } } }
        item { Text("As folhas registram recebimento e ciência. As contagens são da última consulta, e itens parciais continuam sinalizados. Docência compartilhada não atribui autoria individual.",style=MaterialTheme.typography.bodySmall) }
        item { Button(onClick={gerar(grupos.filter {it.chave in selecionados},escola.trim(),responsavel.trim(),descreverFiltro(filtro,docentes))},enabled=selecionados.isNotEmpty(),modifier=Modifier.fillMaxWidth()) { Text("Gerar ${selecionados.size} relatório(s) e recibos") } }
    }
}

@Composable fun TelaEconomia(atual: EconomiaSge, salvar: (EconomiaSge)->Unit) {
    var d by remember(atual) { mutableStateOf(atual) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("Sincronização econômica",style=MaterialTheme.typography.headlineLarge)
        Text("Estas regras valem para consultas automáticas, inclusive a fila noturna. Atualizar manualmente usa a internet disponível. O Android pode adiar os horários para economizar bateria.")
        @Composable fun opcao(t: String,v: Boolean,alterar:(Boolean)->Unit) { Row(verticalAlignment=Alignment.CenterVertically) {Text(t,Modifier.weight(1f));Switch(v,alterar)} }
        opcao("Somente rede sem franquia (Wi-Fi)",d.wifi){d=d.copy(wifi=it)}
        opcao("Somente enquanto carrega",d.carregando){d=d.copy(carregando=it)}
        opcao("Aguardar bateria suficiente",d.bateria){d=d.copy(bateria=it)}
        EscolhaFiltro("Intervalo do diário pessoal",d.horas.toString(),listOf(2,6,12,24).map {it.toString() to "A cada $it horas"}){d=d.copy(horas=it.toInt())}
        EscolhaFiltro("Verificação noturna da escola",d.horaNoturna.toString(),(0..23).map {it.toString() to "%02d:00".format(it)}){d=d.copy(horaNoturna=it.toInt())}
        Text("A consulta noturna precisa estar ligada nos Ajustes e exige uma sessão Unidade escolar. A fila consulta uma turma por vez.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={salvar(d)}) { Text("Salvar preferências") }
    }
}
@Composable fun TelaBackup(criar:()->Unit,restaurar:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("Backup e restauração",style=MaterialTheme.typography.headlineLarge)
        Text("Guarde os painéis, histórico, vínculos de professores, organização das turmas e preferências em um arquivo. Você escolhe a pasta e pode usar o Google Drive se ele estiver disponível no seletor do Android.")
        Text("O arquivo contém dados da escola: guarde-o em um local de sua confiança. Não contém senha, cookies, sessão do SGE nem autorização biométrica. A proteção deste aparelho é mantida na restauração.")
        Button(onClick=criar,modifier=Modifier.fillMaxWidth()) { Text("Criar backup") }
        OutlinedButton(onClick=restaurar,modifier=Modifier.fillMaxWidth()) { Text("Restaurar de um arquivo") }
        Text("Antes de restaurar, o app valida o arquivo e mostra um resumo para você confirmar a substituição dos dados locais. Depois, entre no SGE para atualizar as consultas.",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Sobre entrar com Google",style=MaterialTheme.typography.titleMedium)
        Text("Uma conta Google não substitui o acesso ao SGE. Para esta versão, o login continua no SGE Web. O seletor de arquivos já permite guardar o backup em provedores instalados, sem criar outra conta no aplicativo.",style=MaterialTheme.typography.bodyMedium)
    }
}

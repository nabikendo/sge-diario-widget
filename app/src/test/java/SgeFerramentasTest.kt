package br.to.seduc.sgediariowidget.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import br.to.seduc.sgediariowidget.PoliticaAtualizacao

class SgeFerramentasTest {
    private fun r(id: String="1", m: ResultadoModulo=ResultadoModulo(0,coberturaCompleta=true)) = ResultadoTurma(TurmaMonitorada(id,"$id/EM","Escola A","Química"),listOf(m)+List(4){ResultadoModulo(0,coberturaCompleta=true)},"01/11 10:00")
    private val ana=DocenteRef("a","Ana"); private val bia=DocenteRef("b","Bia")
    @Test fun filtrosCombinamSerieTurnoProfessorModuloEEstado() {
        val d=PainelDocentes(vinculos=listOf(VinculoDocente("1","Química",ana)))
        val org=mapOf("1" to DadosTurma("Escola A","1º ano","Manhã"))
        val f=FiltroAvancado(escola="escola a",serie="1º ano",turno="MANHA",professor="a",modulo=0,estado=1)
        assertEquals(listOf("1"),SgeOrganizacao.filtrar(listOf(r(m=ResultadoModulo(2)),r("2")),f,org,d).map {it.turma.id})
        assertTrue(SgeOrganizacao.filtrar(listOf(r()),f,org,d).isEmpty())
    }
    @Test fun zeroParcialNaoPassaFiltroEmDia() { assertTrue(SgeOrganizacao.filtrar(listOf(r(m=ResultadoModulo(0))),FiltroAvancado(modulo=0,estado=3),emptyMap(),PainelDocentes()).isEmpty()) }
    @Test fun naoDeduzTurnoNemSerieDoCodigo() { assertEquals(DadosTurma("Escola A"),SgeOrganizacao.dados(r("13.01"),emptyMap())) }
    @Test fun loteIndividualPreservaDocenciaCompartilhadaSemMesclarHomonomos() {
        val d=PainelDocentes(vinculos=listOf(VinculoDocente("1","Química",ana),VinculoDocente("1","Química",bia.copy(nome="Ana"))))
        val gs=SgeOrganizacao.porProfessor(listOf(r(),r()),d)
        assertEquals(2,gs.size); assertTrue(gs.all {it.compartilhado && it.resultados.size==1}); assertEquals(2,gs.map {it.chave}.distinct().size)
        assertEquals(1,SgeOrganizacao.porProfessor(listOf(r()),d,"a").size)
    }
    @Test fun loteNaoAtribuiProfessorDesconhecido() { assertTrue(SgeOrganizacao.porProfessor(listOf(r()),PainelDocentes()).isEmpty()) }
    @Test fun organizacaoRoundTrip() { val org=mapOf("1" to DadosTurma("A","2º ano","Tarde"));assertEquals(org,SgeOrganizacao.ler(SgeOrganizacao.json(org))) }
    @Test fun backupExcluiCredenciaisEAutorizacoes() {
        val texto=SgeBackup.criar(mapOf("tema" to 2,"coord_biometria" to true,"sge_cookies" to "segredo","web_user_agent" to "agente","diagnostico_v1" to "privado"),"1.8")
        assertFalse(texto.contains("segredo"));assertFalse(texto.contains("coord_biometria"));assertFalse(texto.contains("web_user_agent"));assertFalse(texto.contains("privado"));assertEquals(2,SgeBackup.validar(texto).dados.getInt("tema"))
    }
    @Test fun backupResetaConsultasEmAndamento() {
        val painel=JSONObject().put("turmas",org.json.JSONArray().put(r().json())).put("sincronizando",true).put("sessaoAtiva",true).toString()
        val b=SgeBackup.validar(SgeBackup.criar(mapOf("painel_v2" to painel),"1.8"));val j=JSONObject(b.dados.getString("painel_v2"))
        assertEquals(1,b.resultados);assertFalse(j.getBoolean("sincronizando"));assertFalse(j.getBoolean("sessaoAtiva"))
    }
    @Test fun backupRejeitaCamposInjetadosAntesDeGravar() {
        val j=JSONObject(SgeBackup.criar(emptyMap<String,String>(),"1.8"));j.getJSONObject("dados").put("coord_biometria",false)
        assertTrue(runCatching {SgeBackup.validar(j.toString())}.isFailure)
    }
    @Test fun backupRejeitaSchemaNovoJsonTruncadoENegativos() {
        val j=JSONObject(SgeBackup.criar(emptyMap<String,String>(),"1.8")).put("schema",2)
        assertTrue(runCatching{SgeBackup.validar(j.toString())}.isFailure)
        assertTrue(runCatching{SgeBackup.validar("{quebrado")}.isFailure)
        val p=JSONObject().put("turmas",org.json.JSONArray().put(r(m=ResultadoModulo(-1)).json())).toString()
        assertTrue(runCatching{SgeBackup.validar(SgeBackup.criar(mapOf("painel_v2" to p),"1.8"))}.isFailure)
    }
    @Test fun backupValidaListaRealDoWebView() {
        assertNotNull(SgeBackup.validar(SgeBackup.criar(mapOf("turmas_escola" to "[{\"v\":\"1\",\"t\":\"13.01\",\"tp\":\"0\"}]"),"1.8")))
    }
    private fun meta()=JSONObject().put("schema",1).put("pacote","app.teste").put("versao","1.8").put("codigo",20).put("minSdk",29).put("arquivo","SGE.apk").put("tamanho",1024).put("sha256","a".repeat(64))
    private val url="https://github.com/nabikendo/sge-diario-widget/releases/download/v1.8/SGE.apk"
    @Test fun atualizacaoAceitaSomenteNovaVersaoDoMesmoCanal() { assertEquals(20L,PoliticaAtualizacao.ler(meta(),"app.teste",19,29,url).codigo);assertTrue(runCatching{PoliticaAtualizacao.ler(meta(),"app",19,29,url)}.isFailure);assertTrue(runCatching{PoliticaAtualizacao.ler(meta(),"app.teste",20,29,url)}.isFailure) }
    @Test fun atualizacaoRejeitaHashOrigemMinSdkETamanhoInvalidos() {
        listOf(meta().put("sha256","abc"),meta().put("minSdk",35),meta().put("tamanho",0)).forEach { assertTrue(runCatching{PoliticaAtualizacao.ler(it,"app.teste",19,29,url)}.isFailure) }
        assertFalse(PoliticaAtualizacao.urlAsset(url.replace("github.com","github.com.evil.com")))
        assertFalse(PoliticaAtualizacao.urlAsset(url.replace("https:","http:")))
        assertTrue(runCatching{PoliticaAtualizacao.ler(meta(),"app.teste",19,29,url.replace("SGE.apk","outro.apk"))}.isFailure)
    }
    @Test fun assinaturaRejeitaTrocaNaoAutorizadaEAceitaRotacaoDeclarada() {
        assertTrue(PoliticaAtualizacao.assinaturaPermitida(setOf("a"),setOf("a"),emptySet()))
        assertTrue(PoliticaAtualizacao.assinaturaPermitida(setOf("a"),setOf("b"),setOf("a","b")))
        assertFalse(PoliticaAtualizacao.assinaturaPermitida(setOf("a"),setOf("b"),setOf("b")))
        assertFalse(PoliticaAtualizacao.assinaturaPermitida(emptySet(),emptySet(),emptySet()))
    }
}

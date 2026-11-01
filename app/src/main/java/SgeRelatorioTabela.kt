package br.to.seduc.sgediariowidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.to.seduc.sgediariowidget.data.*
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Relatório por professor em formato de tabela (A4): resumo em cartões, prioridades, tabela por turma/componente
 * com as datas em aberto, observações e recibo de ciência. Não leva nomes de alunos nem credenciais.
 */
object SgeRelatorioTabela {
    private const val L = 36f
    private const val W = 523f
    private const val LIMITE = 782f
    private val AZUL = Color.rgb(39, 103, 217)
    private val TEXTO = Color.rgb(35, 39, 47)
    private val CINZA = Color.rgb(105, 111, 123)
    private val VERMELHO = Color.rgb(200, 45, 45)
    private val AMBAR = Color.rgb(176, 112, 0)
    private val VERDE = Color.rgb(24, 128, 72)

    private class Celula(val estado: Int, val linhas: List<String>, val dias: Int = -1) // 0 nenhuma, 1 pendente, 2 não verificado, 3 parcial
    private class Linha(val turma: String, val turno: String, val comp: String, val cont: Celula, val freq: Celula,
                        val docs: List<String>, val nivel: Int, val peso: Int, val notas: List<String>)
    private class Prioridade(val dias: Int, val texto: String)

    fun escrever(saida: OutputStream, grupos: List<GrupoPendencias>, fonte: FontePainel, agora: LocalDateTime,
                 comRecibo: Boolean, escola: String, responsavel: String, recorte: String) {
        require(grupos.isNotEmpty())
        val contador = PdfDocument()
        val total = try { desenhar(contador, grupos, fonte, agora, comRecibo, escola, responsavel, recorte, 0) } finally { contador.close() }
        val pdf = PdfDocument()
        try { desenhar(pdf, grupos, fonte, agora, comRecibo, escola, responsavel, recorte, total); pdf.writeTo(saida) } finally { pdf.close() }
    }

    private fun turmaCurta(nome: String) = nome.substringBefore('/').trim()
    private fun turno(nome: String): String {
        val c = nome.substringAfter('/', "").uppercase()
        return when { ".VESP" in c -> "Vespertino"; ".MAT" in c -> "Matutino"; ".NOT" in c -> "Noturno"; ".INT" in c -> "Integral"; else -> "" }
    }
    private fun datas(detalhe: String): List<String> = Regex("\\d{2}/\\d{2}").findAll(detalhe.substringAfter(':', "")).map { it.value }.distinct().toList()
    private fun diasDesde(dm: String, hoje: LocalDate): Int {
        val d = dm.substringBefore('/').toIntOrNull() ?: return -1; val m = dm.substringAfter('/').toIntOrNull() ?: return -1
        return runCatching {
            var data = LocalDate.of(hoje.year, m, d); if (data.isAfter(hoje)) data = data.minusYears(1)
            ChronoUnit.DAYS.between(data, hoje).toInt()
        }.getOrDefault(-1)
    }

    private fun celula(m: ResultadoModulo?, hoje: LocalDate): Celula {
        if (m == null || m.quantidade == null) return Celula(2, listOf("N/V"))
        if (m.quantidade == 0) return Celula(if (m.coberturaCompleta) 0 else 3, listOf(if (m.coberturaCompleta) "—" else "0*"))
        val ds = datas(m.detalhe)
        if (ds.isEmpty()) return Celula(1, listOf("${m.quantidade} ${if (m.quantidade == 1) "dia" else "dias"}"))
        val maisAntiga = ds.maxOf { diasDesde(it, hoje) }
        val linhas = mutableListOf(ds.joinToString(", ") + if (ds.size < m.quantidade) "…" else "")
        if (maisAntiga >= 7) linhas.add("mais antiga: há $maisAntiga dias")
        return Celula(1, linhas, maisAntiga)
    }

    private fun montar(r: ResultadoTurma, hoje: LocalDate): Linha {
        val ms = (0..4).map { r.modulos.getOrNull(it) }
        val cont = celula(ms[0], hoje); val freq = celula(ms[1], hoje)
        val docs = mutableListOf<String>()
        listOf(Triple(2, "Plano de Aula", "Planos de Aula"), Triple(4, "PEI", "PEIs"), Triple(3, "Plano de Curso", "Planos de Curso")).forEach { (i, sing, plur) ->
            val m = ms[i]; val q = m?.quantidade
            when {
                m == null || q == null -> docs.add("$sing: N/V")
                q > 0 -> docs.add("$q ${if (q == 1) sing else plur}")
                !m.coberturaCompleta -> docs.add(if (i == 4 && m.detalhe.contains("periodicidade", true)) "PEI: definir periodicidade*" else "$sing: conferir*")
            }
        }
        val qs = ms.map { it?.quantidade }
        val nivel = if (qs.any { (it ?: 0) > 0 }) 2 else if (qs.any { it == null } || ms.any { it != null && !it.coberturaCompleta }) 1 else 0
        val notas = ms.withIndex().mapNotNull { (i, m) ->
            if (m != null && m.quantidade == null) "${turmaCurta(r.turma.nome)} ${r.turma.disciplina}: ${SgeDashboard.nomes[i]} não verificado (${m.detalhe.substringAfter(':', m.detalhe).trim().take(90)})."
            else null
        }
        return Linha(turmaCurta(r.turma.nome), turno(r.turma.nome), r.turma.disciplina, cont, freq, docs, nivel, qs.sumOf { it ?: 0 }, notas)
    }

    private fun desenhar(pdf: PdfDocument, grupos: List<GrupoPendencias>, fonte: FontePainel, agora: LocalDateTime,
                         comRecibo: Boolean, escola: String, responsavel: String, recorte: String, total: Int): Int {
        val hoje = agora.toLocalDate()
        val tinta = Paint(Paint.ANTI_ALIAS_FLAG)
        var numero = 0; var y = 0f; var pagina: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var titulo = ""

        fun estilo(tamanho: Float, negrito: Boolean = false, cor: Int = TEXTO) {
            tinta.textSize = tamanho; tinta.color = cor; tinta.style = Paint.Style.FILL
            tinta.typeface = Typeface.create("sans-serif", if (negrito) Typeface.BOLD else Typeface.NORMAL)
        }
        fun cortar(t: String, largura: Float): String { val n = tinta.breakText(t, true, largura, null); return if (n < t.length) t.take(n - 1) + "…" else t }
        fun quebrar(t: String, largura: Float): List<String> {
            val saida = mutableListOf<String>(); var atual = ""
            for (p in t.replace('\n', ' ').split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val cand = if (atual.isEmpty()) p else "$atual $p"
                if (tinta.measureText(cand) <= largura) atual = cand
                else {
                    if (atual.isNotEmpty()) saida.add(atual)
                    var resto = p
                    while (tinta.measureText(resto) > largura) { val n = tinta.breakText(resto, true, largura, null).coerceAtLeast(1); saida.add(resto.take(n)); resto = resto.drop(n) }
                    atual = resto
                }
            }
            if (atual.isNotEmpty()) saida.add(atual)
            return saida
        }
        fun rodape() {
            val p = pagina ?: return
            estilo(8.5f, cor = CINZA)
            p.canvas.drawText(cortar("SGE Diário — somente leitura · $titulo", 400f), L, 812f, tinta)
            tinta.textAlign = Paint.Align.RIGHT
            p.canvas.drawText("Página $numero${if (total > 0) " de $total" else ""}", L + W, 812f, tinta)
            tinta.textAlign = Paint.Align.LEFT
            pdf.finishPage(p); pagina = null
        }
        fun nova() {
            rodape(); numero++
            pagina = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, numero).create()); canvas = pagina!!.canvas
            estilo(12f, true, AZUL)
            val perfilTxt = if (fonte == FontePainel.COORDENACAO) "COORDENAÇÃO" else "MEU DIÁRIO"
            canvas.drawText("SGE DIÁRIO  /  $perfilTxt", L, 40f, tinta)
            tinta.color = Color.rgb(222, 226, 234); canvas.drawRect(L, 47f, L + W, 48f, tinta)
            y = 70f
        }
        fun garantir(altura: Float) { if (y + altura > LIMITE) nova() }
        fun caixa(x: Float, topo: Float, w: Float, h: Float, preenchimento: Int, borda: Int, raio: Float = 7f) {
            tinta.style = Paint.Style.FILL; tinta.color = preenchimento; canvas.drawRoundRect(RectF(x, topo, x + w, topo + h), raio, raio, tinta)
            tinta.style = Paint.Style.STROKE; tinta.strokeWidth = 0.8f; tinta.color = borda; canvas.drawRoundRect(RectF(x, topo, x + w, topo + h), raio, raio, tinta)
            tinta.style = Paint.Style.FILL
        }
        fun secao(t: String) { garantir(30f); y += 6f; estilo(13f, true); canvas.drawText(t, L, y, tinta); y += 14f }

        try {
            for (grupo in grupos) {
                titulo = grupo.titulo
                nova()
                estilo(20f, true); canvas.drawText(cortar(grupo.titulo, W), L, y + 6f, tinta); y += 26f
                estilo(11f, cor = CINZA)
                val atualizado = agora.format(DateTimeFormatter.ofPattern("dd/MM/uuuu 'às' HH:mm"))
                canvas.drawText(cortar("${grupo.subtitulo} | Atualizado em $atualizado", W), L, y, tinta); y += 14f
                estilo(9f, cor = CINZA)
                if (escola.isNotBlank()) { canvas.drawText(cortar("Escola: $escola", W), L, y, tinta); y += 12f }
                if (recorte.isNotBlank()) { canvas.drawText(cortar("Recorte: $recorte", W), L, y, tinta); y += 12f }
                if (grupo.compartilhado) { canvas.drawText(cortar("Docência compartilhada: cada turma/componente aparece uma vez; os registros não indicam autoria individual.", W), L, y, tinta); y += 12f }
                y += 4f

                // 1. Resumo geral
                secao("1. Resumo Geral")
                val ordem = listOf(0, 1, 2, 4, 3); val larg = (W - 4 * 8f) / 5f; val altura = 66f
                garantir(altura + 8f)
                val modulos = grupo.modulos
                ordem.forEachIndexed { pos, idx ->
                    val m = modulos.getOrNull(idx) ?: return@forEachIndexed
                    val x = L + pos * (larg + 8f)
                    val (fundo, borda, corNum) = when {
                        m.quantidade == null -> Triple(Color.rgb(243, 244, 246), Color.rgb(205, 209, 216), CINZA)
                        m.quantidade > 0 -> Triple(Color.rgb(253, 236, 236), Color.rgb(233, 170, 170), VERMELHO)
                        !m.completa -> Triple(Color.rgb(255, 245, 224), Color.rgb(236, 205, 140), AMBAR)
                        else -> Triple(Color.rgb(232, 245, 237), Color.rgb(160, 210, 180), VERDE)
                    }
                    caixa(x, y, larg, altura, fundo, borda)
                    estilo(7.5f, true, CINZA); canvas.drawText(cortar(SgeDashboard.nomes[idx].uppercase(), larg - 10f), x + 6f, y + 13f, tinta)
                    estilo(20f, true, corNum); canvas.drawText(m.valor(), x + 6f, y + 36f, tinta)
                    estilo(8f, cor = TEXTO)
                    canvas.drawText(if (idx < 2) "dias pendentes" else "a enviar", x + 6f, y + 47f, tinta)
                    if (idx >= 2) {
                        estilo(7.5f, cor = CINZA)
                        canvas.drawText(cortar(if (idx == 3) "${m.aguardando} em aval. · ${m.aprovados} aprov." else "${m.aguardando} em avaliação", larg - 8f), x + 6f, y + 58f, tinta)
                    }
                }
                y += altura + 6f

                val unicos = grupo.resultados.distinctBy(::chaveResultado)
                val linhas = unicos.map { montar(it, hoje) }
                    .sortedWith(compareByDescending<Linha> { it.nivel }.thenByDescending { it.peso }.thenBy { it.turma }.thenBy { it.comp })

                // Prioridades
                val prioridades = mutableListOf<Prioridade>()
                linhas.forEach { l ->
                    val nome = "${l.comp} ${l.turma}"
                    if (l.cont.estado == 1) prioridades.add(Prioridade(l.cont.dias, "Conteúdo ${l.cont.linhas.first().removeSuffix("…")} · $nome"))
                    if (l.freq.estado == 1) prioridades.add(Prioridade(l.freq.dias, "Frequência ${l.freq.linhas.first().removeSuffix("…")} · $nome"))
                    l.docs.filter { it.first().isDigit() }.forEach { prioridades.add(Prioridade(-1, "$it · $nome")) }
                }
                val topo = prioridades.sortedByDescending { it.dias }.take(4)
                if (topo.isNotEmpty()) {
                    secao("Prioridades sugeridas")
                    estilo(9.5f)
                    topo.forEachIndexed { i, p ->
                        garantir(14f)
                        val idade = if (p.dias >= 7) "  (há ${p.dias} dias)" else ""
                        canvas.drawText(cortar("${i + 1}. ${p.texto}$idade", W), L + 4f, y + 2f, tinta); y += 13f
                    }
                }

                // 2. Tabela
                secao("2. Pendências por Turma")
                estilo(8f, cor = CINZA)
                canvas.drawText("Ordenado por urgência.  — nenhuma pendência   N/V não verificado   * cobertura parcial: o valor conhecido pode aumentar.", L, y, tinta); y += 10f
                if (linhas.isEmpty()) { estilo(10f); canvas.drawText("Sem consultas para este recorte.", L, y + 12f, tinta); y += 24f }
                val cx = floatArrayOf(L, L + 118f, L + 216f, L + 366f); val cw = floatArrayOf(118f, 98f, 150f, 157f)
                fun cabecalho() {
                    garantir(34f)
                    tinta.style = Paint.Style.FILL; tinta.color = Color.rgb(236, 240, 247); canvas.drawRect(L, y, L + W, y + 28f, tinta)
                    val titulos = listOf("Turma / Componente" to "", "Conteúdos" to "(datas em aberto)", "Frequência" to "(datas em aberto)", "Documentos" to "(pendentes de envio)")
                    titulos.forEachIndexed { i, (a, b) ->
                        estilo(9f, true); canvas.drawText(a, cx[i] + 7f, y + 12f, tinta)
                        if (b.isNotEmpty()) { estilo(7.5f, cor = CINZA); canvas.drawText(b, cx[i] + 7f, y + 22f, tinta) }
                    }
                    y += 28f
                }
                if (linhas.isNotEmpty()) cabecalho()
                linhas.forEachIndexed { n, l ->
                    estilo(8.8f)
                    val c1 = listOf(l.turma + if (l.turno.isNotEmpty()) " · ${l.turno}" else "") + quebrar(l.comp, cw[0] - 12f)
                    val c2 = l.cont.linhas.flatMap { quebrar(it, cw[1] - 12f) }
                    val c3 = l.freq.linhas.flatMap { quebrar(it, cw[2] - 12f) }
                    val c4 = (if (l.docs.isEmpty()) listOf("—") else l.docs).flatMap { quebrar(it, cw[3] - 12f) }
                    val h = maxOf(c1.size, c2.size, c3.size, c4.size) * 11.2f + 10f
                    if (y + h > LIMITE) { nova(); cabecalho() }
                    if (n % 2 == 1) { tinta.style = Paint.Style.FILL; tinta.color = Color.rgb(249, 250, 252); canvas.drawRect(L, y, L + W, y + h, tinta) }
                    tinta.style = Paint.Style.FILL
                    tinta.color = when (l.nivel) { 2 -> VERMELHO; 1 -> AMBAR; else -> VERDE }; canvas.drawRect(L, y, L + 3f, y + h, tinta)
                    tinta.color = Color.rgb(226, 230, 237); canvas.drawRect(L, y + h - 0.6f, L + W, y + h, tinta)
                    fun coluna(i: Int, ls: List<String>, cor: (Int) -> Int, negritoPrimeira: Boolean = false) {
                        ls.forEachIndexed { k, t ->
                            estilo(if (negritoPrimeira && k == 0) 9.2f else 8.8f, negritoPrimeira && k == 0, cor(k))
                            canvas.drawText(t, cx[i] + 7f, y + 13f + k * 11.2f, tinta)
                        }
                    }
                    coluna(0, c1, { if (it == 0) TEXTO else CINZA }, true)
                    fun corCelula(c: Celula): (Int) -> Int = { k -> when { c.estado == 2 -> AMBAR; c.estado == 3 -> AMBAR; c.estado == 0 -> CINZA; k > 0 -> CINZA; else -> TEXTO } }
                    coluna(1, c2, corCelula(l.cont)); coluna(2, c3, corCelula(l.freq))
                    coluna(3, c4, { k -> if (c4.getOrNull(k)?.contains("N/V") == true || c4.getOrNull(k)?.endsWith("*") == true) AMBAR else if (l.docs.isEmpty()) CINZA else TEXTO })
                    y += h
                }
                y += 8f

                // Observações
                val notas = linhas.flatMap { it.notas }.distinct()
                if (notas.isNotEmpty()) {
                    secao("Observações")
                    estilo(8.8f, cor = CINZA)
                    notas.take(6).forEach { n -> quebrar(n, W - 6f).forEach { garantir(12f); canvas.drawText(it, L + 3f, y + 2f, tinta); y += 11f } }
                    if (notas.size > 6) { garantir(12f); canvas.drawText("… e mais ${notas.size - 6} observação(ões); veja o diagnóstico no app.", L + 3f, y + 2f, tinta); y += 11f }
                    y += 4f
                }
                estilo(8.5f, cor = CINZA)
                for (t in quebrar("Os dados refletem a última consulta de cada turma e componente. Aguardando avaliação não conta como pendência de envio. Lançamentos e correções são feitos pelo professor no próprio SGE.", W)) { garantir(11f); canvas.drawText(t, L, y + 2f, tinta); y += 10f }

                // Recibo
                if (comRecibo) {
                    garantir(150f)
                    y += 14f
                    tinta.color = Color.rgb(200, 205, 214); canvas.drawRect(L, y, L + W, y + 0.8f, tinta); y += 20f
                    estilo(10.5f, true); canvas.drawText("RECEBIMENTO E CIÊNCIA", L, y, tinta); y += 16f
                    estilo(10f)
                    for (t in quebrar("Declaro ter recebido este relatório e estar ciente das pendências registradas no SGE Diário.", W)) { canvas.drawText(t, L, y, tinta); y += 13f }
                    y += 14f
                    canvas.drawText("Data de recebimento: ____/____/2026        Prazo combinado para regularização: ____/____/2026", L, y, tinta); y += 34f
                    tinta.color = TEXTO; canvas.drawRect(L, y, L + 330f, y + 0.8f, tinta); y += 12f
                    estilo(9f, cor = CINZA); canvas.drawText(cortar("Assinatura de ciência — ${grupo.titulo}", 400f), L, y, tinta); y += 14f
                    if (responsavel.isNotBlank()) { canvas.drawText(cortar("Entregue por: $responsavel", W), L, y, tinta); y += 12f }
                }
            }
        } finally { rodape() }
        return numero
    }
}

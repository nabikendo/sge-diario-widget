package br.to.seduc.sgediariowidget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ícones de traço com o mesmo peso em toda a navegação. */
object SgeIcons {
    private fun line(name: String, draw: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).apply { path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathFillType = PathFillType.NonZero, pathBuilder = draw) }.build()
    val Cards = line("Cartões") {
        moveTo(4f, 3f); lineTo(9f, 3f); lineTo(9f, 10f); lineTo(3f, 10f); lineTo(3f, 4f); close()
        moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 8f); lineTo(14f, 8f); lineTo(14f, 4f); close()
        moveTo(3f, 15f); lineTo(9f, 15f); lineTo(9f, 21f); lineTo(3f, 21f); close()
        moveTo(14f, 13f); lineTo(21f, 13f); lineTo(21f, 21f); lineTo(14f, 21f); close()
    }
    val People = line("Professores") {
        moveTo(8f, 3f); curveTo(4f,3f,4f,10f,8f,10f); curveTo(12f,10f,12f,3f,8f,3f)
        moveTo(2f,21f); lineTo(2f,17f); curveTo(2f,12f,14f,12f,14f,17f); lineTo(14f,21f)
        moveTo(16f,4f); curveTo(21f,4f,21f,11f,16f,11f)
        moveTo(17f,14f); curveTo(22f,14f,22f,17f,22f,20f)
    }
    val Globe = line("SGE Web") {
        moveTo(12f,2f); curveTo(25f,2f,25f,22f,12f,22f); curveTo(-1f,22f,-1f,2f,12f,2f)
        moveTo(2f,12f); lineTo(22f,12f)
        moveTo(12f,2f); curveTo(6f,7f,6f,17f,12f,22f); curveTo(18f,17f,18f,7f,12f,2f)
    }
    val Settings = line("Ajustes") {
        moveTo(4f,6f); lineTo(20f,6f); moveTo(4f,18f); lineTo(20f,18f)
        moveTo(8f,3f); lineTo(8f,9f); moveTo(16f,15f); lineTo(16f,21f)
    }
    val Info = line("Ajuda") {
        moveTo(12f,2f); curveTo(25f,2f,25f,22f,12f,22f); curveTo(-1f,22f,-1f,2f,12f,2f)
        moveTo(12f,11f); lineTo(12f,17f); moveTo(12f,7f); lineTo(12f,7.1f)
    }
    val Back = line("Voltar") { moveTo(14f,5f); lineTo(7f,12f); lineTo(14f,19f) }
    val Chevron = line("Abrir") { moveTo(9f,5f); lineTo(16f,12f); lineTo(9f,19f) }
    val Close = line("Fechar") { moveTo(6f,6f); lineTo(18f,18f); moveTo(18f,6f); lineTo(6f,18f) }
    val Refresh = line("Atualizar") {
        moveTo(20f,7f); curveTo(16f,0f,5f,2f,3f,10f); moveTo(20f,2f); lineTo(20f,7f); lineTo(15f,7f)
        moveTo(4f,17f); curveTo(8f,24f,19f,22f,21f,14f); moveTo(4f,22f); lineTo(4f,17f); lineTo(9f,17f)
    }
    val Check = line("Em dia") { moveTo(5f,12f); lineTo(10f,17f); lineTo(20f,6f) }
    val Alert = line("A conferir") {
        moveTo(12f,3f); lineTo(22f,21f); lineTo(2f,21f); close()
        moveTo(12f,9f); lineTo(12f,14f); moveTo(12f,17f); lineTo(12f,17.1f)
    }
    val Search = line("Buscar") {
        moveTo(10f,3f); curveTo(19f,3f,19f,17f,10f,17f); curveTo(1f,17f,1f,3f,10f,3f)
        moveTo(15f,15f); lineTo(21f,21f)
    }
    val Book = line("Conteúdos") {
        moveTo(12f,5f); curveTo(9f,2f,4f,2f,2f,4f); lineTo(2f,20f); curveTo(6f,18f,9f,18f,12f,21f)
        lineTo(12f,5f); curveTo(15f,2f,20f,2f,22f,4f); lineTo(22f,20f); curveTo(18f,18f,15f,18f,12f,21f)
    }
    val Calendar = line("Frequência") {
        moveTo(3f,5f); lineTo(21f,5f); lineTo(21f,21f); lineTo(3f,21f); close()
        moveTo(7f,2f); lineTo(7f,8f); moveTo(17f,2f); lineTo(17f,8f)
        moveTo(3f,11f); lineTo(21f,11f); moveTo(7f,15f); lineTo(10f,15f); moveTo(14f,15f); lineTo(17f,15f)
    }
    val Document = line("Planejamento") {
        moveTo(5f,2f); lineTo(15f,2f); lineTo(21f,8f); lineTo(21f,22f); lineTo(5f,22f); close()
        moveTo(15f,2f); lineTo(15f,8f); lineTo(21f,8f)
        moveTo(9f,13f); lineTo(17f,13f); moveTo(9f,17f); lineTo(15f,17f)
    }
    val User = line("Professor") {
        moveTo(12f,3f); curveTo(6f,3f,6f,11f,12f,11f); curveTo(18f,11f,18f,3f,12f,3f)
        moveTo(4f,22f); lineTo(4f,18f); curveTo(4f,12f,20f,12f,20f,18f); lineTo(20f,22f)
    }
    val Link = line("Vínculo") {
        moveTo(9f,15f); lineTo(15f,9f)
        moveTo(7f,12f); curveTo(1f,16f,7f,24f,12f,17f)
        moveTo(12f,7f); curveTo(16f,1f,24f,7f,17f,12f)
    }
    val Clock = line("Última consulta") {
        moveTo(12f,2f); curveTo(25f,2f,25f,22f,12f,22f); curveTo(-1f,22f,-1f,2f,12f,2f)
        moveTo(12f,6f); lineTo(12f,12f); lineTo(16f,15f)
    }
    val Plus = line("Adicionar") { moveTo(12f,4f); lineTo(12f,20f); moveTo(4f,12f); lineTo(20f,12f) }
    val Export = line("Compartilhar") {
        moveTo(12f,15f); lineTo(12f,2f); moveTo(7f,7f); lineTo(12f,2f); lineTo(17f,7f)
        moveTo(4f,11f); lineTo(4f,21f); lineTo(20f,21f); lineTo(20f,11f)
    }
}

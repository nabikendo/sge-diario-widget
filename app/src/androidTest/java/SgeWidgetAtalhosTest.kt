package br.to.seduc.sgediariowidget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.to.seduc.sgediariowidget.data.PainelSge
import br.to.seduc.sgediariowidget.data.PerfilSge
import br.to.seduc.sgediariowidget.data.ResultadoModulo
import br.to.seduc.sgediariowidget.data.ResultadoTurma
import br.to.seduc.sgediariowidget.data.SgePainelStore
import br.to.seduc.sgediariowidget.data.SgeSessaoStore
import br.to.seduc.sgediariowidget.data.TurmaMonitorada
import br.to.seduc.sgediariowidget.widget.ResumoGlanceWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalGlanceApi::class)
class SgeWidgetAtalhosTest {
    @Test fun resumoMantemDadosParciaisComSessaoExpirada() {
        val instrumento = InstrumentationRegistry.getInstrumentation()
        val contexto = instrumento.targetContext
        val prefs = contexto.getSharedPreferences(SgePainelStore.PREFS_NAME, 0)
        val anterior = prefs.getString("painel_v2", null)
        val perfil = prefs.getString("perfil_observado", null)
        val observado = prefs.getLong("perfil_observado_em", 0L)
        try {
            val resultado = ResultadoTurma(TurmaMonitorada("1", "13.01", "Escola", "Física"),
                listOf(ResultadoModulo(2), ResultadoModulo(), ResultadoModulo(), ResultadoModulo(), ResultadoModulo()),
                "08/11 10:30")
            SgePainelStore.salvar(contexto, PainelSge(turmas = listOf(resultado)))
            SgeSessaoStore.salvar(contexto, PerfilSge.EXPIRADO)
            val widget = runBlocking { ResumoGlanceWidget().compose(contexto, size = DpSize(220.dp, 170.dp)) }

            instrumento.runOnMainSync {
                val textos = textos(widget.apply(contexto, FrameLayout(contexto)))
                assertTrue(textos.any { it.contains("Sessão expirada") && it.contains("Cobertura parcial") && it.contains("08/11 10:30") })
                assertTrue(textos.containsAll(listOf("Cont.", "Freq.", "PEI", "turmas pendentes")))
            }
        } finally {
            val editor = prefs.edit().putString("painel_v2", anterior).putString("perfil_observado", perfil)
            if (observado == 0L) editor.remove("perfil_observado_em") else editor.putLong("perfil_observado_em", observado)
            editor.commit()
        }
    }

    private fun textos(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { textos(view.getChildAt(it)) }
        else -> emptyList()
    }
}

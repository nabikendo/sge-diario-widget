package br.to.seduc.sgediariowidget

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.updateAll
import br.to.seduc.sgediariowidget.data.SgeRepository
import br.to.seduc.sgediariowidget.data.SgeSummary
import br.to.seduc.sgediariowidget.widget.DiarioGlanceWidget
import br.to.seduc.sgediariowidget.worker.SgeSyncWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SgeSyncWorker.agendarPeriodico(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF000000),
                    surface = Color(0xFF0D1017),
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFF34D399)
                )
            ) {
                SgeMainScreen()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun SgeMainScreen() {
        var abaAtiva by remember { mutableIntStateOf(0) }
        var resumo by remember { mutableStateOf(SgeRepository.lerResumo(this@MainActivity)) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .padding(16.dp)
        ) {
            Text(
                text = "SGE 3.0 • Diário Widget",
                color = Color(0xFF38BDF8),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Monitoramento de Conteúdos, Frequência, Planos e PEIs",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { abaAtiva = 0 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (abaAtiva == 0) Color(0xFF38BDF8) else Color(0xFF161B22)
                    )
                ) {
                    Text("Painel & Ajustes", color = if (abaAtiva == 0) Color.Black else Color.White)
                }
                Button(
                    onClick = { abaAtiva = 1 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (abaAtiva == 1) Color(0xFF38BDF8) else Color(0xFF161B22)
                    )
                ) {
                    Text("Login SGE (Web)", color = if (abaAtiva == 1) Color.Black else Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (abaAtiva == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CounterControlCard("Conteúdos Pendentes", resumo.conteudos) { novo ->
                        resumo = resumo.copy(conteudos = novo, sessaoAtiva = true)
                        salvarEAtualizarWidget(resumo)
                    }
                    CounterControlCard("Frequências Pendentes", resumo.frequencia) { novo ->
                        resumo = resumo.copy(frequencia = novo, sessaoAtiva = true)
                        salvarEAtualizarWidget(resumo)
                    }
                    CounterControlCard("Planos de Aula", resumo.planosAula) { novo ->
                        resumo = resumo.copy(planosAula = novo, sessaoAtiva = true)
                        salvarEAtualizarWidget(resumo)
                    }
                    CounterControlCard("Plano de Curso", resumo.planoCurso) { novo ->
                        resumo = resumo.copy(planoCurso = novo, sessaoAtiva = true)
                        salvarEAtualizarWidget(resumo)
                    }
                    CounterControlCard("PEIs Pendentes", resumo.peis) { novo ->
                        resumo = resumo.copy(peis = novo, sessaoAtiva = true)
                        salvarEAtualizarWidget(resumo)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            SgeSyncWorker.sincronizarAgora(this@MainActivity)
                            resumo = SgeRepository.lerResumo(this@MainActivity)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399))
                    ) {
                        Text("Sincronizar com SGE Agora", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        CookieManager.getInstance().flush()
                                        if (url != null && !url.contains("/Acesso/Entrar", ignoreCase = true)) {
                                            resumo = resumo.copy(sessaoAtiva = true)
                                            salvarEAtualizarWidget(resumo)
                                            SgeSyncWorker.sincronizarAgora(ctx)
                                        }
                                    }
                                }
                                loadUrl("https://sge.seduc.to.pontoid.com.br/Acesso/Entrar")
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun CounterControlCard(titulo: String, valor: Int, onValueChange: (Int) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1017), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(titulo, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (valor == 0) "Em dia" else "$valor pendência(s)",
                    color = if (valor == 0) Color(0xFF34D399) else Color(0xFFF87171),
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (valor > 0) onValueChange(valor - 1) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Text("-", color = Color.White)
                }
                Text(
                    text = valor.toString(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { onValueChange(valor + 1) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Text("+", color = Color.White)
                }
            }
        }
    }

    private fun salvarEAtualizarWidget(summary: SgeSummary) {
        SgeRepository.salvarResumo(this, summary)
        lifecycleScope.launch {
            DiarioGlanceWidget().updateAll(this@MainActivity)
        }
    }
}

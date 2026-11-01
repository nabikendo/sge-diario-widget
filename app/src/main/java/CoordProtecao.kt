package br.to.seduc.sgediariowidget

import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** O desbloqueio é apenas em memória; não se grava um estado autenticado em preferências. */
class CoordProtecao(private val activity: FragmentActivity) {
    private var sucesso: (() -> Unit)? = null
    private val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { val acao = sucesso; sucesso = null; acao?.invoke() }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            sucesso = null
            if (errorCode !in setOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED))
                Toast.makeText(activity, errString, Toast.LENGTH_LONG).show()
        }
    })
    fun autenticar(acao: () -> Unit) {
        if (sucesso != null) return
        val tipos = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(activity).canAuthenticate(tipos) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(activity, if (Build.VERSION.SDK_INT >= 30) "Configure biometria ou bloqueio de tela no aparelho para usar esta opção."
                else "Cadastre uma biometria no aparelho para usar esta opção no Android 10.", Toast.LENGTH_LONG).show(); return
        }
        sucesso = acao
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Desbloquear coordenação")
            .setSubtitle("Confirme sua identidade neste aparelho").setAllowedAuthenticators(tipos)
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Cancelar")
        try { prompt.authenticate(info.build()) } catch (e: Exception) { sucesso = null; Toast.makeText(activity, "Não foi possível iniciar a autenticação.", Toast.LENGTH_LONG).show() }
    }
}
@Composable fun TelaCoordenacaoProtegida(desbloquear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(SgeIcons.User, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Coordenação protegida", style = MaterialTheme.typography.headlineSmall)
        Text("Confirme sua identidade para acessar a coordenação e a sessão do SGE Web. O app bloqueia novamente ao ir para segundo plano.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = desbloquear) { Text("Desbloquear") }
    }
}

package br.to.seduc.sgediariowidget

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText

/**
 * Cliente "chrome" do WebView do SGE Web.
 *
 * Sem um WebChromeClient o WebView ignora `alert`, `confirm` e `prompt` (confirm devolve sempre "cancelar")
 * e não abre o seletor de arquivos. O SGE usa essas janelas ao salvar Conteúdo Aplicado e Frequência
 * ("Deseja salvar?"), então o lançamento parecia travado: o clique em Salvar não fazia nada.
 *
 * Aqui cada janela do JavaScript vira um diálogo nativo. Cada [JsResult] é respondido exatamente uma vez.
 */
class SgeWebChromeClient(
    private val activity: Activity,
    private val abrirSeletor: (ValueCallback<Array<Uri>>, Intent) -> Boolean,
) : WebChromeClient() {

    private fun podeMostrar() = !activity.isFinishing && !activity.isDestroyed

    private fun titulo(view: WebView?, url: String?): String =
        runCatching { Uri.parse(url ?: view?.url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "SGE"

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
        if (!podeMostrar()) { result.cancel(); return true }
        AlertDialog.Builder(activity).setTitle(titulo(view, url)).setMessage(message.orEmpty())
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
        if (!podeMostrar()) { result.cancel(); return true }
        AlertDialog.Builder(activity).setTitle(titulo(view, url)).setMessage(message.orEmpty())
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
        if (!podeMostrar()) { result.cancel(); return true }
        val campo = EditText(activity).apply { inputType = InputType.TYPE_CLASS_TEXT; setText(defaultValue.orEmpty()); setSelection(text.length) }
        AlertDialog.Builder(activity).setTitle(titulo(view, url)).setMessage(message.orEmpty()).setView(campo)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(campo.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    /** Fecha o pedido de "sair da página" (beforeunload) sem prender a navegação. */
    override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
        result.confirm()
        return true
    }

    override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        val intent = runCatching { params.createIntent() }.getOrNull()
        if (intent == null || !abrirSeletor(callback, intent)) { callback.onReceiveValue(null); return true }
        return true
    }
}

package br.to.seduc.sgediariowidget

/** Helpers do modo navegador/desktop do WebView do SGE. */
internal object SgeModoWeb {
    const val PREFERENCIA = "web_modo_computador"

    fun userAgentComputador(userAgentMovel: String): String {
        val chrome = Regex("Chrome/[\\d.]+")
            .find(userAgentMovel)
            ?.value
            ?: "Chrome/120.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }
}

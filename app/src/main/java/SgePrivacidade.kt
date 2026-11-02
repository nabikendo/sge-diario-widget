package br.to.seduc.sgediariowidget.data

object SgePrivacidade {
    private val particulas = setOf("da", "de", "do", "das", "dos", "e")
    /** "Maria da Silva" vira "M. S.": identifica para quem conhece, sem expor o nome em telas ou capturas. */
    fun iniciais(nome: String): String = nome.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.lowercase() !in particulas }
        .joinToString(" ") { it.first().uppercase() + "." }.ifBlank { "—" }
}

package br.to.seduc.sgediariowidget.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Backup na pasta oculta do aplicativo no Google Drive (appDataFolder).
 * O app só enxerga o que ele mesmo gravou; nada aparece no Drive do usuário.
 * O conteúdo é o mesmo JSON de [SgeBackup]: sem senha, cookies ou sessão do SGE.
 */
object SgeDriveBackup {
    const val ESCOPO = "https://www.googleapis.com/auth/drive.appdata"
    const val ARQUIVO = "sge-diario-backup.json"
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    class SemBackup : Exception("Nenhum backup do SGE Diário foi encontrado no Google Drive desta conta.")
    class Falha(val codigo: Int) : IOException("Erro $codigo do Google Drive")

    private fun conexao(url: String, token: String, metodo: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            // HttpURLConnection não aceita PATCH; o Google aceita o método por cabeçalho.
            requestMethod = if (metodo == "PATCH") "POST" else metodo
            if (metodo == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

    private fun checar(c: HttpURLConnection) {
        if (c.responseCode !in 200..299) throw Falha(c.responseCode)
    }

    private fun procurarId(token: String): String? {
        val q = URLEncoder.encode("name='$ARQUIVO'", "UTF-8")
        val c = conexao("$API?spaces=appDataFolder&q=$q&fields=files(id)&pageSize=1", token)
        try {
            checar(c)
            val corpo = c.inputStream.use { it.readBytesLimitados(64 * 1024) }.toString(Charsets.UTF_8)
            val arquivos = JSONObject(corpo).getJSONArray("files")
            return if (arquivos.length() == 0) null else arquivos.getJSONObject(0).getString("id")
        } finally {
            c.disconnect()
        }
    }

    private fun enviarCorpo(c: HttpURLConnection, tipo: String, corpo: ByteArray) {
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", tipo)
            c.setFixedLengthStreamingMode(corpo.size)
            c.outputStream.use { it.write(corpo) }
            checar(c)
            c.inputStream.use { it.readBytesLimitados(64 * 1024) }
        } finally {
            c.disconnect()
        }
    }

    /** Cria o arquivo na primeira vez e o substitui nas seguintes (um único backup por conta). */
    fun enviar(token: String, json: String) {
        val conteudo = json.toByteArray(Charsets.UTF_8)
        require(conteudo.size <= SgeBackup.LIMITE) { "Backup maior que 10 MB." }
        val id = procurarId(token)
        if (id == null) {
            val limite = "sge-diario-" + System.nanoTime()
            val meta = JSONObject().put("name", ARQUIVO).put("parents", JSONArray().put("appDataFolder")).toString()
            val corpo = ByteArrayOutputStream().apply {
                write("--$limite\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray(Charsets.UTF_8))
                write("--$limite\r\nContent-Type: application/json\r\n\r\n".toByteArray(Charsets.UTF_8))
                write(conteudo)
                write("\r\n--$limite--".toByteArray(Charsets.UTF_8))
            }.toByteArray()
            enviarCorpo(conexao("$UPLOAD?uploadType=multipart&fields=id", token, "POST"), "multipart/related; boundary=$limite", corpo)
        } else {
            enviarCorpo(conexao("$UPLOAD/$id?uploadType=media&fields=id", token, "PATCH"), "application/json", conteudo)
        }
    }

    /** Devolve o texto do backup; a validação e a confirmação ficam com [SgeBackup]. */
    fun baixar(token: String): String {
        val id = procurarId(token) ?: throw SemBackup()
        val c = conexao("$API/$id?alt=media", token)
        try {
            checar(c)
            return c.inputStream.use { it.readBytesLimitados(SgeBackup.LIMITE) }.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }
}

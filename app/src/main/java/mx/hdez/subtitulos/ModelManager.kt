package mx.hdez.subtitulos

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Descarga (una sola vez) el modelo de voz de Vosk en inglés y lo descomprime. */
object ModelManager {
    private const val NAME = "vosk-model-small-en-us-0.15"
    private const val URL_ZIP = "https://alphacephei.com/vosk/models/$NAME.zip"

    private fun root(ctx: Context) = File(ctx.filesDir, "models")
    fun modelDir(ctx: Context) = File(root(ctx), NAME)
    private fun marker(ctx: Context) = File(root(ctx), "$NAME.ok")

    fun isReady(ctx: Context) = marker(ctx).exists() && modelDir(ctx).isDirectory

    /** Bloqueante: llamar desde un hilo de fondo. Lanza excepción si falla. */
    fun download(ctx: Context, onProgress: (Int) -> Unit) {
        val base = root(ctx).apply { mkdirs() }
        modelDir(ctx).deleteRecursively()
        marker(ctx).delete()

        val conn = URL(URL_ZIP).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        try {
            val total = conn.contentLengthLong
            var bytesRead = 0L
            var lastPct = -1
            val counting = object : FilterInputStream(conn.inputStream) {
                override fun read(): Int = super.read().also { if (it >= 0) tick(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, len).also { if (it > 0) tick(it) }

                private fun tick(n: Int) {
                    bytesRead += n
                    if (total > 0) {
                        val pct = (bytesRead * 100 / total).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
            unzip(counting, base)
            if (!modelDir(ctx).isDirectory) error("El modelo descargado está incompleto")
            marker(ctx).writeText("ok")
        } catch (e: Exception) {
            modelDir(ctx).deleteRecursively()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun unzip(input: InputStream, dest: File) {
        val destPath = dest.canonicalPath + File.separator
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                // Protección contra rutas maliciosas dentro del zip
                if (!out.canonicalPath.startsWith(destPath)) error("Entrada de zip inválida")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
        }
    }
}

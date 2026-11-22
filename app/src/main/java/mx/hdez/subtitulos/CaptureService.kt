package mx.hdez.subtitulos

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Captura el audio que suena en el teléfono (Dailymotion), lo transcribe en inglés con Vosk,
 * lo traduce a español con ML Kit y lo muestra en la franja flotante.
 */
class CaptureService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "subtitulos"
        private const val SAMPLE_RATE = 16000
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var overlay: SubtitleOverlay? = null
    private var translator: Translator? = null
    private var worker: Thread? = null

    @Volatile private var running = false
    private var requested = 0 // solo hilo principal
    private var shown = 0     // solo hilo principal
    private val clearRunnable = Runnable { overlay?.hide() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null || running) return START_NOT_STICKY

        createChannel()
        ServiceCompat.startForeground(
            this, 1, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(code, data)
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopSelf() }
            }
        }, main)
        projection = proj
        running = true

        overlay = SubtitleOverlay(this).also { it.show("Preparando traducción…") }
        prepareTranslator(proj)
        return START_NOT_STICKY
    }

    private fun prepareTranslator(proj: MediaProjection) {
        val t = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build()
        )
        translator = t
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (running) {
                    overlay?.show("Listo. Reproduce el video.")
                    main.postDelayed(clearRunnable, 3000)
                    startCapture(proj)
                }
            }
            .addOnFailureListener {
                overlay?.show("No se pudo descargar el traductor. Revisa tu internet.")
            }
    }

    @SuppressLint("MissingPermission")
    private fun startCapture(proj: MediaProjection) {
        val config = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuf, 16384))
            .setAudioPlaybackCaptureConfig(config)
            .build()

        worker = Thread({
            var model: Model? = null
            var rec: Recognizer? = null
            try {
                main.post { overlay?.show("Cargando reconocimiento de voz…") }
                model = Model(ModelManager.modelDir(this).absolutePath)
                rec = Recognizer(model, SAMPLE_RATE.toFloat())
                record.startRecording()
                main.post { overlay?.hide() }

                val buf = ByteArray(4096)
                var lastPartial = ""
                var lastPartialAt = 0L
                while (running) {
                    val n = record.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (rec.acceptWaveForm(buf, n)) {
                        push(JSONObject(rec.result).optString("text"))
                        lastPartial = ""
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastPartialAt > 1000) {
                            val p = JSONObject(rec.partialResult).optString("partial")
                            if (p.isNotBlank() && p != lastPartial) {
                                lastPartial = p
                                lastPartialAt = now
                                push(p)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                main.post { overlay?.show("Error: ${e.message}") }
            } finally {
                runCatching { record.stop() }
                record.release()
                rec?.close()
                model?.close()
            }
        }, "captura-audio").also { it.start() }
    }

    /** Traduce el texto y lo muestra; descarta traducciones que lleguen tarde u desordenadas. */
    private fun push(text: String) {
        if (text.isBlank()) return
        main.post {
            val id = ++requested
            translator?.translate(text)?.addOnSuccessListener { es ->
                if (id > shown && running) {
                    shown = id
                    overlay?.show(es)
                    main.removeCallbacks(clearRunnable)
                    main.postDelayed(clearRunnable, 6000)
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        projection?.stop()
        overlay?.remove()
        translator?.close()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Subtítulos", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, CaptureService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Subtítulos en español activos")
            .setContentText("Traduciendo el audio del teléfono")
            .setOngoing(true)
            .addAction(0, "Detener", stop)
            .build()
    }
}

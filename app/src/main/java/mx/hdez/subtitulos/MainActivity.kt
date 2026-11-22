package mx.hdez.subtitulos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val permsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) start() else setStatus("Faltan permisos (audio y notificaciones).")
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) {
                val i = Intent(this, CaptureService::class.java)
                    .putExtra(CaptureService.EXTRA_CODE, r.resultCode)
                    .putExtra(CaptureService.EXTRA_DATA, data)
                ContextCompat.startForegroundService(this, i)
                setStatus("Subtítulos activos. Abre Dailymotion y reproduce un video.")
            } else {
                setStatus("Permiso de captura de audio denegado.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Subtítulos en español para Dailymotion"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "\n1. Toca \"Iniciar\" y concede los permisos.\n" +
                "2. Abre Dailymotion y reproduce el video en inglés.\n" +
                "3. Los subtítulos en español aparecerán abajo de la pantalla.\n"
        })
        status = TextView(this).apply { text = "Listo para iniciar." }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Iniciar"
            setOnClickListener { start() }
        })
        root.addView(Button(this).apply {
            text = "Detener"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                setStatus("Detenido.")
            }
        })
        setContentView(root)
    }

    private fun setStatus(s: String) {
        status.text = s
    }

    /** Pasos en orden: permiso de superposición -> permisos normales -> modelo de voz -> captura. */
    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            setStatus("Activa \"Mostrar sobre otras apps\" para esta app y vuelve a tocar Iniciar.")
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permsLauncher.launch(missing.toTypedArray())
            return
        }

        if (!ModelManager.isReady(this)) {
            setStatus("Descargando modelo de voz (una sola vez)…")
            thread {
                try {
                    ModelManager.download(applicationContext) { pct ->
                        runOnUiThread { setStatus("Descargando modelo de voz… $pct%") }
                    }
                    runOnUiThread { requestCapture() }
                } catch (e: Exception) {
                    runOnUiThread { setStatus("No se pudo descargar el modelo: ${e.message}") }
                }
            }
            return
        }

        requestCapture()
    }

    private fun requestCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}

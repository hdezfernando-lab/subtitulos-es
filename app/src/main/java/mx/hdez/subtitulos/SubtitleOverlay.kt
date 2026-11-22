package mx.hdez.subtitulos

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Franja de subtítulos que flota sobre cualquier app (necesita permiso "Mostrar sobre otras apps").
 * Se puede arrastrar hacia arriba o abajo con el dedo; la posición se recuerda.
 */
class SubtitleOverlay(ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = ctx.getSharedPreferences("overlay", Context.MODE_PRIVATE)
    private val screenHeight = ctx.resources.displayMetrics.heightPixels

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM
        y = prefs.getInt("y", 120)
    }

    private val tv = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        textSize = 24f
        gravity = Gravity.CENTER
        setPadding(32, 20, 32, 20)
        setBackgroundColor(0xB3000000.toInt())
        visibility = View.GONE
    }

    private var lastY = 0f

    init {
        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Con gravedad inferior, "y" es la distancia desde el borde de abajo.
                    val maxY = maxOf(0, screenHeight - tv.height)
                    lp.y = (lp.y - (e.rawY - lastY)).toInt().coerceIn(0, maxY)
                    lastY = e.rawY
                    wm.updateViewLayout(tv, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("y", lp.y).apply()
                    v.performClick()
                    true
                }
                else -> false
            }
        }
        wm.addView(tv, lp)
    }

    fun show(text: String) {
        tv.text = text
        tv.visibility = View.VISIBLE
    }

    fun hide() {
        tv.visibility = View.GONE
    }

    fun remove() {
        runCatching { wm.removeView(tv) }
    }
}

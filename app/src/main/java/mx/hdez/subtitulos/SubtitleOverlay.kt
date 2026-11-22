package mx.hdez.subtitulos

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Franja de subtítulos que flota sobre cualquier app (necesita permiso "Mostrar sobre otras apps"). */
class SubtitleOverlay(ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val tv = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        textSize = 18f
        gravity = Gravity.CENTER
        setPadding(32, 16, 32, 16)
        setBackgroundColor(0xB3000000.toInt())
        visibility = View.GONE
    }

    init {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 120
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

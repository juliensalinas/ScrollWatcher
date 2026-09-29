package com.juliensalinas.scrollwatcher.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.juliensalinas.scrollwatcher.R
import com.juliensalinas.scrollwatcher.ui.MainActivity

/**
 * SYSTEM_ALERT_WINDOW full-screen overlay shown when the daily scroll budget is exhausted
 * and an evil app is in the foreground.
 */
class LockOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null

    val isShowing: Boolean get() = rootView != null

    fun show() {
        if (rootView != null) return
        if (!android.provider.Settings.canDrawOverlays(context)) return

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(48))
            setBackgroundColor(0xF0121A24.toInt())
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_title)
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.overlay_subtitle)
            textSize = 16f
            setTextColor(0xFFB0B8C4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        val remaining = TextView(context).apply {
            text = context.getString(R.string.overlay_remaining_zero)
            textSize = 18f
            setTextColor(0xFFE57373.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(32))
        }
        val openButton = Button(context).apply {
            text = context.getString(R.string.overlay_open_app)
            setOnClickListener {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
            }
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(remaining)
        container.addView(openButton)

        val frame = FrameLayout(context).apply {
            addView(
                container,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            setBackgroundColor(0xF0121A24.toInt())
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(frame, params)
            rootView = frame
        } catch (_: Exception) {
            rootView = null
        }
    }

    fun hide() {
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // already removed
        }
        rootView = null
    }
}

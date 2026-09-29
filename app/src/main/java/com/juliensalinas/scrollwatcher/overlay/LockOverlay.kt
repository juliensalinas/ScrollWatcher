package com.juliensalinas.scrollwatcher.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.juliensalinas.scrollwatcher.R
import com.juliensalinas.scrollwatcher.ui.MainActivity

/**
 * SYSTEM_ALERT_WINDOW full-screen overlay shown when the daily scroll budget is exhausted
 * and an evil app is in the foreground.
 *
 * WindowManager add/remove must run on the main thread; callers may invoke from any thread.
 */
class LockOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rootView: FrameLayout? = null

    val isShowing: Boolean get() = rootView != null

    fun show() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showOnMain()
        } else {
            mainHandler.post { showOnMain() }
        }
    }

    fun hide() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideOnMain()
        } else {
            mainHandler.post { hideOnMain() }
        }
    }

    private fun showOnMain() {
        if (rootView != null) {
            Log.d(TAG, "overlay already showing — skip addView")
            return
        }
        if (!android.provider.Settings.canDrawOverlays(context)) {
            Log.w(
                TAG,
                "SYSTEM_ALERT_WINDOW / canDrawOverlays=false — cannot show lock overlay"
            )
            return
        }

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
            // Consume all touches so the underlying evil app cannot be used.
            isClickable = true
            isFocusable = true
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

        // Focusable + touchable (no FLAG_NOT_FOCUSABLE / FLAG_NOT_TOUCHABLE) so the lock
        // blocks interaction with the app underneath and the "Open ScrollWatcher" button works.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(frame, params)
            rootView = frame
            Log.i(TAG, "lock overlay shown")
        } catch (e: IllegalStateException) {
            // Already attached somehow — treat as showing.
            Log.w(TAG, "addView IllegalStateException (already added?): ${e.message}")
            rootView = frame
        } catch (e: Exception) {
            Log.e(TAG, "failed to show lock overlay: ${e.javaClass.simpleName}: ${e.message}", e)
            rootView = null
        }
    }

    private fun hideOnMain() {
        val view = rootView ?: return
        try {
            windowManager.removeView(view)
            Log.i(TAG, "lock overlay hidden")
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed (already removed?): ${e.message}")
        }
        rootView = null
    }

    companion object {
        private const val TAG = "ScrollWatcher"
    }
}

package com.juliensalinas.scrollwatcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.juliensalinas.scrollwatcher.R
import com.juliensalinas.scrollwatcher.data.BudgetPreferences
import com.juliensalinas.scrollwatcher.overlay.LockOverlay
import com.juliensalinas.scrollwatcher.tracking.ForegroundAppTracker
import com.juliensalinas.scrollwatcher.tracking.PermissionHelper
import com.juliensalinas.scrollwatcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that polls UsageStatsManager every ~1.5s while the screen is on,
 * accumulates evil-app usage into DataStore, and shows/hides the lock overlay.
 */
class ScrollMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var preferences: BudgetPreferences
    private lateinit var tracker: ForegroundAppTracker
    private lateinit var overlay: LockOverlay
    private var pollJob: Job? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var screenOn: Boolean = true

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "service onCreate — starting foreground + poll loop")
        preferences = BudgetPreferences(applicationContext)
        tracker = ForegroundAppTracker(applicationContext)
        overlay = LockOverlay(applicationContext)
        screenOn = PermissionHelper.isScreenInteractive(this)
        registerScreenReceiver()
        startForeground(NOTIFICATION_ID, buildNotification())
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "service ACTION_STOP")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SCREEN_ON -> {
                screenOn = true
                Log.d(TAG, "screen ON")
            }
            ACTION_SCREEN_OFF -> {
                screenOn = false
                overlay.hide()
                Log.d(TAG, "screen OFF")
            }
            else -> Log.d(TAG, "onStartCommand action=${intent?.action}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "service onDestroy")
        pollJob?.cancel()
        overlay.hide()
        unregisterScreenReceiver()
        scope.cancel()
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        val receiver = ScreenStateReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        screenReceiver = receiver
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        screenReceiver = null
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive) {
                preferences.ensureDailyReset()
                val state = preferences.budgetState.first()

                if (!state.monitoringEnabled) {
                    overlay.hide()
                    Log.d(TAG, "monitoring disabled — skipping tick")
                    delay(POLL_INTERVAL_MS)
                    lastTick = System.currentTimeMillis()
                    continue
                }

                val now = System.currentTimeMillis()
                val elapsed = (now - lastTick).coerceAtLeast(0L).coerceAtMost(POLL_INTERVAL_MS * 2)
                lastTick = now

                // Prefer live PowerManager state; keep screenOn as a fast path from broadcasts.
                val interactive = PermissionHelper.isScreenInteractive(this@ScrollMonitorService)
                if (interactive) screenOn = true
                val hasUsage = PermissionHelper.hasUsageAccess(this@ScrollMonitorService)

                if (interactive && hasUsage) {
                    val fg = tracker.currentForegroundPackage()
                    val isEvil = fg != null &&
                        fg != packageName &&
                        state.evilPackages.contains(fg)

                    Log.d(
                        TAG,
                        "tick fg=$fg evil=$isEvil evilCount=${state.evilPackages.size} " +
                            "deltaMs=$elapsed usedMs=${state.usedMillis} " +
                            "remainingMs=${state.remainingMillis}"
                    )

                    if (isEvil) {
                        preferences.addUsedMillis(elapsed)
                        val after = preferences.budgetState.first()
                        Log.i(
                            TAG,
                            "added ${elapsed}ms for $fg → used=${after.usedMillis} " +
                                "remaining=${after.remainingMillis}"
                        )
                    }

                    val refreshed = preferences.budgetState.first()
                    val stillEvil = fg != null &&
                        fg != packageName &&
                        refreshed.evilPackages.contains(fg)
                    val exhausted = refreshed.isExhausted
                    val shouldLock = stillEvil && exhausted

                    if (shouldLock) {
                        if (!PermissionHelper.hasOverlayPermission(this@ScrollMonitorService)) {
                            Log.w(
                                TAG,
                                "budget exhausted in evil app fg=$fg but " +
                                    "SYSTEM_ALERT_WINDOW missing — overlay not shown"
                            )
                            overlay.hide()
                        } else {
                            Log.i(
                                TAG,
                                "showing lock overlay (fg=$fg remaining=${refreshed.remainingMillis})"
                            )
                            overlay.show()
                        }
                    } else {
                        if (overlay.isShowing) {
                            Log.d(
                                TAG,
                                "hiding lock overlay (stillEvil=$stillEvil exhausted=$exhausted)"
                            )
                        }
                        overlay.hide()
                    }
                } else {
                    if (!interactive) {
                        Log.d(TAG, "tick skipped: screen not interactive")
                    } else {
                        Log.d(TAG, "tick skipped: no usage access")
                    }
                    overlay.hide()
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun buildNotification(): Notification {
        createChannel()
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ScrollWatcher"
        const val CHANNEL_ID = "scroll_monitor"
        const val NOTIFICATION_ID = 1001
        const val POLL_INTERVAL_MS = 1500L
        const val ACTION_STOP = "com.juliensalinas.scrollwatcher.STOP"
        const val ACTION_SCREEN_ON = "com.juliensalinas.scrollwatcher.SCREEN_ON"
        const val ACTION_SCREEN_OFF = "com.juliensalinas.scrollwatcher.SCREEN_OFF"

        fun start(context: android.content.Context) {
            Log.i(TAG, "ScrollMonitorService.start()")
            val intent = Intent(context, ScrollMonitorService::class.java)
            ContextCompatStart.start(context, intent)
        }

        fun stop(context: android.content.Context) {
            Log.i(TAG, "ScrollMonitorService.stop()")
            val intent = Intent(context, ScrollMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
            context.stopService(Intent(context, ScrollMonitorService::class.java))
        }
    }
}

/** Tiny helper to avoid importing androidx.core everywhere for startForegroundService. */
private object ContextCompatStart {
    fun start(context: android.content.Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

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
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SCREEN_ON -> {
                screenOn = true
            }
            ACTION_SCREEN_OFF -> {
                screenOn = false
                overlay.hide()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
                    delay(POLL_INTERVAL_MS)
                    lastTick = System.currentTimeMillis()
                    continue
                }

                val now = System.currentTimeMillis()
                val elapsed = (now - lastTick).coerceAtLeast(0L).coerceAtMost(POLL_INTERVAL_MS * 2)
                lastTick = now

                val interactive = screenOn && PermissionHelper.isScreenInteractive(this@ScrollMonitorService)
                if (interactive && PermissionHelper.hasUsageAccess(this@ScrollMonitorService)) {
                    val fg = tracker.currentForegroundPackage()
                    val isEvil = fg != null &&
                        fg != packageName &&
                        state.evilPackages.contains(fg)
                    if (isEvil) {
                        preferences.addUsedMillis(elapsed)
                    }
                    val refreshed = preferences.budgetState.first()
                    val stillEvil = fg != null &&
                        fg != packageName &&
                        refreshed.evilPackages.contains(fg)
                    if (stillEvil && refreshed.isExhausted &&
                        PermissionHelper.hasOverlayPermission(this@ScrollMonitorService)
                    ) {
                        overlay.show()
                    } else {
                        overlay.hide()
                    }
                } else {
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
        const val CHANNEL_ID = "scroll_monitor"
        const val NOTIFICATION_ID = 1001
        const val POLL_INTERVAL_MS = 1500L
        const val ACTION_STOP = "com.juliensalinas.scrollwatcher.STOP"
        const val ACTION_SCREEN_ON = "com.juliensalinas.scrollwatcher.SCREEN_ON"
        const val ACTION_SCREEN_OFF = "com.juliensalinas.scrollwatcher.SCREEN_OFF"

        fun start(context: android.content.Context) {
            val intent = Intent(context, ScrollMonitorService::class.java)
            ContextCompatStart.start(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, ScrollMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
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

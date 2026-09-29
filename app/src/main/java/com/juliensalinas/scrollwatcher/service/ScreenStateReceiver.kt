package com.juliensalinas.scrollwatcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Relays SCREEN_ON / SCREEN_OFF to the foreground monitor service.
 * Registered dynamically by the service (manifest entry is a fallback / documentation).
 *
 * Important: must deliver the action on the service Intent. Calling
 * ScrollMonitorService.start() alone drops ACTION_SCREEN_ON and left screenOn stuck false
 * after the first screen-off — so usage never accumulated.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val serviceIntent = Intent(context, ScrollMonitorService::class.java)
        when (action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "SCREEN_ON → service")
                serviceIntent.action = ScrollMonitorService.ACTION_SCREEN_ON
                startService(context, serviceIntent)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "SCREEN_OFF → service")
                serviceIntent.action = ScrollMonitorService.ACTION_SCREEN_OFF
                try {
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "SCREEN_OFF startService failed: ${e.message}")
                }
            }
        }
    }

    private fun startService(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startService failed: ${e.message}")
            try {
                context.startService(intent)
            } catch (e2: Exception) {
                Log.w(TAG, "fallback startService failed: ${e2.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ScrollWatcher"
    }
}

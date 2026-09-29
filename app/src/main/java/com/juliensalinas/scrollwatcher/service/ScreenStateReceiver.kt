package com.juliensalinas.scrollwatcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Relays SCREEN_ON / SCREEN_OFF to the foreground monitor service.
 * Registered dynamically by the service (manifest entry is a fallback / documentation).
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val serviceIntent = Intent(context, ScrollMonitorService::class.java)
        when (action) {
            Intent.ACTION_SCREEN_ON -> {
                serviceIntent.action = ScrollMonitorService.ACTION_SCREEN_ON
                ScrollMonitorService.start(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                serviceIntent.action = ScrollMonitorService.ACTION_SCREEN_OFF
                try {
                    context.startService(serviceIntent)
                } catch (_: Exception) {
                    // service may not be running
                }
            }
        }
    }
}

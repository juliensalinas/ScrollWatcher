package com.juliensalinas.scrollwatcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juliensalinas.scrollwatcher.data.BudgetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = BudgetPreferences(context.applicationContext)
                prefs.ensureDailyReset()
                val state = prefs.budgetState.first()
                if (state.monitoringEnabled) {
                    ScrollMonitorService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

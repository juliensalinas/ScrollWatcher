package com.juliensalinas.scrollwatcher

import android.app.Application
import com.juliensalinas.scrollwatcher.data.BudgetPreferences
import com.juliensalinas.scrollwatcher.service.ScrollMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScrollWatcherApp : Application() {
    val preferences by lazy { BudgetPreferences(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            preferences.ensureDailyReset()
            val state = preferences.budgetState.first()
            if (state.monitoringEnabled) {
                ScrollMonitorService.start(this@ScrollWatcherApp)
            }
        }
    }
}

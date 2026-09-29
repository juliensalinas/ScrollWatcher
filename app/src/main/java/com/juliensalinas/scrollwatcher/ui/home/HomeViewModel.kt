package com.juliensalinas.scrollwatcher.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juliensalinas.scrollwatcher.ScrollWatcherApp
import com.juliensalinas.scrollwatcher.data.BudgetState
import com.juliensalinas.scrollwatcher.service.ScrollMonitorService
import com.juliensalinas.scrollwatcher.tracking.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiExtras(
    val hasUsageAccess: Boolean = false,
    val hasOverlay: Boolean = false,
    val hasNotifications: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as ScrollWatcherApp).preferences

    val budgetState: StateFlow<BudgetState> = prefs.budgetState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetState())

    private val _extras = MutableStateFlow(HomeUiExtras())
    val extras: StateFlow<HomeUiExtras> = _extras.asStateFlow()

    init {
        viewModelScope.launch { prefs.ensureDailyReset() }
        refreshPermissions()
        ensureMonitorRunning()
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _extras.value = HomeUiExtras(
            hasUsageAccess = PermissionHelper.hasUsageAccess(ctx),
            hasOverlay = PermissionHelper.hasOverlayPermission(ctx),
            hasNotifications = PermissionHelper.hasNotificationPermission(ctx),
        )
    }

    fun addExtraTenMinutes() {
        viewModelScope.launch { prefs.addExtraPackage() }
    }

    fun setMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setMonitoringEnabled(enabled)
            val ctx = getApplication<Application>()
            if (enabled) {
                ScrollMonitorService.start(ctx)
            } else {
                ScrollMonitorService.stop(ctx)
            }
            Log.i(TAG, "setMonitoring enabled=$enabled")
        }
    }

    /** If monitoring is enabled in prefs, (re)start the FGS — covers process death / missed start. */
    fun ensureMonitorRunning() {
        viewModelScope.launch {
            prefs.ensureDailyReset()
            val state = prefs.budgetState.first()
            if (state.monitoringEnabled) {
                Log.i(TAG, "ensureMonitorRunning: starting service")
                ScrollMonitorService.start(getApplication())
            }
        }
    }

    companion object {
        private const val TAG = "ScrollWatcher"
    }
}

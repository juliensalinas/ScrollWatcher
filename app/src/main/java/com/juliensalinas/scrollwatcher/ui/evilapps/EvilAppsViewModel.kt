package com.juliensalinas.scrollwatcher.ui.evilapps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juliensalinas.scrollwatcher.ScrollWatcherApp
import com.juliensalinas.scrollwatcher.data.InstalledApp
import com.juliensalinas.scrollwatcher.tracking.ForegroundAppTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EvilAppsUiState(
    val apps: List<InstalledApp> = emptyList(),
    val evilPackages: Set<String> = emptySet(),
    val query: String = "",
    val loading: Boolean = true,
) {
    val filtered: List<InstalledApp>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return apps
            return apps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
}

class EvilAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as ScrollWatcherApp).preferences
    private val tracker = ForegroundAppTracker(application)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _loading = MutableStateFlow(true)

    val uiState: StateFlow<EvilAppsUiState> = combine(
        _apps,
        prefs.budgetState,
        _query,
        _loading,
    ) { apps, budget, query, loading ->
        EvilAppsUiState(
            apps = apps,
            evilPackages = budget.evilPackages,
            query = query,
            loading = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EvilAppsUiState())

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            _loading.value = true
            val list = withContext(Dispatchers.IO) { tracker.listLaunchableApps() }
            _apps.value = list
            _loading.value = false
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setEvil(packageName: String, evil: Boolean) {
        viewModelScope.launch {
            prefs.setEvilPackage(packageName, evil)
        }
    }
}

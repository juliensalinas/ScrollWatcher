package com.juliensalinas.scrollwatcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

private val Context.budgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scroll_watcher_budget"
)

class BudgetPreferences(private val context: Context) {

    private object Keys {
        val USED_MILLIS = longPreferencesKey("used_millis")
        val EXTRA_MILLIS = longPreferencesKey("extra_millis")
        val LAST_RESET_DATE = stringPreferencesKey("last_reset_date")
        val EVIL_PACKAGES = stringSetPreferencesKey("evil_packages")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
    }

    val budgetState: Flow<BudgetState> = context.budgetDataStore.data.map { prefs ->
        BudgetState(
            usedMillis = prefs[Keys.USED_MILLIS] ?: 0L,
            extraMillis = prefs[Keys.EXTRA_MILLIS] ?: 0L,
            lastResetDate = prefs[Keys.LAST_RESET_DATE] ?: "",
            evilPackages = prefs[Keys.EVIL_PACKAGES] ?: emptySet(),
            monitoringEnabled = prefs[Keys.MONITORING_ENABLED] ?: true,
        )
    }

    suspend fun ensureDailyReset(zoneId: ZoneId = ZoneId.systemDefault()) {
        val today = LocalDate.now(zoneId).toString()
        context.budgetDataStore.edit { prefs ->
            val last = prefs[Keys.LAST_RESET_DATE] ?: ""
            if (last != today) {
                prefs[Keys.USED_MILLIS] = 0L
                prefs[Keys.EXTRA_MILLIS] = 0L
                prefs[Keys.LAST_RESET_DATE] = today
            }
        }
    }

    suspend fun addUsedMillis(delta: Long) {
        if (delta <= 0L) return
        context.budgetDataStore.edit { prefs ->
            val current = prefs[Keys.USED_MILLIS] ?: 0L
            prefs[Keys.USED_MILLIS] = current + delta
        }
    }

    suspend fun addExtraPackage() {
        context.budgetDataStore.edit { prefs ->
            val current = prefs[Keys.EXTRA_MILLIS] ?: 0L
            prefs[Keys.EXTRA_MILLIS] = current + BudgetState.EXTRA_PACKAGE_MILLIS
        }
    }

    suspend fun setEvilPackage(packageName: String, evil: Boolean) {
        context.budgetDataStore.edit { prefs ->
            val current = prefs[Keys.EVIL_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (evil) current.add(packageName) else current.remove(packageName)
            prefs[Keys.EVIL_PACKAGES] = current
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.budgetDataStore.edit { prefs ->
            prefs[Keys.MONITORING_ENABLED] = enabled
        }
    }
}

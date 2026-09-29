package com.juliensalinas.scrollwatcher.tracking

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.juliensalinas.scrollwatcher.data.InstalledApp

/**
 * Reads the current foreground package via UsageStatsManager (UsageEvents + UsageStats fallback).
 * Does NOT use AccessibilityService.
 *
 * Important: ACTIVITY_RESUMED only fires when an activity starts/resumes. A short lookback
 * window wrongly returns null while the user keeps scrolling the same activity for > lookback.
 * We use a multi-minute lookback and keep the last resume as the foreground package.
 */
class ForegroundAppTracker(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Last package we observed via ACTIVITY_RESUMED / MOVE_TO_FOREGROUND. */
    @Volatile
    private var cachedForeground: String? = null

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * Returns the package name of the most recent foreground activity, or null if unknown /
     * no permission.
     */
    fun currentForegroundPackage(lookbackMs: Long = DEFAULT_LOOKBACK_MS): String? {
        if (!hasUsageAccess()) {
            Log.d(TAG, "currentForegroundPackage: no usage access")
            cachedForeground = null
            return null
        }

        val fromEvents = foregroundFromEvents(lookbackMs)
        if (fromEvents != null) {
            cachedForeground = fromEvents
            return fromEvents
        }

        val fromStats = foregroundFromUsageStats(lookbackMs)
        if (fromStats != null) {
            cachedForeground = fromStats
            return fromStats
        }

        // No new events in the window (can happen briefly between polls). Keep last known
        // only if we have one — safer than inventing a package.
        Log.d(
            TAG,
            "currentForegroundPackage: no events/stats in lookback=${lookbackMs}ms, " +
                "cached=$cachedForeground"
        )
        return cachedForeground
    }

    private fun foregroundFromEvents(lookbackMs: Long): String? {
        val end = System.currentTimeMillis()
        val begin = end - lookbackMs
        val events = try {
            usageStatsManager.queryEvents(begin, end)
        } catch (e: Exception) {
            Log.w(TAG, "queryEvents failed: ${e.message}")
            return null
        } ?: return null

        val event = UsageEvents.Event()
        var foreground: String? = null
        var eventCount = 0
        var resumeCount = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            eventCount++
            @Suppress("DEPRECATION")
            val isFg = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (isFg && !event.packageName.isNullOrBlank()) {
                foreground = event.packageName
                resumeCount++
            }
        }
        Log.d(
            TAG,
            "foregroundFromEvents: events=$eventCount resumes=$resumeCount fg=$foreground"
        )
        return foreground
    }

    /**
     * Fallback: package with the most recent lastTimeUsed within the lookback window.
     * Less precise than UsageEvents but helps on OEMs that batch or delay events.
     */
    private fun foregroundFromUsageStats(lookbackMs: Long): String? {
        val end = System.currentTimeMillis()
        val begin = end - lookbackMs
        val stats = try {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                begin,
                end
            )
        } catch (e: Exception) {
            Log.w(TAG, "queryUsageStats failed: ${e.message}")
            return null
        }
        if (stats.isNullOrEmpty()) {
            Log.d(TAG, "foregroundFromUsageStats: empty")
            return null
        }
        val recent = stats
            .filter { it.lastTimeUsed >= begin && !it.packageName.isNullOrBlank() }
            .maxByOrNull { it.lastTimeUsed }
        Log.d(
            TAG,
            "foregroundFromUsageStats: candidates=${stats.size} fg=${recent?.packageName} " +
                "lastUsed=${recent?.lastTimeUsed}"
        )
        return recent?.packageName
    }

    fun listLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val self = context.packageName
        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == self) return@mapNotNull null
                val ai: ApplicationInfo = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                InstalledApp(
                    packageName = pkg,
                    label = pm.getApplicationLabel(ai).toString(),
                    icon = pm.getApplicationIcon(ai),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    companion object {
        private const val TAG = "ScrollWatcher"
        /** Multi-minute lookback so continuous scrolling still resolves the FG package. */
        const val DEFAULT_LOOKBACK_MS: Long = 120_000L
    }
}

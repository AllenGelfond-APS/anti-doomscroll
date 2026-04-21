package com.gelfond.focusblocker

import android.content.Context
import java.time.LocalDate

private const val USAGE_PREFS = "focus_blocker_usage_prefs"
private const val KEY_USAGE_DATE = "usage_date"
private const val KEY_ACTIVE_PACKAGE = "active_package"
private const val KEY_ACTIVE_START_MS = "active_start_ms"

private const val MAX_SINGLE_SESSION_MS = 2 * 60 * 60 * 1000L // 2 hours

private fun packageMillisKey(packageName: String): String {
    return "millis_$packageName"
}

private fun getUsagePrefs(context: Context) =
    context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE)

private fun todayString(): String = LocalDate.now().toString()

private fun resetIfNewDay(context: Context) {
    val prefs = getUsagePrefs(context)
    val today = todayString()
    val savedDate = prefs.getString(KEY_USAGE_DATE, null)

    if (savedDate != today) {
        prefs.edit()
            .clear()
            .putString(KEY_USAGE_DATE, today)
            .apply()
    }
}

private fun getActiveSessionElapsedMs(
    context: Context,
    packageName: String,
    nowMs: Long = System.currentTimeMillis()
): Long {
    resetIfNewDay(context)
    val prefs = getUsagePrefs(context)

    val activePackage = prefs.getString(KEY_ACTIVE_PACKAGE, null)
    val activeStart = prefs.getLong(KEY_ACTIVE_START_MS, -1L)

    if (activePackage != packageName || activeStart <= 0L || nowMs <= activeStart) {
        return 0L
    }

    return (nowMs - activeStart)
        .coerceAtLeast(0L)
        .coerceAtMost(MAX_SINGLE_SESSION_MS)
}

fun startTrackedSession(
    context: Context,
    packageName: String,
    startTimeMs: Long = System.currentTimeMillis()
) {
    resetIfNewDay(context)
    val prefs = getUsagePrefs(context)

    val currentActivePackage = prefs.getString(KEY_ACTIVE_PACKAGE, null)
    val currentActiveStart = prefs.getLong(KEY_ACTIVE_START_MS, -1L)

    if (currentActivePackage == packageName && currentActiveStart > 0L) {
        return
    }

    prefs.edit()
        .putString(KEY_ACTIVE_PACKAGE, packageName)
        .putLong(KEY_ACTIVE_START_MS, startTimeMs)
        .apply()
}

fun stopTrackedSession(
    context: Context,
    stopTimeMs: Long = System.currentTimeMillis()
) {
    resetIfNewDay(context)
    val prefs = getUsagePrefs(context)

    val activePackage = prefs.getString(KEY_ACTIVE_PACKAGE, null)
    val activeStart = prefs.getLong(KEY_ACTIVE_START_MS, -1L)

    if (activePackage == null || activeStart <= 0L) {
        prefs.edit()
            .remove(KEY_ACTIVE_PACKAGE)
            .remove(KEY_ACTIVE_START_MS)
            .apply()
        return
    }

    if (stopTimeMs <= activeStart) {
        prefs.edit()
            .remove(KEY_ACTIVE_PACKAGE)
            .remove(KEY_ACTIVE_START_MS)
            .apply()
        return
    }

    val elapsedMs = (stopTimeMs - activeStart)
        .coerceAtLeast(0L)
        .coerceAtMost(MAX_SINGLE_SESSION_MS)

    val key = packageMillisKey(activePackage)
    val currentMillis = prefs.getLong(key, 0L)

    prefs.edit()
        .putLong(key, currentMillis + elapsedMs)
        .remove(KEY_ACTIVE_PACKAGE)
        .remove(KEY_ACTIVE_START_MS)
        .apply()
}

fun switchTrackedSession(
    context: Context,
    newPackageName: String,
    switchTimeMs: Long = System.currentTimeMillis()
) {
    resetIfNewDay(context)
    val prefs = getUsagePrefs(context)
    val activePackage = prefs.getString(KEY_ACTIVE_PACKAGE, null)

    if (activePackage == newPackageName) return

    stopTrackedSession(context, switchTimeMs)
    startTrackedSession(context, newPackageName, switchTimeMs)
}

fun getTrackedUsageMillisToday(
    context: Context,
    packageName: String
): Long {
    resetIfNewDay(context)
    val prefs = getUsagePrefs(context)
    val storedMillis = prefs.getLong(packageMillisKey(packageName), 0L)

    return storedMillis + getActiveSessionElapsedMs(context, packageName)
}

fun getTrackedUsageMinutesToday(
    context: Context,
    packageName: String
): Int {
    return (getTrackedUsageMillisToday(context, packageName) / 1000L / 60L).toInt()
}

fun getTrackedUsageSummaryToday(
    context: Context,
    targetPackages: Set<String>
): List<AppUsageEntry> {
    resetIfNewDay(context)

    return targetPackages
        .map { pkg ->
            AppUsageEntry(
                packageName = pkg,
                minutesToday = getTrackedUsageMinutesToday(context, pkg)
            )
        }
        .sortedByDescending { it.minutesToday }
}

fun getTrackedUsageTotalMinutesToday(
    context: Context,
    targetPackages: Set<String>
): Int {
    return getTrackedUsageSummaryToday(context, targetPackages)
        .sumOf { it.minutesToday }
}

fun getActiveTrackedPackage(context: Context): String? {
    resetIfNewDay(context)
    return getUsagePrefs(context).getString(KEY_ACTIVE_PACKAGE, null)
}
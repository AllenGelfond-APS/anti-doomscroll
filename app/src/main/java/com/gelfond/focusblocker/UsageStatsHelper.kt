package com.gelfond.focusblocker

import android.content.Context

data class AppUsageEntry(
    val packageName: String,
    val minutesToday: Int
)

fun getTodayUsageMinutes(
    context: Context,
    targetPackages: Set<String>
): Int {
    return getTrackedUsageTotalMinutesToday(context, targetPackages)
}

fun getTodayUsageSummary(
    context: Context,
    targetPackages: Set<String>
): List<AppUsageEntry> {
    return getTrackedUsageSummaryToday(context, targetPackages)
}
package com.gelfond.focusblocker

import android.content.Context
import android.util.Log
import java.time.LocalDate

private const val PREFS_NAME = "focus_blocker_prefs"
private const val KEY_OVERRIDE_DATE = "override_date"
private const val KEY_OVERRIDE_COUNT = "override_count"

private const val KEY_LIMIT_DATE = "limit_date"
private const val KEY_TODAY_LIMIT = "today_limit"
private const val KEY_NEXT_DAY_LIMIT = "next_day_limit"

private const val KEY_UNLOCKED_PACKAGE = "unlocked_package"
private const val KEY_UNLOCK_UNTIL_MS = "unlock_until_ms"
private const val KEY_UNLOCK_LEFT_AT_MS = "unlock_left_at_ms"

private const val MIN_LIMIT_MINUTES = 15
private const val TAG = "FocusBlocker"

private val unlockDurationMs: Long
    get() = if (DebugConfig.DEBUG_MODE) {
        DebugConfig.TEMP_UNLOCK_DURATION_MS
    } else {
        10 * 60 * 1000L
    }

private val returnGracePeriodMs: Long
    get() = if (DebugConfig.DEBUG_MODE) {
        DebugConfig.RETURN_GRACE_PERIOD_MS
    } else {
        60 * 1000L
    }

private val shrunkUnlockMs: Long
    get() = if (DebugConfig.DEBUG_MODE) {
        DebugConfig.SHRUNK_UNLOCK_MS
    } else {
        30 * 1000L
    }

private fun todayString(): String = LocalDate.now().toString()

private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun calculateNextDayLimitFromOverrideCount(
    overrideCount: Int,
    nextDay: LocalDate = LocalDate.now().plusDays(1)
): Int {
    val baseLimit = defaultLimitForDate(nextDay)
    val penalty = overrideCount * 10
    return (baseLimit - penalty).coerceAtLeast(MIN_LIMIT_MINUTES)
}

fun resetOverrideStateIfNewDay(context: Context) {
    val prefs = prefs(context)
    val today = todayString()
    val savedOverrideDate = prefs.getString(KEY_OVERRIDE_DATE, null)
    val savedLimitDate = prefs.getString(KEY_LIMIT_DATE, null)

    val editor = prefs.edit()

    if (savedOverrideDate != today) {
        Log.d(TAG, "Resetting override count for new day: $today")
        editor.putString(KEY_OVERRIDE_DATE, today)
        editor.putInt(KEY_OVERRIDE_COUNT, 0)
    }

    if (savedLimitDate != today) {
        val todaysLimit = if (prefs.contains(KEY_NEXT_DAY_LIMIT)) {
            prefs.getInt(KEY_NEXT_DAY_LIMIT, defaultLimitForDate(LocalDate.now()))
        } else {
            defaultLimitForDate(LocalDate.now())
        }

        Log.d(TAG, "Applying today's limit: $todaysLimit for date: $today")
        editor.putString(KEY_LIMIT_DATE, today)
        editor.putInt(KEY_TODAY_LIMIT, todaysLimit)
        editor.remove(KEY_NEXT_DAY_LIMIT)
    }

    editor.apply()
}

fun getTodayOverrideCount(context: Context): Int {
    resetOverrideStateIfNewDay(context)
    return prefs(context).getInt(KEY_OVERRIDE_COUNT, 0)
}

fun getTodayLimitMinutes(context: Context): Int {
    resetOverrideStateIfNewDay(context)
    return prefs(context).getInt(
        KEY_TODAY_LIMIT,
        defaultLimitForDate(LocalDate.now())
    )
}

fun getTomorrowLimitMinutes(context: Context): Int {
    resetOverrideStateIfNewDay(context)
    val tomorrow = LocalDate.now().plusDays(1)
    return prefs(context).getInt(KEY_NEXT_DAY_LIMIT, defaultLimitForDate(tomorrow))
}

fun registerOverrideAndUpdateTomorrowLimit(context: Context): Int {
    resetOverrideStateIfNewDay(context)
    val prefs = prefs(context)
    val newCount = prefs.getInt(KEY_OVERRIDE_COUNT, 0) + 1
    val tomorrow = LocalDate.now().plusDays(1)
    val newTomorrowLimit = calculateNextDayLimitFromOverrideCount(newCount, tomorrow)

    prefs.edit()
        .putString(KEY_OVERRIDE_DATE, todayString())
        .putInt(KEY_OVERRIDE_COUNT, newCount)
        .putInt(KEY_NEXT_DAY_LIMIT, newTomorrowLimit)
        .apply()

    Log.d(
        TAG,
        "registerOverrideAndUpdateTomorrowLimit newCount=$newCount newTomorrowLimit=$newTomorrowLimit"
    )

    return newCount
}

fun grantTemporaryUnlock(
    context: Context,
    packageName: String,
    nowMs: Long = System.currentTimeMillis()
) {
    val unlockUntil = nowMs + unlockDurationMs

    prefs(context).edit()
        .putString(KEY_UNLOCKED_PACKAGE, packageName)
        .putLong(KEY_UNLOCK_UNTIL_MS, unlockUntil)
        .remove(KEY_UNLOCK_LEFT_AT_MS)
        .apply()

    Log.d(
        TAG,
        "grantTemporaryUnlock package=$packageName now=$nowMs unlockUntil=$unlockUntil durationMs=$unlockDurationMs"
    )
}

fun getTemporarilyUnlockedPackage(
    context: Context,
    nowMs: Long = System.currentTimeMillis()
): String? {
    val prefs = prefs(context)
    val unlockUntilMs = prefs.getLong(KEY_UNLOCK_UNTIL_MS, 0L)

    if (unlockUntilMs <= nowMs) {
        if (unlockUntilMs != 0L) {
            Log.d(
                TAG,
                "getTemporarilyUnlockedPackage expired unlock detected now=$nowMs unlockUntil=$unlockUntilMs"
            )
            clearTemporaryUnlock(context)
        }
        return null
    }

    return prefs.getString(KEY_UNLOCKED_PACKAGE, null)
}

fun isPackageTemporarilyUnlocked(
    context: Context,
    packageName: String,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
    val unlockedPackage = getTemporarilyUnlockedPackage(context, nowMs)
    val remaining = getTemporaryUnlockRemainingMs(context, nowMs)
    val result = unlockedPackage == packageName && remaining > 0L

    Log.d(
        TAG,
        "isPackageTemporarilyUnlocked package=$packageName unlockedPackage=$unlockedPackage remainingMs=$remaining result=$result"
    )

    return result
}

fun getTemporaryUnlockRemainingMs(
    context: Context,
    nowMs: Long = System.currentTimeMillis()
): Long {
    val unlockUntilMs = prefs(context).getLong(KEY_UNLOCK_UNTIL_MS, 0L)
    val remaining = unlockUntilMs - nowMs

    if (remaining <= 0L) {
        if (unlockUntilMs != 0L) {
            Log.d(
                TAG,
                "getTemporaryUnlockRemainingMs clearing expired unlock now=$nowMs unlockUntil=$unlockUntilMs"
            )
            clearTemporaryUnlock(context)
        }
        return 0L
    }

    return remaining
}

fun markTemporaryUnlockLeft(
    context: Context,
    packageName: String,
    nowMs: Long = System.currentTimeMillis()
) {
    val prefs = prefs(context)
    val unlockedPackage = prefs.getString(KEY_UNLOCKED_PACKAGE, null) ?: return
    val unlockUntilMs = prefs.getLong(KEY_UNLOCK_UNTIL_MS, 0L)
    val existingLeftAtMs = prefs.getLong(KEY_UNLOCK_LEFT_AT_MS, 0L)

    if (unlockedPackage != packageName) return

    if (unlockUntilMs <= nowMs) {
        Log.d(
            TAG,
            "markTemporaryUnlockLeft found expired unlock package=$packageName now=$nowMs unlockUntil=$unlockUntilMs"
        )
        clearTemporaryUnlock(context)
        return
    }

    if (existingLeftAtMs > 0L) {
        Log.d(
            TAG,
            "markTemporaryUnlockLeft already marked package=$packageName existingLeftAt=$existingLeftAtMs"
        )
        return
    }

    prefs.edit()
        .putLong(KEY_UNLOCK_LEFT_AT_MS, nowMs)
        .apply()

    Log.d(
        TAG,
        "markTemporaryUnlockLeft package=$packageName leftAt=$nowMs"
    )
}

fun markTemporaryUnlockReturned(
    context: Context,
    packageName: String
) {
    val prefs = prefs(context)
    val unlockedPackage = prefs.getString(KEY_UNLOCKED_PACKAGE, null) ?: return
    val leftAtMs = prefs.getLong(KEY_UNLOCK_LEFT_AT_MS, 0L)

    if (unlockedPackage != packageName) return

    if (leftAtMs <= 0L) {
        Log.d(
            TAG,
            "markTemporaryUnlockReturned package=$packageName no pending leftAt marker"
        )
        return
    }

    prefs.edit()
        .remove(KEY_UNLOCK_LEFT_AT_MS)
        .apply()

    Log.d(
        TAG,
        "markTemporaryUnlockReturned package=$packageName clearedLeftAt=$leftAtMs"
    )
}

fun maybeShrinkTemporaryUnlockAfterLeaving(
    context: Context,
    nowMs: Long = System.currentTimeMillis()
) {
    val prefs = prefs(context)

    val unlockedPackage = prefs.getString(KEY_UNLOCKED_PACKAGE, null) ?: return
    val unlockUntilMs = prefs.getLong(KEY_UNLOCK_UNTIL_MS, 0L)
    val leftAtMs = prefs.getLong(KEY_UNLOCK_LEFT_AT_MS, 0L)

    if (unlockUntilMs <= nowMs) {
        Log.d(
            TAG,
            "maybeShrinkTemporaryUnlockAfterLeaving found expired unlock package=$unlockedPackage now=$nowMs unlockUntil=$unlockUntilMs"
        )
        clearTemporaryUnlock(context)
        return
    }

    if (leftAtMs <= 0L) {
        return
    }

    val awayDuration = nowMs - leftAtMs
    val remaining = unlockUntilMs - nowMs

    Log.d(
        TAG,
        "maybeShrinkTemporaryUnlockAfterLeaving package=$unlockedPackage leftAt=$leftAtMs now=$nowMs awayDuration=$awayDuration remaining=$remaining"
    )

    if (awayDuration < returnGracePeriodMs) {
        return
    }

    if (remaining <= shrunkUnlockMs) {
        Log.d(
            TAG,
            "maybeShrinkTemporaryUnlockAfterLeaving no shrink needed package=$unlockedPackage remaining=$remaining"
        )
        return
    }

    val newUnlockUntil = nowMs + shrunkUnlockMs

    prefs.edit()
        .putLong(KEY_UNLOCK_UNTIL_MS, newUnlockUntil)
        .apply()

    Log.d(
        TAG,
        "maybeShrinkTemporaryUnlockAfterLeaving SHRUNK package=$unlockedPackage oldUnlockUntil=$unlockUntilMs newUnlockUntil=$newUnlockUntil shrunkUnlockMs=$shrunkUnlockMs"
    )
}

fun clearTemporaryUnlock(context: Context) {
    val prefs = prefs(context)
    val unlockedPackage = prefs.getString(KEY_UNLOCKED_PACKAGE, null)
    val unlockUntilMs = prefs.getLong(KEY_UNLOCK_UNTIL_MS, 0L)
    val leftAtMs = prefs.getLong(KEY_UNLOCK_LEFT_AT_MS, 0L)

    Log.d(
        TAG,
        "clearTemporaryUnlock package=$unlockedPackage unlockUntil=$unlockUntilMs leftAt=$leftAtMs"
    )

    prefs.edit()
        .remove(KEY_UNLOCKED_PACKAGE)
        .remove(KEY_UNLOCK_UNTIL_MS)
        .remove(KEY_UNLOCK_LEFT_AT_MS)
        .apply()
}
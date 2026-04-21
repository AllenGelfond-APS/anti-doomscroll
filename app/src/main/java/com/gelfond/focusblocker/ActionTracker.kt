package com.gelfond.focusblocker

import android.content.Context
import java.time.LocalDate

enum class RedirectAction(val keySuffix: String) {
    READ("read"),
    BACK_TO_WORK("back_to_work"),
    EXERCISE("exercise"),
    STRETCH("stretch")
}

private const val ACTION_PREFS = "focus_blocker_action_prefs"
private const val KEY_ACTION_DATE = "action_date"

private fun actionPrefs(context: Context) =
    context.getSharedPreferences(ACTION_PREFS, Context.MODE_PRIVATE)

private fun actionCountKey(action: RedirectAction): String {
    return "count_${action.keySuffix}"
}

private fun todayActionDate(): String = LocalDate.now().toString()

private fun resetActionCountsIfNewDay(context: Context) {
    val prefs = actionPrefs(context)
    val today = todayActionDate()
    val saved = prefs.getString(KEY_ACTION_DATE, null)

    if (saved != today) {
        prefs.edit()
            .clear()
            .putString(KEY_ACTION_DATE, today)
            .apply()
    }
}

fun incrementRedirectAction(context: Context, action: RedirectAction) {
    resetActionCountsIfNewDay(context)
    val prefs = actionPrefs(context)
    val key = actionCountKey(action)
    val current = prefs.getInt(key, 0)

    prefs.edit()
        .putInt(key, current + 1)
        .apply()
}

fun getTodayRedirectActionCount(context: Context, action: RedirectAction): Int {
    resetActionCountsIfNewDay(context)
    return actionPrefs(context).getInt(actionCountKey(action), 0)
}
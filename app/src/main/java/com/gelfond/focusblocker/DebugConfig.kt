package com.gelfond.focusblocker

object DebugConfig {
    const val DEBUG_MODE = true

    const val FORCE_OVER_LIMIT = true
    const val FORCED_MINUTES = 61

    const val OVERRIDE_REQUIRED_WORDS = 5
    const val OVERRIDE_WAIT_MS = 10_000L

    const val TEMP_UNLOCK_DURATION_MS = 20_000L
    const val RETURN_GRACE_PERIOD_MS = 10_000L
    const val SHRUNK_UNLOCK_MS = 5_000L
}
package com.gelfond.focusblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusBlocker"
        private const val BLOCK_LAUNCH_DELAY_MS = 150L
        private const val BLOCK_DEBOUNCE_MS = 750L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastBlockLaunchMs = 0L

    private val blockedApps = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.reddit.frontpage"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val isRelevantEvent =
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

        if (!isRelevantEvent) return

        val packageName = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()

        maybeShrinkTemporaryUnlockAfterLeaving(this, now)
        val unlockedPackage = getTemporarilyUnlockedPackage(this, now)

        when (packageName) {
            this.packageName -> {
                if (unlockedPackage != null) {
                    markTemporaryUnlockLeft(this, unlockedPackage, now)
                }
                stopTrackedSession(this, now)
            }

            in blockedApps -> {
                if (unlockedPackage != null && unlockedPackage != packageName) {
                    markTemporaryUnlockLeft(this, unlockedPackage, now)
                }
                handleTrackedAppForeground(packageName, now)
            }

            else -> {
                if (unlockedPackage != null) {
                    markTemporaryUnlockLeft(this, unlockedPackage, now)
                }
                stopTrackedSession(this, now)
            }
        }
    }

    private fun handleTrackedAppForeground(packageName: String, now: Long) {
        switchTrackedSession(this, packageName, now)

        if (isPackageTemporarilyUnlocked(this, packageName, now)) {
            markTemporaryUnlockReturned(this, packageName)
            Log.d(TAG, "Temporary unlock active for $packageName")
            return
        }

        val accumulatedMinutes = if (
            DebugConfig.DEBUG_MODE && DebugConfig.FORCE_OVER_LIMIT
        ) {
            DebugConfig.FORCED_MINUTES
        } else {
            getTodayUsageMinutes(this, blockedApps)
        }

        val todaysLimit = getTodayLimitMinutes(this)

        val decision = computeBlockDecision(
            accumulatedMinutes = accumulatedMinutes,
            todaysLimitMinutes = todaysLimit
        )

        Log.d(
            TAG,
            "Tracked app foreground: $packageName | total_min: $accumulatedMinutes | limit: $todaysLimit | block: ${decision.shouldBlock} | reason: ${decision.reason}"
        )

        if (decision.shouldBlock) {
            launchBlockFlow(
                blockedPackage = packageName,
                decision = decision,
                now = now
            )
        }
    }

    private fun launchBlockFlow(
        blockedPackage: String,
        decision: BlockDecision,
        now: Long
    ) {
        if (now - lastBlockLaunchMs < BLOCK_DEBOUNCE_MS) {
            Log.d(TAG, "Skipping duplicate block launch for $blockedPackage")
            return
        }

        lastBlockLaunchMs = now

        performGlobalAction(GLOBAL_ACTION_HOME)

        mainHandler.postDelayed({
            launchBlockerActivity(
                blockedPackage = blockedPackage,
                decision = decision
            )
        }, BLOCK_LAUNCH_DELAY_MS)
    }

    private fun launchBlockerActivity(
        blockedPackage: String,
        decision: BlockDecision
    ) {
        val intent = Intent(this, BlockerActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra("blocked_package", blockedPackage)
            putExtra("accumulated_minutes", decision.accumulatedMinutes)
            putExtra("todays_limit_minutes", decision.todaysLimitMinutes)
            putExtra("block_message", decision.message)
            putExtra("block_reason", decision.reason?.name)
        }

        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
}
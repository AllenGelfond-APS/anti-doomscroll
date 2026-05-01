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
        private const val BLOCK_LAUNCH_DELAY_MS = 400L
        private const val BLOCK_DEBOUNCE_MS = 750L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastBlockLaunchMs = 0L
    private var blockFlowInProgress = false

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

                // critical: allow future blocks after we reach our own UI
                blockFlowInProgress = false
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
            launchBlockFlow(packageName, decision, now)
        }
    }

    private fun launchBlockFlow(
        blockedPackage: String,
        decision: BlockDecision,
        now: Long
    ) {
        if (blockFlowInProgress) {
            Log.d(TAG, "Block flow already in progress; ignoring $blockedPackage")
            return
        }

        if (now - lastBlockLaunchMs < BLOCK_DEBOUNCE_MS) {
            Log.d(TAG, "Skipping duplicate block launch for $blockedPackage")
            return
        }

        blockFlowInProgress = true
        lastBlockLaunchMs = now

        // kick user out of the app
        performGlobalAction(GLOBAL_ACTION_HOME)

        // delay prevents race condition where HOME closes your blocker
        mainHandler.postDelayed({
            launchBlockerActivity(blockedPackage, decision)
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
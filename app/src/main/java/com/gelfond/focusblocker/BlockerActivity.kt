package com.gelfond.focusblocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class BlockerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedPackage = intent.getStringExtra("blocked_package").orEmpty()
        val accumulatedMinutes = intent.getIntExtra("accumulated_minutes", 0)
        val todaysLimitMinutes = intent.getIntExtra("todays_limit_minutes", 60)
        val blockMessage = intent.getStringExtra("block_message") ?: "Blocked"

        setContent {
            FocusBlockerDarkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BlockerScreen(
                        blockedPackage = blockedPackage,
                        accumulatedMinutes = accumulatedMinutes,
                        todaysLimitMinutes = todaysLimitMinutes,
                        blockMessage = blockMessage,
                        onUnlock = { handleUnlock(blockedPackage) },
                        onExit = { goHomeAndFinish() },
                        onDashboard = { openDashboard() }
                    )
                }
            }
        }
    }

    private fun handleUnlock(blockedPackage: String) {
        registerOverrideAndUpdateTomorrowLimit(this)
        grantTemporaryUnlock(this, blockedPackage)
        goHomeAndFinish()
    }

    private fun openDashboard() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_dashboard", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun goHomeAndFinish() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun BlockerScreen(
    blockedPackage: String,
    accumulatedMinutes: Int,
    todaysLimitMinutes: Int,
    blockMessage: String,
    onUnlock: () -> Unit,
    onExit: () -> Unit,
    onDashboard: () -> Unit
) {
    val context = LocalContext.current
    val overrideCount = getTodayOverrideCount(context)

    val requiredWords = if (DebugConfig.DEBUG_MODE) {
        DebugConfig.OVERRIDE_REQUIRED_WORDS
    } else {
        50
    }

    val requiredWaitMs = if (DebugConfig.DEBUG_MODE) {
        DebugConfig.OVERRIDE_WAIT_MS
    } else {
        5 * 60 * 1000L
    }

    val isFirstOverrideToday = overrideCount == 0

    var showOverrideEntry by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf<Long?>(null) }
    var timeRemaining by remember { mutableLongStateOf(requiredWaitMs) }

    val disabledTextToolbar = object : TextToolbar {
        override val status: TextToolbarStatus
            get() = TextToolbarStatus.Hidden

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?
        ) {
        }

        override fun hide() {
        }
    }

    val wordCount = text
        .trim()
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }
        .size

    LaunchedEffect(wordCount, showOverrideEntry, overrideCount) {
        if (showOverrideEntry && overrideCount > 0 && wordCount > 0 && startTime == null) {
            startTime = System.currentTimeMillis()
        }
    }

    LaunchedEffect(startTime, requiredWaitMs, showOverrideEntry) {
        while (showOverrideEntry && startTime != null) {
            val elapsed = System.currentTimeMillis() - startTime!!
            val remaining = requiredWaitMs - elapsed
            timeRemaining = remaining.coerceAtLeast(0L)

            if (remaining <= 0L) break
            delay(1000)
        }
    }

    val timerFinished = timeRemaining <= 0L
    val canUnlock = isFirstOverrideToday || (wordCount >= requiredWords && timerFinished)

    if (showOverrideEntry && !isFirstOverrideToday) {
        OverrideEntryScreen(
            blockedPackage = blockedPackage,
            requiredWords = requiredWords,
            wordCount = wordCount,
            timeRemaining = timeRemaining,
            text = text,
            onTextChange = { newText ->
                if (newText.length - text.length <= 2) {
                    text = newText
                }
            },
            onUnlock = onUnlock,
            onExit = onExit,
            canUnlock = canUnlock,
            disabledTextToolbar = disabledTextToolbar
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        OutlinedButton(
            onClick = onDashboard,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text("Dashboard")
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BLOCKED",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = friendlyAppName(blockedPackage),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = blockMessage,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Today: $accumulatedMinutes / $todaysLimitMinutes min")
            Text("Overrides today: $overrideCount")

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exit")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isFirstOverrideToday) {
                OutlinedButton(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Free override")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        showOverrideEntry = true
                        text = ""
                        startTime = null
                        timeRemaining = requiredWaitMs
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Override")
                }
            }
        }
    }
}

@Composable
fun OverrideEntryScreen(
    blockedPackage: String,
    requiredWords: Int,
    wordCount: Int,
    timeRemaining: Long,
    text: String,
    onTextChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onExit: () -> Unit,
    canUnlock: Boolean,
    disabledTextToolbar: TextToolbar
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Override",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "App: ${friendlyAppName(blockedPackage)}",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Type $requiredWords words and wait to unlock.")

        Spacer(modifier = Modifier.height(16.dp))

        CompositionLocalProvider(
            LocalTextToolbar provides disabledTextToolbar
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Explain why you're overriding...") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text("Words: $wordCount / $requiredWords")

        Spacer(modifier = Modifier.height(8.dp))

        Text("Wait: ${formatRemainingTime(timeRemaining)}")

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exit")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onUnlock,
            enabled = canUnlock,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock")
        }
    }
}
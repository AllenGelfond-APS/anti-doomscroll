package com.gelfond.focusblocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val openDashboard = intent.getBooleanExtra("open_dashboard", false)

        setContent {
            FocusBlockerDarkTheme {
                MainScreen(
                    initialShowDashboard = openDashboard
                )
            }
        }
    }
}

@Composable
fun FocusBlockerDarkTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme()
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}

@Composable
fun MainScreen(
    initialShowDashboard: Boolean = false
) {
    val context = LocalContext.current

    var accessibilityEnabled by remember {
        mutableStateOf(isFocusAccessibilityServiceEnabled(context))
    }

    var hasOpenedAccessibilitySettings by remember {
        mutableStateOf(false)
    }

    var showDashboard by remember {
        mutableStateOf(initialShowDashboard)
    }

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = isFocusAccessibilityServiceEnabled(context)
            delay(1_000)
        }
    }

    LaunchedEffect(accessibilityEnabled) {
        if (!accessibilityEnabled && !hasOpenedAccessibilitySettings) {
            hasOpenedAccessibilitySettings = true
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!accessibilityEnabled) {
            AccessibilityRequiredScreen()
        } else if (showDashboard) {
            UsageDashboardScreen(
                onBack = { showDashboard = false }
            )
        } else {
            HomeScreen(
                onOpenDashboard = { showDashboard = true }
            )
        }
    }
}

@Composable
fun AccessibilityRequiredScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Accessibility Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "FocusBlocker cannot block apps unless its Accessibility Service is turned on."
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Accessibility Settings")
            }
        }
    }
}

fun isFocusAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(
        context,
        FocusAccessibilityService::class.java
    )

    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)

    for (enabledService in splitter) {
        val enabledComponentName =
            ComponentName.unflattenFromString(enabledService)

        if (enabledComponentName == expectedComponentName) {
            return true
        }
    }

    return false
}

@Composable
fun HomeScreen(
    onOpenDashboard: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Doomscrolling is Bad!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = onOpenDashboard) {
                Text("Open Dashboard")
            }
        }
    }
}

@Composable
fun UsageDashboardScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val blockedApps = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.reddit.frontpage"
    )

    var usageEntries by remember { mutableStateOf(emptyList<AppUsageEntry>()) }
    var totalMinutes by remember { mutableIntStateOf(0) }
    var todaysLimit by remember { mutableIntStateOf(60) }
    var shouldBlockNow by remember { mutableStateOf(false) }
    var blockMessage by remember { mutableStateOf("No block.") }
    var todayOverrideCount by remember { mutableIntStateOf(0) }
    var tomorrowLimit by remember { mutableIntStateOf(60) }
    var unlockRemainingMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            usageEntries = getTodayUsageSummary(context, blockedApps)
            totalMinutes = usageEntries.sumOf { it.minutesToday }
            todaysLimit = getTodayLimitMinutes(context)
            todayOverrideCount = getTodayOverrideCount(context)
            tomorrowLimit = getTomorrowLimitMinutes(context)
            unlockRemainingMs = getTemporaryUnlockRemainingMs(context)

            val decision = computeBlockDecision(
                accumulatedMinutes = totalMinutes,
                todaysLimitMinutes = todaysLimit
            )

            shouldBlockNow = decision.shouldBlock
            blockMessage = decision.message

            delay(5_000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 40.dp,
                bottom = 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            CompactDashboardCard(
                title = "Today",
                rows = listOf(
                    "Tracked use" to "$totalMinutes min",
                    "Limit" to "$todaysLimit min",
                    "Remaining" to "${(todaysLimit - totalMinutes).coerceAtLeast(0)} min",
                    "Overrides" to todayOverrideCount.toString()
                )
            )
        }

        item {
            CompactDashboardCard(
                title = "Block status",
                rows = buildList {
                    add("Currently blocking" to if (shouldBlockNow) "Yes" else "No")
                    add("Reason" to blockMessage)
                    if (unlockRemainingMs > 0L) {
                        add("Temporary unlock" to formatRemainingTime(unlockRemainingMs))
                    }
                }
            )
        }

        item {
            CompactDashboardCard(
                title = "Tomorrow",
                rows = listOf(
                    "Limit" to "$tomorrowLimit min"
                )
            )
        }

        item {
            CompactDashboardCard(
                title = "Apps",
                rows = usageEntries.map {
                    friendlyAppName(it.packageName) to "${it.minutesToday} min"
                }
            )
        }

        item {
            Text(
                text = "Don't doomscroll dummy!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CompactDashboardCard(
    title: String,
    rows: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            rows.forEach { (label, value) ->
                CompactStatRow(label = label, value = value)
            }
        }
    }
}

@Composable
fun CompactStatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatRemainingTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

fun friendlyAppName(packageName: String): String {
    return when (packageName) {
        "com.instagram.android" -> "Instagram"
        "com.google.android.youtube" -> "YouTube"
        "com.reddit.frontpage" -> "Reddit"
        "None" -> "None"
        else -> packageName
    }
}
package cz.ioff.app.ui.screens

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.ioff.app.AttentionEngine
import cz.ioff.app.AttentionSnapshot
import cz.ioff.app.UsageMetrics
import cz.ioff.app.data.IOffRepository
import cz.ioff.app.system.SettingsNavigator
import cz.ioff.app.ui.components.*
import cz.ioff.app.ui.theme.*

@Composable
fun ProgressScreen(repository: IOffRepository) {
    var section by remember { mutableIntStateOf(1) }
    val sessions = repository.total("sessions")
    val focusAverage = if (sessions == 0) 0.0 else repository.total("focusSum").toDouble() / sessions
    val score = AttentionEngine.score(
        AttentionSnapshot(repository.total("mins"), focusAverage, repository.total("urges"), repository.total("shield"), repository.total("bypasses"), repository.total("distracting"))
    )
    IOffScreen(title = "Progress") {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).background(IOffSurface, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Overview", "Insights", "Trends").forEachIndexed { index, label ->
                Surface(
                    onClick = { section = index },
                    modifier = Modifier.weight(1f),
                    color = if (section == index) IOffGreen else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (section == index) IOffBackground else IOffText,
                    shape = RoundedCornerShape(12.dp)
                ) { Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 7.dp)) }
            }
        }
        when (section) {
            0 -> ProgressOverview(repository, score)
            1 -> ProgressInsights(repository)
            else -> ProgressTrends(repository)
        }
    }
}

@Composable
private fun ProgressOverview(repository: IOffRepository, score: Int) {
    IOffSectionTitle("Attention Score")
    IOffCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IOffCircularProgress(score / 100f, Modifier.size(100.dp), strokeWidth = 10f) { Text("$score", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(start = 18.dp)) {
                Text(formatDuration(repository.total("mins")), style = MaterialTheme.typography.headlineMedium)
                Text("deep work across 7 days", color = IOffMuted)
            }
        }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IOffMetricCard("App opens", "${repository.total("opens")}", Modifier.weight(1f))
        IOffMetricCard("Distractions", formatDuration(repository.total("distracting")), Modifier.weight(1f))
    }
}

@Composable
private fun ProgressInsights(repository: IOffRepository) {
    IOffSectionTitle("💡  Your Attention Insights")
    val insights = listOf(
        Triple(Icons.Outlined.Schedule, "90-minute sessions", "give you the best chance of sustained focus."),
        Triple(Icons.Outlined.ShowChart, "Most distractions", "happen after ${repository.total("distracting").coerceAtLeast(17)} minutes of scattered use."),
        Triple(Icons.Outlined.WbSunny, "Protected mornings", "support deeper work before social apps open."),
        Triple(Icons.Outlined.EmojiEvents, "Movement helps", "on active days, finishing your One Thing is easier.")
    )
    IOffCard {
        insights.forEachIndexed { index, insight ->
            IOffListRow(insight.first, iconColor = if (index == 3) IOffYellow else IOffGreen, title = insight.second, subtitle = insight.third, onClick = {})
            if (index < insights.lastIndex) HorizontalDivider(color = IOffBorder)
        }
    }
}

@Composable
private fun ProgressTrends(repository: IOffRepository) {
    IOffSectionTitle("7-day experiment")
    (1..7).forEach { day ->
        IOffCard(Modifier.padding(bottom = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Day $day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(52.dp))
                LinearProgressIndicator(
                    progress = { (repository.metric("mins", day) / 120f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(7.dp),
                    color = IOffGreen,
                    trackColor = IOffBorder
                )
                Text("${repository.metric("mins", day)}m", color = IOffMuted, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
fun MoreScreen(
    repository: IOffRepository,
    settingsNavigator: SettingsNavigator,
    dndReady: Boolean,
    onHealth: () -> Unit,
    onShutdown: () -> Unit,
    onDataReset: () -> Unit
) {
    val context = LocalContext.current
    var morning by remember { mutableStateOf(repository.morningShieldEnabled()) }
    var shield by remember { mutableStateOf(repository.shieldEnabled()) }
    var resetConfirm by remember { mutableStateOf(false) }
    val usageReady = UsageMetrics.hasAccess(context)
    val accessibilityReady = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().contains(context.packageName)
    val notificationReady = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty().contains(context.packageName)

    IOffScreen(title = "More") {
        IOffSectionTitle("Life")
        IOffCard {
            IOffListRow(Icons.Outlined.FavoriteBorder, title = "Health & Lifestyle", subtitle = "Commitments, sport and sex", onClick = onHealth)
            HorizontalDivider(color = IOffBorder)
            IOffListRow(Icons.Outlined.NightsStay, title = "Daily Shutdown", subtitle = if (repository.shutdown().completed) "Completed today" else "1 minute reflection", onClick = onShutdown)
        }
        IOffSectionTitle("Protection")
        IOffCard {
            AccessRow("Do Not Disturb", dndReady, settingsNavigator::openDoNotDisturbAccess)
            HorizontalDivider(color = IOffBorder)
            AccessRow("Focus Shield", accessibilityReady, settingsNavigator::openAccessibilityAccess)
            HorizontalDivider(color = IOffBorder)
            AccessRow("Usage Access", usageReady, settingsNavigator::openUsageAccess)
            HorizontalDivider(color = IOffBorder)
            AccessRow("Notification Access", notificationReady, settingsNavigator::openNotificationAccess)
        }
        IOffSectionTitle("Automation")
        IOffCard {
            ToggleRow("Focus Shield", "Block distractions during focus", shield) { shield = it; repository.setShieldEnabled(it) }
            HorizontalDivider(color = IOffBorder)
            ToggleRow("Morning Protection", "06:00–09:00", morning) { morning = it; repository.setMorningShieldEnabled(it) }
        }
        IOffSectionTitle("Testing")
        IOffCard(onClick = { resetConfirm = true }) {
            IOffListRow(Icons.Outlined.RestartAlt, iconColor = IOffRed, title = "Reset all data", subtitle = "Focus history, ideas and daily tracking")
        }
    }
    if (resetConfirm) AlertDialog(
        onDismissRequest = { resetConfirm = false },
        title = { Text("Reset all data?") },
        text = { Text("This cannot be undone.") },
        confirmButton = { TextButton(onClick = { repository.resetAll(); resetConfirm = false; onDataReset() }) { Text("Reset", color = IOffRed) } },
        dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("Cancel") } }
    )
}

@Composable
private fun AccessRow(title: String, ready: Boolean, onClick: () -> Unit) {
    IOffListRow(
        Icons.Outlined.AdminPanelSettings,
        iconColor = if (ready) IOffGreen else IOffYellow,
        title = title,
        subtitle = if (ready) "Ready" else "Needs access",
        trailing = { Icon(if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight, null, tint = if (ready) IOffGreen else IOffYellow) },
        onClick = onClick
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    IOffListRow(Icons.Outlined.Shield, title = title, subtitle = subtitle, trailing = { Switch(checked, onChange) })
}

@Composable
fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(IOffGreenDeep.copy(alpha = .75f), IOffBackground), radius = 850f)
        ).windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("i◉ff", color = IOffGreen, fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = (-4).sp)
            Text(".life", color = IOffGreen, fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
            Spacer(Modifier.height(48.dp))
            Text("Less Noise.\nMore Life.", textAlign = TextAlign.Center, fontSize = 22.sp, lineHeight = 31.sp)
            Spacer(Modifier.height(82.dp))
            Text("Focus  ·  Health  ·  Progress", color = IOffMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun ShieldScreen(appName: String = "Instagram", goal: String = "Finish DAW proposal", remainingMinutes: Int = 43, onReturn: () -> Unit = {}, onOverride: () -> Unit = {}) {
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(IOffRed.copy(alpha = .14f), IOffBackground), radius = 900f)).windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = IOffRed.copy(alpha = .12f), shape = CircleShape, modifier = Modifier.size(82.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PhoneAndroid, null, tint = IOffRed, modifier = Modifier.size(42.dp)) }
            }
            Text("Stay Focused", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 20.dp))
            Text("You chose to finish:\n$goal", textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
            IOffCard(Modifier.padding(top = 18.dp)) {
                Text("$remainingMinutes min left", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("“Distraction now\nis a longer tomorrow.”", color = IOffMuted, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))
            }
            IOffPrimaryButton("Return to iOff", Modifier.padding(top = 34.dp), onClick = onReturn)
            TextButton(onClick = onOverride, modifier = Modifier.padding(top = 12.dp)) { Text("I really need to open $appName", color = IOffText) }
            Text("3 overrides left today", color = IOffMuted, fontSize = 12.sp)
        }
    }
}

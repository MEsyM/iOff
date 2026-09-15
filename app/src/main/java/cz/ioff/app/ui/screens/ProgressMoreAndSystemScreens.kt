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
            Modifier.fillMaxWidth().padding(top = 4.dp).background(IOffSurface, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Overview", "Insights", "Trends").forEachIndexed { index, label ->
                Surface(
                    onClick = { section = index },
                    modifier = Modifier.weight(1f),
                    color = if (section == index) IOffGreen else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (section == index) IOffBackground else IOffText,
                    shape = RoundedCornerShape(12.dp)
                ) { Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp)) }
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
        Triple(Icons.Outlined.Schedule, IOffGreen, Pair("90-minute sessions", "give you 23% better focus than 60-minute sessions.")),
        Triple(Icons.Outlined.ShowChart, IOffBlue, Pair("Most distractions", "happen 17–25 min after starting.")),
        Triple(Icons.Outlined.WbSunny, IOffYellow, Pair("Mornings without social apps", "you average +41 min deep work.")),
        Triple(Icons.Outlined.EmojiEvents, IOffYellow, Pair("You are 3.2x more likely", "to finish your One Thing on days you exercise."))
    )
    insights.forEachIndexed { index, insight ->
        IOffCard(Modifier.padding(bottom = if (index == insights.lastIndex) 0.dp else 8.dp), onClick = {}) {
            IOffListRow(
                icon = insight.first,
                iconColor = insight.second,
                title = insight.third.first,
                subtitle = insight.third.second,
                onClick = {}
            )
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
            Brush.radialGradient(listOf(IOffGreenDeep.copy(alpha = .82f), IOffBackground), radius = 850f)
        ).windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("iOff", color = IOffGreen, fontSize = 66.sp, fontWeight = FontWeight.Black, letterSpacing = (-4).sp)
            Spacer(Modifier.height(44.dp))
            Text("Less Noise.\nMore Life.", textAlign = TextAlign.Center, fontSize = 20.sp, lineHeight = 28.sp)
            Spacer(Modifier.height(78.dp))
            Box(Modifier.width(46.dp).height(2.dp).background(IOffGreen, RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(34.dp))
            Text("Focus  ·  Health  ·  Progress", color = IOffMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun ShieldScreen(appName: String = "Instagram", goal: String = "Finish DAW proposal", remainingMinutes: Int = 43, onReturn: () -> Unit = {}, onOverride: () -> Unit = {}) {
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(IOffRed.copy(alpha = .18f), IOffBackground), radius = 900f)).windowInsetsPadding(WindowInsets.systemBars).padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = IOffRed.copy(alpha = .12f), shape = CircleShape, modifier = Modifier.size(82.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PhoneAndroid, null, tint = IOffMuted, modifier = Modifier.size(38.dp))
                    Icon(Icons.Outlined.Block, null, tint = IOffRed, modifier = Modifier.size(64.dp))
                }
            }
            Text("Stay Focused", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 20.dp))
            Text("You chose to finish:\n$goal", textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            IOffCard(Modifier.padding(top = 18.dp)) {
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = IOffGreen, modifier = Modifier.size(20.dp))
                    Text("$remainingMinutes min left", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp))
                }
                Text("“Distraction now\nis a longer tomorrow.”", color = IOffMuted, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))
            }
            IOffPrimaryButton("Return to iOff", Modifier.padding(top = 30.dp), onClick = onReturn)
            TextButton(onClick = onOverride, modifier = Modifier.padding(top = 10.dp)) { Text("I really need to open this", color = IOffText) }
            Text("3 overrides left today", color = IOffMuted, fontSize = 12.sp)
        }
    }
}

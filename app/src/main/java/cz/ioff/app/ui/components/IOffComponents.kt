package cz.ioff.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.ioff.app.ui.theme.*

enum class MainTab(val title: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.Today),
    FOCUS("Focus", Icons.Outlined.Timer),
    IDEAS("Ideas", Icons.Outlined.Lightbulb),
    PROGRESS("Progress", Icons.Outlined.BarChart),
    MORE("More", Icons.Outlined.Menu)
}

@Composable
fun IOffTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    brand: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            onBack != null -> {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "Back", tint = IOffText)
                }
                Text(title.orEmpty(), style = MaterialTheme.typography.titleLarge)
            }
            brand -> Text("iOff", color = IOffGreen, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.1).sp)
            title != null -> Text(title, style = MaterialTheme.typography.titleLarge)
            else -> Text("iOff", color = IOffGreen, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.1).sp)
        }
        Spacer(Modifier.weight(1f))
        if (brand && title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = IOffMuted, style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Outlined.CalendarMonth, null, tint = IOffGreen, modifier = Modifier.padding(start = 8.dp).size(17.dp))
            }
        }
        action?.invoke()
    }
}

@Composable
fun IOffScreen(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    brand: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = Modifier
        .fillMaxSize()
        .background(IOffBackground)
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(horizontal = 16.dp)
    Column(if (scroll) base.verticalScroll(rememberScrollState()) else base) {
        IOffTopBar(title, onBack, action, brand)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun IOffCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val click = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Surface(
        modifier = modifier.fillMaxWidth().then(click),
        color = IOffSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, IOffBorder)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun IOffPrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IOffGreen,
            contentColor = IOffBackground,
            disabledContainerColor = IOffBorder,
            disabledContentColor = IOffMuted
        )
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun IOffBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = Color(0xFF080D0F),
        tonalElevation = 0.dp
    ) {
        MainTab.entries.forEach { item ->
            NavigationBarItem(
                modifier = Modifier.testTag("tab_${item.name.lowercase()}"),
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, item.title, modifier = Modifier.size(20.dp)) },
                label = { Text(item.title, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IOffGreen,
                    selectedTextColor = IOffGreen,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = IOffMuted,
                    unselectedTextColor = IOffMuted
                )
            )
        }
    }
}

@Composable
fun IOffCircularProgress(
    value: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 12f,
    center: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = strokeWidth * density
            drawArc(IOffGreenDeep, -90f, 360f, false, style = Stroke(strokePx, cap = StrokeCap.Round))
            drawArc(IOffGreen, -90f, 360f * value.coerceIn(0f, 1f), false, style = Stroke(strokePx, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
fun IOffMetricCard(label: String, value: String, modifier: Modifier = Modifier, progress: Float? = null) {
    IOffCard(modifier) {
        Text(label, color = IOffMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(7.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium)
        if (progress != null) {
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = IOffGreen,
                trackColor = IOffBorder
            )
        }
    }
}

@Composable
fun IOffSectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = IOffGreen) }
    }
}

@Composable
fun IOffListRow(
    icon: ImageVector,
    iconColor: Color = IOffGreen,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = iconColor.copy(alpha = .12f), modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp)) }
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, color = IOffMuted, style = MaterialTheme.typography.bodyMedium)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = IOffMuted)
    }
}

@Composable
fun IOffEmptyState(title: String, body: String, icon: ImageVector = Icons.Outlined.Inbox) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = IOffMuted, modifier = Modifier.size(34.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text(body, color = IOffMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
    }
}

fun formatDuration(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

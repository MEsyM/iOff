package cz.ioff.app.ui.screens.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.ioff.app.ui.components.*
import cz.ioff.app.ui.theme.*

@Composable
fun FocusSetupScreen(
    state: FocusUiState,
    onGoalChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onStart: () -> Unit,
    onEnableDnd: () -> Unit
) {
    IOffScreen(title = "Focus") {
        Spacer(Modifier.height(8.dp))
        Text("Finish one thing.", style = MaterialTheme.typography.headlineLarge)
        Text("Everything else can wait.", color = IOffMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        IOffCard {
            Text("WHAT ARE YOU FINISHING?", color = IOffMuted, style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = state.goal,
                onValueChange = onGoalChange,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                placeholder = { Text("A concrete outcome") },
                singleLine = true,
                shape = RoundedCornerShape(13.dp)
            )
            Text("DURATION", color = IOffMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp, bottom = 7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(30, 60, 90, 120).forEach { minutes ->
                    FilterChip(
                        selected = state.selectedMinutes == minutes,
                        onClick = { onDurationChange(minutes) },
                        label = { Text("$minutes") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IOffGreen, selectedLabelColor = IOffBackground)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        IOffCard(onClick = if (state.dndReady) null else onEnableDnd) {
            IOffListRow(
                icon = Icons.Outlined.Shield,
                title = "Focus Protection",
                subtitle = if (state.dndReady) "Do Not Disturb is ready" else "Tap to enable Do Not Disturb access",
                trailing = { Icon(if (state.dndReady) Icons.Outlined.CheckCircle else Icons.Outlined.ChevronRight, null, tint = if (state.dndReady) IOffGreen else IOffYellow) }
            )
        }
        state.error?.let {
            Text(it, color = IOffRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(14.dp))
        IOffPrimaryButton("▶  Begin Focus", enabled = state.goal.isNotBlank() && !state.isLoading, onClick = onStart)
    }
}

@Composable
fun ActiveFocusScreen(
    state: FocusUiState,
    onUrge: () -> Unit,
    onParkIdea: (String) -> Unit,
    onEnd: () -> Unit
) {
    var ideaDialog by remember { mutableStateOf(false) }
    val totalMillis = state.selectedMinutes * 60_000L
    val progress = if (totalMillis == 0L) 0f else state.remainingMillis.toFloat() / totalMillis
    val minutes = state.remainingMillis / 60_000
    val seconds = state.remainingMillis / 1_000 % 60

    IOffScreen(title = "Focus", scroll = false, action = {
        Icon(Icons.Outlined.Settings, "Focus settings", tint = IOffText)
    }) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(8.dp))
            IOffCircularProgress(progress, Modifier.size(230.dp), strokeWidth = 13f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Deep Work", color = IOffGreen, style = MaterialTheme.typography.titleMedium)
                    Text(String.format("%02d:%02d", minutes, seconds), fontSize = 50.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.8).sp)
                    Text("of ${state.selectedMinutes} min", color = IOffMuted)
                }
            }
            IOffCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.goal, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = IOffMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(54.dp)) {
                FocusAction(Icons.Filled.Pause, "Urge ${if (state.urges > 0) "(${state.urges})" else ""}", onUrge)
                FocusAction(Icons.Filled.Stop, "End", onEnd)
            }
            IOffCard {
                IOffListRow(
                    icon = Icons.Outlined.Shield,
                    title = "Focus Shield Active",
                    subtitle = "Distracting apps are blocked",
                    onClick = { ideaDialog = true }
                )
            }
            Text("“Small steps. Big results.”", color = IOffMuted, fontStyle = FontStyle.Italic, modifier = Modifier.padding(bottom = 12.dp))
        }
    }

    if (ideaDialog) {
        IdeaParkingDialog(onDismiss = { ideaDialog = false }) {
            onParkIdea(it)
            ideaDialog = false
        }
    }
}

@Composable
private fun FocusAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(54.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = IOffSurfaceHigh)) {
            Icon(icon, label, tint = IOffText)
        }
        Text(label, color = IOffMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun FocusCompleteScreen(state: FocusUiState, onSave: (Int, String) -> Unit) {
    var score by remember { mutableIntStateOf(8) }
    var output by remember { mutableStateOf("") }
    val session = state.completedSession ?: return
    IOffScreen(title = "Complete") {
        Spacer(Modifier.height(28.dp))
        Surface(color = IOffGreenDeep, shape = CircleShape, modifier = Modifier.align(Alignment.CenterHorizontally).size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, tint = IOffGreen, modifier = Modifier.size(38.dp)) }
        }
        Text("Protected.", color = IOffGreen, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp))
        Text(
            "${session.actualMinutes} minutes of real work.",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp)
        )
        IOffCard {
            OutlinedTextField(
                output,
                { output = it.take(500) },
                Modifier.fillMaxWidth(),
                label = { Text("What exists now?") },
                minLines = 3,
                shape = RoundedCornerShape(13.dp)
            )
            Text("FOCUS QUALITY", color = IOffMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..10).forEach { value ->
                    Surface(
                        onClick = { score = value },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        color = if (score == value) IOffGreen else IOffSurfaceHigh,
                        contentColor = if (score == value) IOffBackground else IOffText,
                        shape = CircleShape
                    ) { Box(contentAlignment = Alignment.Center) { Text("$value", fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        IOffPrimaryButton("Save session  ✓") { onSave(score, output) }
    }
}

@Composable
private fun IdeaParkingDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Park the idea") },
        text = { OutlinedTextField(text, { text = it.take(300) }, placeholder = { Text("Capture it. Don't switch projects.") }) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Park") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

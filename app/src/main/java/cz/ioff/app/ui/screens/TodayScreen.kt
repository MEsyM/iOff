package cz.ioff.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.ioff.app.AttentionEngine
import cz.ioff.app.AttentionSnapshot
import cz.ioff.app.UsageMetrics
import cz.ioff.app.data.IOffRepository
import cz.ioff.app.ui.components.*
import cz.ioff.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TodayScreen(repository: IOffRepository, onStartFocus: (String) -> Unit, onHealth: () -> Unit) {
    val day = repository.day()
    var oneThing by remember(day) { mutableStateOf(repository.oneThing(day)) }
    var usageRevision by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(day) {
        UsageMetrics.collect(context, repository.preferences, day)
        usageRevision++
    }
    usageRevision
    val life = repository.dailyLife(day)
    val sessions = repository.metric("sessions", day)
    val attention = AttentionEngine.score(
        AttentionSnapshot(
            deepWorkMinutes = repository.metric("mins", day),
            focusAverage = if (sessions == 0) 0.0 else repository.metric("focusSum", day).toDouble() / sessions,
            urges = repository.metric("urges", day),
            shieldInterventions = repository.metric("shield", day),
            bypasses = repository.metric("bypasses", day),
            distractingMinutes = repository.metric("distracting", day)
        )
    )

    IOffScreen(title = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())) {
        Spacer(Modifier.height(6.dp))
        IOffCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = IOffGreenDeep, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.TrackChanges, null, tint = IOffGreen) }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Today’s One Thing", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = oneThing,
                        onValueChange = { oneThing = it.take(200); repository.saveOneThing(oneThing, day) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleLarge,
                        placeholder = { Text("What makes today count?") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedBorderColor = IOffGreen.copy(alpha = .45f)
                        )
                    )
                }
            }
            IOffPrimaryButton("▶  Start Focus", Modifier.padding(top = 8.dp), enabled = oneThing.isNotBlank()) { onStartFocus(oneThing.trim()) }
        }

        Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IOffCard(Modifier.weight(1f)) {
                Text("Attention Score", color = IOffMuted, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IOffCircularProgress(attention / 100f, Modifier.size(74.dp), strokeWidth = 8f) {
                        Text("$attention", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("↑  today", color = IOffGreen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 7.dp))
                }
            }
            IOffMetricCard("Focus Time", formatDuration(repository.metric("mins", day)), Modifier.weight(1f), repository.metric("mins", day) / 240f)
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CommitmentTile("No drugs", life.noDrugs, Modifier.weight(1f))
            CommitmentTile("No weed", life.noWeed, Modifier.weight(1f))
            CommitmentTile("No alcohol", life.noAlcohol, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            IOffCard(Modifier.weight(1f), onClick = onHealth) {
                IOffListRow(Icons.Outlined.DirectionsRun, title = "Sport", subtitle = if (life.sports.isEmpty()) "Not logged" else "${life.sports.size} activities")
            }
            IOffCard(Modifier.weight(1f), onClick = onHealth) {
                IOffListRow(Icons.Outlined.Favorite, iconColor = IOffRed, title = "Sex", subtitle = if (life.sex) "Logged today" else "Not logged")
            }
        }
        IOffCard(Modifier.padding(top = 8.dp)) {
            Text(
                "Discipline gives you the freedom\nto create the life you want.",
                color = IOffMuted,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun CommitmentTile(label: String, checked: Boolean, modifier: Modifier) {
    IOffCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        Icon(
            if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            null,
            tint = if (checked) IOffGreen else IOffMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp)
        )
    }
}

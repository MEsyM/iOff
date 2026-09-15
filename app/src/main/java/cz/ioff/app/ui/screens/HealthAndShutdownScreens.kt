package cz.ioff.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import cz.ioff.app.DailyLife
import cz.ioff.app.data.IOffRepository
import cz.ioff.app.ui.components.*
import cz.ioff.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthScreen(repository: IOffRepository, onBack: () -> Unit) {
    val day = repository.day()
    var revision by remember { mutableIntStateOf(0) }
    var sportDialog by remember { mutableStateOf(false) }
    var sexDialog by remember { mutableStateOf(false) }
    revision
    val life = repository.dailyLife(day)

    fun update(value: DailyLife) { repository.saveDailyLife(value, day); revision++ }

    IOffScreen(title = "Health & Lifestyle", onBack = onBack) {
        IOffSectionTitle("Daily Commitments", "Reset") {
            update(life.copy(noDrugs = false, noWeed = false, noAlcohol = false))
        }
        IOffCard {
            CommitmentRow("No drugs", Icons.Outlined.NoDrinks, life.noDrugs) { update(life.copy(noDrugs = it)) }
            HorizontalDivider(color = IOffBorder)
            CommitmentRow("No weed", Icons.Outlined.Grass, life.noWeed) { update(life.copy(noWeed = it)) }
            HorizontalDivider(color = IOffBorder)
            CommitmentRow("No alcohol", Icons.Outlined.LocalBar, life.noAlcohol) { update(life.copy(noAlcohol = it)) }
        }

        IOffSectionTitle("Sport Activities", "+ Add") { sportDialog = true }
        IOffCard {
            if (life.sports.isEmpty()) IOffEmptyState("No activity yet", "Add every movement session.", Icons.Outlined.DirectionsRun)
            life.sports.forEachIndexed { index, sport ->
                val bike = sport.activity.contains("bike", ignoreCase = true) || sport.activity.contains("cycling", ignoreCase = true)
                IOffListRow(
                    if (bike) Icons.Outlined.DirectionsBike else Icons.Outlined.DirectionsRun,
                    iconColor = if (bike) IOffBlue else IOffGreen,
                    title = sport.activity,
                    subtitle = "${sport.minutes} min",
                    trailing = { IconButton(onClick = { repository.deleteSport(sport.id, day); revision++ }) { Icon(Icons.Outlined.MoreHoriz, "Remove") } }
                )
                if (index < life.sports.lastIndex) HorizontalDivider(color = IOffBorder)
            }
        }

        IOffSectionTitle("Sex", "+ Add") { sexDialog = true }
        IOffCard {
            if (life.sexEvents.isEmpty()) IOffEmptyState("Not logged", "Log events without turning life into a score.", Icons.Outlined.FavoriteBorder)
            life.sexEvents.forEach { event ->
                IOffListRow(
                    Icons.Outlined.Favorite,
                    iconColor = IOffRed,
                    title = if (event.note.isBlank()) "Logged" else event.note,
                    subtitle = SimpleDateFormat("'Today' HH:mm", Locale.getDefault()).format(Date(event.id)),
                    trailing = { IconButton(onClick = { repository.deleteSex(event.id, day); revision++ }) { Icon(Icons.Outlined.MoreHoriz, "Remove") } }
                )
            }
        }
    }

    if (sportDialog) AddSportDialog({ sportDialog = false }) { activity, minutes ->
        repository.addSport(activity, minutes, day); revision++; sportDialog = false
    }
    if (sexDialog) AddSexDialog({ sexDialog = false }) { note ->
        repository.addSex(note, day); revision++; sexDialog = false
    }
}

@Composable
private fun CommitmentRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChecked: (Boolean) -> Unit) {
    IOffListRow(icon, iconColor = IOffYellow, title = title, trailing = {
        Checkbox(checked = checked, onCheckedChange = onChecked, colors = CheckboxDefaults.colors(checkedColor = IOffGreen, checkmarkColor = IOffBackground))
    })
}

@Composable
private fun AddSportDialog(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var activity by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add sport activity") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(activity, { activity = it.take(80) }, label = { Text("Activity") }, singleLine = true)
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("Minutes") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { onSave(activity, minutes.toIntOrNull() ?: 0) }, enabled = activity.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddSexDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log sex") },
        text = { OutlinedTextField(note, { note = it.take(80) }, label = { Text("Optional note") }) },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text("Log") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ShutdownScreen(repository: IOffRepository, onBack: () -> Unit, onSaved: () -> Unit) {
    var entry by remember { mutableStateOf(repository.shutdown()) }
    IOffScreen(title = "Daily Shutdown", onBack = onBack) {
        Text("Take 1 minute to reflect.", color = IOffMuted, modifier = Modifier.padding(bottom = 10.dp))
        ShutdownQuestion("What did I finish today?", entry.finished) { entry = entry.copy(finished = it) }
        ShutdownQuestion("What stole my attention?", entry.distraction) { entry = entry.copy(distraction = it) }
        ShutdownQuestion("Tomorrow’s One Thing?", entry.tomorrow) { entry = entry.copy(tomorrow = it) }
        Spacer(Modifier.height(12.dp))
        IOffPrimaryButton("Save & Close Day") {
            repository.saveShutdown(entry.copy(completed = true))
            onSaved()
        }
        Text(
            "A better tomorrow\nstarts with an honest today.",
            color = IOffText,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 22.dp)
        )
    }
}

@Composable
private fun ShutdownQuestion(label: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = true,
            onCheckedChange = null,
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(checkedColor = IOffGreen, checkmarkColor = IOffBackground)
        )
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
    }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(500)) },
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        minLines = 2,
        maxLines = 3,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = IOffSurface, focusedContainerColor = IOffSurface)
    )
}

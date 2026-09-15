package cz.ioff.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.ioff.app.IdeaState
import cz.ioff.app.ParkedIdea
import cz.ioff.app.data.IOffRepository
import cz.ioff.app.ui.components.*
import cz.ioff.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IdeasScreen(repository: IOffRepository) {
    var revision by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<IdeaState?>(null) }
    var dialog by remember { mutableStateOf<ParkedIdea?>(null) }
    var creating by remember { mutableStateOf(false) }
    revision
    val ideas = repository.ideas().filter {
        (filter == null || it.state == filter) && (query.isBlank() || it.text.contains(query, ignoreCase = true))
    }

    IOffScreen(title = "Idea Parking", action = {
        FilledIconButton(
            onClick = { creating = true },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = IOffGreen, contentColor = IOffBackground),
            shape = CircleShape
        ) { Icon(Icons.Outlined.Add, "Add idea") }
    }) {
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(top = 6.dp),
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Search ideas…") },
            singleLine = true,
            shape = RoundedCornerShape(13.dp)
        )
        Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            IdeaFilterChip("All", filter == null) { filter = null }
            IdeaState.entries.forEach { state -> IdeaFilterChip("${state.label()} (${repository.ideas().count { it.state == state }})", filter == state) { filter = state } }
        }
        if (ideas.isEmpty()) {
            IOffEmptyState("No ideas here", "Capture a thought and return to what matters.", Icons.Outlined.Lightbulb)
        } else {
            Spacer(Modifier.height(8.dp))
            ideas.forEach { idea ->
                IOffCard(Modifier.padding(bottom = 7.dp), onClick = { dialog = idea }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(idea.text, style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IdeaBadge(idea.state)
                                Icon(Icons.Outlined.Schedule, null, tint = IOffMuted, modifier = Modifier.padding(start = 9.dp).size(13.dp))
                                Text(
                                    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(idea.timestamp)),
                                    color = IOffMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(start = 3.dp)
                                )
                            }
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = IOffMuted)
                    }
                }
            }
        }
    }

    if (creating) IdeaEditor(null, onDismiss = { creating = false }, onSave = { text ->
        repository.parkIdea(text); revision++; creating = false
    })
    dialog?.let { idea ->
        IdeaEditor(idea, onDismiss = { dialog = null }, onSave = { text -> repository.updateIdea(idea, text); revision++; dialog = null }, onState = {
            repository.setIdeaState(idea, it); revision++; dialog = null
        }, onDelete = { repository.deleteIdea(idea); revision++; dialog = null })
    }
}

@Composable
private fun IdeaFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IOffGreen, selectedLabelColor = IOffBackground)
    )
}

@Composable
private fun IdeaBadge(state: IdeaState) {
    val color = when (state) { IdeaState.DO -> IOffYellow; IdeaState.LATER -> IOffYellow; IdeaState.DONE -> IOffGreen }
    Surface(color = color.copy(alpha = .2f), contentColor = color, shape = RoundedCornerShape(6.dp)) {
        Text(state.label().uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

@Composable
private fun IdeaEditor(
    idea: ParkedIdea?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onState: ((IdeaState) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var text by remember(idea) { mutableStateOf(idea?.text.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (idea == null) "Park an idea" else "Edit idea") },
        text = {
            Column {
                OutlinedTextField(text, { text = it.take(300) }, minLines = 2, placeholder = { Text("What is pulling at your attention?") })
                if (onState != null) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    IdeaState.entries.forEach { state -> TextButton(onClick = { onState(state) }) { Text(state.label()) } }
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = IOffRed) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun IdeaState.label() = when (this) { IdeaState.DO -> "Do"; IdeaState.LATER -> "Later"; IdeaState.DONE -> "Done" }

package cz.ioff.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.ioff.app.data.IOffRepository
import cz.ioff.app.ui.components.IOffCard
import cz.ioff.app.ui.components.IOffEmptyState
import cz.ioff.app.ui.components.IOffListRow
import cz.ioff.app.ui.components.IOffScreen
import cz.ioff.app.ui.theme.IOffBorder
import cz.ioff.app.ui.theme.IOffGreen
import cz.ioff.app.ui.theme.IOffMuted

data class InstalledAppUi(val packageName: String, val label: String)

@Composable
fun ProtectedAppsScreen(repository: IOffRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val installedApps = remember(context) { loadLaunchableApps(context) }
    val protected = remember(revision) { repository.protectedPackages() }

    ProtectedAppsContent(
        apps = installedApps,
        protectedPackages = protected,
        onBack = onBack,
        onToggle = { app, enabled ->
            repository.setProtectedApp(app.packageName, app.label, enabled)
            revision++
        }
    )
}

@Composable
internal fun ProtectedAppsContent(
    apps: List<InstalledAppUi>,
    protectedPackages: Set<String>,
    onBack: () -> Unit = {},
    onToggle: (InstalledAppUi, Boolean) -> Unit = { _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query, protectedPackages) {
        apps.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }.sortedWith(
            compareByDescending<InstalledAppUi> { it.packageName in protectedPackages }
                .thenBy { it.label.lowercase() }
        )
    }
    val enabledCount = apps.count { it.packageName in protectedPackages }

    IOffScreen(title = "Blocked Apps", onBack = onBack) {
        Text(
            "Choose which apps iOff blocks during Focus and Morning Protection.",
            color = IOffMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, tint = IOffGreen, modifier = Modifier.size(18.dp))
            Text(
                "$enabledCount protected on this phone",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 7.dp)
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Search installed apps") },
            singleLine = true,
            shape = RoundedCornerShape(13.dp)
        )

        if (filtered.isEmpty()) {
            IOffEmptyState("No apps found", "Try another search or check Android package visibility.", Icons.Outlined.Apps)
        } else {
            Spacer(Modifier.height(10.dp))
            IOffCard {
                filtered.forEachIndexed { index, app ->
                    val enabled = app.packageName in protectedPackages
                    IOffListRow(
                        icon = Icons.Outlined.Apps,
                        iconColor = if (enabled) IOffGreen else IOffMuted,
                        title = app.label,
                        subtitle = app.packageName,
                        trailing = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { onToggle(app, it) }
                            )
                        }
                    )
                    if (index < filtered.lastIndex) HorizontalDivider(color = IOffBorder)
                }
            }
        }

        Text(
            "Shield only reacts while protection is active. System apps and iOff itself are never added automatically.",
            color = IOffMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
    }
}

@Suppress("DEPRECATION")
private fun loadLaunchableApps(context: Context): List<InstalledAppUi> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(context.packageManager).toString() }
                .getOrDefault(packageName)
                .ifBlank { packageName }
            InstalledAppUi(packageName, label)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

package com.dualactionwindows.dawdrive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DawModule(
    val title: String,
    val subtitle: String
) {
    Games("Quick Trivia", "Career mode with levels, XP and memory"),
    Video("Video", "Web browser"),
    Erp("DAW ERP", "Deals, contacts and next actions"),
    Tools("Tools", "Vehicle and utility tools")
}

@Composable
fun DawDriveApp(
    onVideoClick: () -> Unit,
    onRoadVoiceStart: () -> Unit,
    onRoadVoiceStop: () -> Unit
) {
    var selectedModule by remember { mutableStateOf<DawModule?>(null) }

    BackHandler(enabled = selectedModule != null) {
        selectedModule = null
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF080A0D)
        ) {
            if (selectedModule == null) {
                LauncherScreen(
                    onModuleSelected = { module ->
                        when (module) {
                            DawModule.Video -> onVideoClick()
                            else -> selectedModule = module
                        }
                    }
                )
            } else if (selectedModule == DawModule.Games) {
                RoadVoiceScreen(
                    onStart = onRoadVoiceStart,
                    onStop = onRoadVoiceStop,
                    onBack = { selectedModule = null }
                )
            } else {
                ModulePlaceholder(
                    module = selectedModule!!,
                    onBack = { selectedModule = null }
                )
            }
        }
    }
}

@Composable
private fun LauncherScreen(
    onModuleSelected: (DawModule) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Column {
            Text(
                text = "DAW DRIVE",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "v0.10 Quick Trivia Career",
                color = Color(0xFF9AA4B2),
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LauncherTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    module = DawModule.Games,
                    onClick = { onModuleSelected(DawModule.Games) }
                )
                LauncherTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    module = DawModule.Video,
                    onClick = { onModuleSelected(DawModule.Video) }
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LauncherTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    module = DawModule.Erp,
                    onClick = { onModuleSelected(DawModule.Erp) }
                )
                LauncherTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    module = DawModule.Tools,
                    onClick = { onModuleSelected(DawModule.Tools) }
                )
            }
        }
    }
}

@Composable
private fun LauncherTile(
    modifier: Modifier,
    module: DawModule,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF171B22),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = module.title,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = module.subtitle,
                color = Color(0xFFAFB7C2),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun RoadVoiceScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Road Voice",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Normal use: Android Auto > DAW Drive > choose a game > Play. Phone setup is needed only once for microphone permission.",
            color = Color(0xFFAFB7C2),
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("One-time setup", fontSize = 18.sp)
            }
            Button(
                onClick = onStop,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A303A)
                )
            ) {
                Text("Stop", fontSize = 18.sp)
            }
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A303A)
                )
            ) {
                Text("Back", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun ModulePlaceholder(
    module: DawModule,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = module.title,
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = module.subtitle + " — module shell ready",
            color = Color(0xFFAFB7C2),
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onBack,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2A303A),
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Back to launcher",
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }
    }
}

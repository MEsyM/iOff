package com.dualactionwindows.dawdrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualactionwindows.dawdrive.ui.LoneRiderColors
import com.dualactionwindows.dawdrive.ui.LoneRiderTheme

class FamilySetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = FamilyGameEngine(this)

        setContent {
            val names = remember {
                mutableStateListOf<String>().apply {
                    addAll(engine.playerNames())
                    while (size < 4) add("")
                }
            }
            val difficulties = remember {
                mutableStateListOf<FamilyGameEngine.Difficulty>().apply {
                    addAll(engine.playerDifficulties())
                    while (size < 6) add(FamilyGameEngine.Difficulty.NORMAL)
                }
            }
            var mode by remember { mutableStateOf(engine.mode()) }
            var saved by remember { mutableStateOf(false) }

            LoneRiderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LoneRiderColors.Background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "LONE RIDER",
                            color = Color.White,
                            fontSize = 13.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Family Quiz",
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Nastav hráče, jejich pořadí a obtížnost. V Battle módu funguje jméno jako hlasový buzzer.",
                            color = LoneRiderColors.TextSecondary,
                            fontSize = 14.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ModeButton(
                                title = "ROUND",
                                subtitle = "Každý hraje své kolo",
                                selected = mode == FamilyGameEngine.Mode.ROUND,
                                color = LoneRiderColors.Purple
                            ) {
                                mode = FamilyGameEngine.Mode.ROUND
                                saved = false
                            }
                            ModeButton(
                                title = "BATTLE",
                                subtitle = "Kdo buzzne první, odpovídá",
                                selected = mode == FamilyGameEngine.Mode.BATTLE,
                                color = LoneRiderColors.Amber
                            ) {
                                mode = FamilyGameEngine.Mode.BATTLE
                                saved = false
                            }
                        }

                        Surface(
                            color = LoneRiderColors.Surface,
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    if (mode == FamilyGameEngine.Mode.ROUND) {
                                        "Round mode • otázky se střídají podle hráčů • série zvyšuje body"
                                    } else {
                                        "Battle mode • společná otázka • řekni své jméno pro buzz • steal po chybě"
                                    },
                                    color = LoneRiderColors.TextSecondary,
                                    fontSize = 13.sp
                                )

                                repeat(6) { index ->
                                    PlayerEditor(
                                        number = index + 1,
                                        name = names.getOrElse(index) { "" },
                                        difficulty = difficulties.getOrElse(index) {
                                            FamilyGameEngine.Difficulty.NORMAL
                                        },
                                        onNameChange = { value ->
                                            while (names.size <= index) names.add("")
                                            names[index] = value
                                            saved = false
                                        },
                                        onDifficultyChange = { difficulty ->
                                            while (difficulties.size <= index) {
                                                difficulties.add(FamilyGameEngine.Difficulty.NORMAL)
                                            }
                                            difficulties[index] = difficulty
                                            saved = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionButton(
                                text = if (saved) "ULOŽENO" else "ULOŽIT HRÁČE A MÓD",
                                primary = true
                            ) {
                                engine.savePlayers(names.toList(), difficulties.toList())
                                engine.setMode(mode)
                                saved = true
                            }
                            ActionButton("ZPĚT", false) { finish() }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(96.dp),
        contentPadding = PaddingValues(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color.copy(alpha = 0.28f) else LoneRiderColors.Surface,
            contentColor = Color.White
        )
    ) {
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = LoneRiderColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerEditor(
    number: Int,
    name: String,
    difficulty: FamilyGameEngine.Difficulty,
    onNameChange: (String) -> Unit,
    onDifficultyChange: (FamilyGameEngine.Difficulty) -> Unit
) {
    Surface(
        color = LoneRiderColors.SurfaceRaised,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    number.toString(),
                    color = LoneRiderColors.Cyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 15.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Jméno hráče") },
                    singleLine = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FamilyGameEngine.Difficulty.entries.forEach { item ->
                    Button(
                        onClick = { onDifficultyChange(item) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (difficulty == item) {
                                LoneRiderColors.Blue
                            } else {
                                Color(0xFF1A2431)
                            },
                            contentColor = Color.White
                        )
                    ) {
                        Text(item.labelCs(), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ActionButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) LoneRiderColors.Blue else LoneRiderColors.SurfaceRaised,
            contentColor = Color.White
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

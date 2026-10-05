package com.dualactionwindows.dawdrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
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

class EnglishSetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = EnglishLearningEngine(this)

        setContent {
            var selectedPlayer by remember { mutableStateOf(engine.selectedPlayerId()) }
            var revision by remember { mutableStateOf(0) }
            val profile = remember(selectedPlayer, revision) { engine.settings(selectedPlayer) }
            val names = engine.playerNames()

            LoneRiderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LoneRiderColors.Background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "LONE RIDER ENGLISH",
                            color = LoneRiderColors.Cyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "English profiles",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        )
                        Text(
                            "Každý člen rodiny má vlastní úroveň, Learn/Challenge režim, XP a historii opakování.",
                            color = LoneRiderColors.TextSecondary,
                            fontSize = 14.sp
                        )

                        Section("Kdo se učí?") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                names.forEachIndexed { index, name ->
                                    ChoiceButton(name, selectedPlayer == index) {
                                        selectedPlayer = index
                                        engine.setSelectedPlayer(index)
                                    }
                                }
                            }
                        }

                        Section("Typ profilu") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceButton("KID", profile.audience == EnglishLearningEngine.Audience.KID) {
                                    engine.saveSettings(
                                        selectedPlayer,
                                        EnglishLearningEngine.Audience.KID,
                                        EnglishLearningEngine.Level.KID_STARTER,
                                        profile.mode
                                    )
                                    revision++
                                }
                                ChoiceButton("ADULT", profile.audience == EnglishLearningEngine.Audience.ADULT) {
                                    engine.saveSettings(
                                        selectedPlayer,
                                        EnglishLearningEngine.Audience.ADULT,
                                        EnglishLearningEngine.Level.A2,
                                        profile.mode
                                    )
                                    revision++
                                }
                            }
                        }

                        Section("Obtížnost") {
                            val levels = EnglishLearningEngine.Level.entries
                                .filter { it.audience == profile.audience }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                levels.forEach { level ->
                                    ChoiceButton(level.label, profile.level == level) {
                                        engine.saveSettings(
                                            selectedPlayer,
                                            profile.audience,
                                            level,
                                            profile.mode
                                        )
                                        revision++
                                    }
                                }
                            }
                        }

                        Section("Režim") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceButton("LEARN", profile.mode == EnglishLearningEngine.Mode.LEARN) {
                                    engine.saveSettings(
                                        selectedPlayer,
                                        profile.audience,
                                        profile.level,
                                        EnglishLearningEngine.Mode.LEARN
                                    )
                                    revision++
                                }
                                ChoiceButton("CHALLENGE", profile.mode == EnglishLearningEngine.Mode.CHALLENGE) {
                                    engine.saveSettings(
                                        selectedPlayer,
                                        profile.audience,
                                        profile.level,
                                        EnglishLearningEngine.Mode.CHALLENGE
                                    )
                                    revision++
                                }
                            }
                        }

                        Section("Progress") {
                            val accuracy = if (profile.answered == 0) 0 else profile.correct * 100 / profile.answered
                            Text(
                                profile.playerName + " • " + profile.level.label +
                                    " • " + profile.xp + " XP • " +
                                    accuracy + "% • best streak " + profile.bestStreak,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                if (profile.audience == EnglishLearningEngine.Audience.KID) {
                                    "Kids obsah: slovíčka, základní fráze, škola, sport, rodina a mluvení."
                                } else {
                                    "Adult obsah: travel, hotel, restaurant, business, small talk a vyjednávání."
                                },
                                color = LoneRiderColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceButton("RESET LEKCE", false) {
                                engine.resetRound()
                                revision++
                            }
                            ChoiceButton("HOTOVO", true) { finish() }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Section(
    title: String,
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    Surface(
        color = LoneRiderColors.Surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@androidx.compose.runtime.Composable
private fun ChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) LoneRiderColors.Blue else LoneRiderColors.SurfaceRaised,
            contentColor = Color.White
        )
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

package com.dualactionwindows.dawdrive.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualactionwindows.dawdrive.R

@Composable
fun CarPreviewScreen(
    title: String,
    subtitle: String,
    question: String,
    selectedGame: String,
    isPlaying: Boolean,
    connected: Boolean,
    onSelectTrivia: () -> Unit,
    onSelectSpelling: () -> Unit,
    onSelectGuessWho: () -> Unit,
    onSelectKids: () -> Unit,
    onSelectFamily: () -> Unit,
    onFamilySetup: () -> Unit,
    onPlayPause: () -> Unit,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val game = when (selectedGame) {
        "spelling" -> GameVisual("🐝", "SPELLING BEE", LoneRiderColors.Amber)
        "guesswho" -> GameVisual("👤", "GUESS WHO", LoneRiderColors.Green)
        "kids" -> GameVisual("★", "TRIVIA KIDS", LoneRiderColors.Blue)
        "family" -> GameVisual("🏆", "FAMILY QUIZ", LoneRiderColors.Pink)
        else -> GameVisual("💡", "QUICK TRIVIA", LoneRiderColors.Purple)
    }

    LoneRiderTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LoneRiderColors.Background
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF05070B), Color(0xFF07111F))
                        )
                    )
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NavigationRail(
                    selectedGame = selectedGame,
                    onSelectTrivia = onSelectTrivia,
                    onSelectSpelling = onSelectSpelling,
                    onSelectGuessWho = onSelectGuessWho,
                    onSelectKids = onSelectKids,
                    onSelectFamily = onSelectFamily
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CarHeader(
                        connected = connected,
                        onClose = onClose
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        color = LoneRiderColors.Surface,
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GameHero(
                                modifier = Modifier.weight(0.34f),
                                visual = game,
                                isPlaying = isPlaying
                            )

                            Column(
                                modifier = Modifier.weight(0.66f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = game.label,
                                        color = game.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.6.sp
                                    )
                                    StatusPill(
                                        text = if (isPlaying) "LISTENING" else "READY",
                                        active = isPlaying
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    text = title.ifBlank { game.label },
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = subtitle.ifBlank { "Lone Rider • Car Game Hub" },
                                    color = LoneRiderColors.TextSecondary,
                                    fontSize = 14.sp
                                )

                                Spacer(Modifier.height(20.dp))

                                Surface(
                                    color = Color(0xFF0A1421),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = question.ifBlank { "Press Play to start" },
                                        color = LoneRiderColors.TextPrimary,
                                        fontSize = 23.sp,
                                        lineHeight = 30.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(20.dp)
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Text(
                                    text = if (isPlaying) {
                                        "Voice loop active • answer naturally"
                                    } else {
                                        "Large controls • voice first • family friendly"
                                    },
                                    color = LoneRiderColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    ControlBar(
                        isPlaying = isPlaying,
                        family = selectedGame == "family",
                        onPlayPause = onPlayPause,
                        onRepeat = onRepeat,
                        onNext = onNext,
                        onStop = onStop,
                        onFamilySetup = onFamilySetup
                    )
                }
            }
        }
    }
}

private data class GameVisual(
    val icon: String,
    val label: String,
    val accent: Color
)

@Composable
private fun NavigationRail(
    selectedGame: String,
    onSelectTrivia: () -> Unit,
    onSelectSpelling: () -> Unit,
    onSelectGuessWho: () -> Unit,
    onSelectKids: () -> Unit,
    onSelectFamily: () -> Unit
) {
    Surface(
        modifier = Modifier.width(92.dp),
        color = Color(0xFF08101A),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            WolfMark()
            RailButton("💡", selectedGame == "trivia", onSelectTrivia)
            RailButton("🐝", selectedGame == "spelling", onSelectSpelling)
            RailButton("👤", selectedGame == "guesswho", onSelectGuessWho)
            RailButton("★", selectedGame == "kids", onSelectKids)
            RailButton("🏆", selectedGame == "family", onSelectFamily)
        }
    }
}

@Composable
private fun WolfMark() {
    Image(
        painter = painterResource(R.drawable.ic_lone_rider),
        contentDescription = "Lone Rider logo",
        modifier = Modifier
            .width(54.dp)
            .height(54.dp)
    )
}

@Composable
private fun RailButton(
    icon: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) LoneRiderColors.Blue else Color.Transparent,
            contentColor = Color.White
        ),
        modifier = Modifier
            .width(62.dp)
            .height(54.dp)
    ) {
        Text(icon, fontSize = 23.sp)
    }
}

@Composable
private fun CarHeader(
    connected: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "LONE RIDER",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.4.sp
            )
            Text(
                "CAR GAME HUB",
                color = LoneRiderColors.Cyan,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusPill(if (connected) "CONNECTED" else "CONNECTING", connected)
            CarButton("CLOSE", false, onClose)
        }
    }
}

@Composable
private fun GameHero(
    modifier: Modifier,
    visual: GameVisual,
    isPlaying: Boolean
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = visual.accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(26.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(visual.icon, fontSize = 70.sp)
                Text(
                    visual.label,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp
                )
                Text(
                    if (isPlaying) "● VOICE ACTIVE" else "○ READY",
                    color = if (isPlaying) LoneRiderColors.Green else LoneRiderColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ControlBar(
    isPlaying: Boolean,
    family: Boolean,
    onPlayPause: () -> Unit,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onFamilySetup: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF08101A),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CarButton("REPEAT", false, onRepeat)
            Spacer(Modifier.width(10.dp))
            CarButton(if (isPlaying) "PAUSE" else "PLAY", true, onPlayPause)
            Spacer(Modifier.width(10.dp))
            CarButton("NEXT", false, onNext)
            Spacer(Modifier.width(10.dp))
            CarButton("STOP", false, onStop)
            if (family) {
                Spacer(Modifier.width(10.dp))
                CarButton("PLAYERS / MODE", false, onFamilySetup)
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    active: Boolean
) {
    Surface(
        color = if (active) LoneRiderColors.Green.copy(alpha = 0.18f) else Color(0xFF1A2431),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text,
            color = if (active) LoneRiderColors.Green else LoneRiderColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun CarButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) LoneRiderColors.Blue else LoneRiderColors.SurfaceRaised,
            contentColor = Color.White
        ),
        modifier = Modifier.height(48.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

package com.dualactionwindows.dawdrive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CarPreviewScreen(
    title: String,
    subtitle: String,
    question: String,
    isPlaying: Boolean,
    connected: Boolean,
    onPlayPause: () -> Unit,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF050607)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DAW DRIVE",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "PHONE CAR PREVIEW",
                            color = Color(0xFF8E98A5),
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (connected) Color(0xFF26362C) else Color(0xFF3A2828),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (connected) "CONNECTED" else "CONNECTING",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        PreviewButton(
                            text = "Close",
                            primary = false,
                            onClick = onClose
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = Color(0xFF111418),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.34f)
                                .fillMaxSize()
                                .background(
                                    color = Color(0xFF1A1F26),
                                    shape = RoundedCornerShape(22.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Q",
                                    color = Color.White,
                                    fontSize = 82.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "QUICK TRIVIA",
                                    color = Color(0xFF9AA5B3),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(0.66f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = subtitle,
                                color = Color(0xFF9AA5B3),
                                fontSize = 17.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = question,
                                color = Color(0xFFE9EEF4),
                                fontSize = 24.sp,
                                lineHeight = 31.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(26.dp))

                            Text(
                                text = if (isPlaying) "GAME ACTIVE • VOICE LOOP" else "READY / PAUSED",
                                color = if (isPlaying) Color(0xFFC7D4C9) else Color(0xFF8D98A6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewButton(
                        text = "Repeat",
                        primary = false,
                        onClick = onRepeat
                    )

                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                    PreviewButton(
                        text = if (isPlaying) "Pause" else "Play",
                        primary = true,
                        onClick = onPlayPause
                    )

                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                    PreviewButton(
                        text = "Next",
                        primary = false,
                        onClick = onNext
                    )

                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                    PreviewButton(
                        text = "Stop",
                        primary = false,
                        onClick = onStop
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Color(0xFFE5E9EE) else Color(0xFF20252C),
            contentColor = if (primary) Color(0xFF0B0D10) else Color.White
        ),
        modifier = Modifier.height(48.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

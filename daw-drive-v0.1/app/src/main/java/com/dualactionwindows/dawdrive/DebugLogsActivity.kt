package com.dualactionwindows.dawdrive

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DebugLogsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var logText by remember {
                mutableStateOf(DawDebugLog.read(this))
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF080A0D)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Lone Rider Debug Logs",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Audio focus, MediaSession, TTS, STT and service lifecycle",
                            color = Color(0xFF9AA4B2),
                            fontSize = 14.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = getSystemService(ClipboardManager::class.java)
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "Lone Rider debug log",
                                            logText
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Copy")
                            }

                            Button(
                                onClick = {
                                    logText = DawDebugLog.read(this@DebugLogsActivity)
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Refresh")
                            }

                            Button(
                                onClick = {
                                    DawDebugLog.clear(this@DebugLogsActivity)
                                    logText = ""
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Clear")
                            }

                            Button(
                                onClick = { finish() },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Close")
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(
                                    Color(0xFF11151B),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(14.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = if (logText.isBlank()) {
                                        "No log entries yet. Test in the car, then tap Refresh."
                                    } else {
                                        logText
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    color = Color(0xFFD7DEE8),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

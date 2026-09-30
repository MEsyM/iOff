package com.dualactionwindows.dawdrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080A0D)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Family Game", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Nastav 2 až 6 hráčů. Každý má vlastní obtížnost. Jméno funguje zároveň jako hlasový buzzer v Battle.",
                            color = Color(0xFFADB6C3),
                            fontSize = 16.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { mode = FamilyGameEngine.Mode.ROUND },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == FamilyGameEngine.Mode.ROUND) Color(0xFF334A3A) else Color(0xFF1A1F26)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Round") }
                            Button(
                                onClick = { mode = FamilyGameEngine.Mode.BATTLE },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == FamilyGameEngine.Mode.BATTLE) Color(0xFF4A3434) else Color(0xFF1A1F26)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Battle") }
                        }

                        Text(
                            if (mode == FamilyGameEngine.Mode.ROUND)
                                "Round: každý hráč dostane vlastní otázku. Správně 100 bodů + rostoucí bonus za sérii."
                            else
                                "Battle 2.0: vítěz vybírá další kategorii a 100/200/300 bodů hlasem. Po otázce první řekne své jméno. Chyba otevře steal ostatním za 70 % a pak 50 % bodů. Poslední 3 otázky jsou bonusové za dvojnásobek.",
                            color = Color(0xFF98A3B1),
                            fontSize = 14.sp
                        )

                        repeat(6) { index ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = names.getOrElse(index) { "" },
                                    onValueChange = { value ->
                                        while (names.size <= index) names.add("")
                                        names[index] = value
                                        saved = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Hráč " + (index + 1)) },
                                    singleLine = true
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FamilyGameEngine.Difficulty.entries.forEach { difficulty ->
                                        Button(
                                            onClick = {
                                                while (difficulties.size <= index) {
                                                    difficulties.add(FamilyGameEngine.Difficulty.NORMAL)
                                                }
                                                difficulties[index] = difficulty
                                                saved = false
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (difficulties.getOrNull(index) == difficulty) {
                                                    Color(0xFF334A3A)
                                                } else {
                                                    Color(0xFF1A1F26)
                                                }
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                horizontal = 10.dp,
                                                vertical = 6.dp
                                            )
                                        ) {
                                            Text(difficulty.labelCs(), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    engine.savePlayers(names.toList(), difficulties.toList())
                                    engine.setMode(mode)
                                    saved = true
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) { Text(if (saved) "Uloženo" else "Uložit hráče a mód") }
                            Button(
                                onClick = { finish() },
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Zpět") }
                        }

                        Text(
                            "Round respektuje obtížnost každého hráče zvlášť. Battle má jednu společnou otázku, proto vybírá obtížnost kolem mediánu celé rodiny. Tip: po otázce řekni pouze své jméno a až po potvrzení odpověď.",
                            color = Color(0xFF7F8A99),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

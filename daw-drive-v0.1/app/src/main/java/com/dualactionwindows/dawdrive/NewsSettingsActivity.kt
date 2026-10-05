package com.dualactionwindows.dawdrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualactionwindows.dawdrive.ui.LoneRiderTheme

class NewsSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = NewsEngine(this)
        setContent {
            LoneRiderTheme {
                var revision by remember { mutableStateOf(0) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("DAILY NEWS", fontSize = 28.sp)
                    Text("Vyber zdroje a témata pro denní hlasový briefing.")
                    engine.sources.forEach { source ->
                        val enabled = engine.isEnabled(source)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.name)
                                Text(source.url, fontSize = 10.sp)
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    engine.setEnabled(source.id, it)
                                    revision += 1
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("HLAVNÍ TÉMATA", fontSize = 20.sp)
                    Text("Vyber, co tě zajímá. Hlavní zprávy nech zapnuté jako pojistku pro zásadní události.", fontSize = 12.sp)
                    engine.topics.forEach { topic ->
                        val enabled = engine.isTopicEnabled(topic)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(topic.name, modifier = Modifier.weight(1f))
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    engine.setTopicEnabled(topic.id, it)
                                    revision += 1
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Počet zpráv: " + engine.dailyLimit())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(5, 10, 15, 20).forEach { limit ->
                            Button(onClick = {
                                engine.setDailyLimit(limit)
                                revision += 1
                            }) { Text(limit.toString()) }
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ContextCompat.startForegroundService(
                                this@NewsSettingsActivity,
                                android.content.Intent(this@NewsSettingsActivity, RoadGameMediaService::class.java)
                                    .setAction(RoadGameMediaService.ACTION_START_NEWS)
                            )
                        }
                    ) {
                        Text("PŘEHRÁT DNEŠNÍ ZPRÁVY")
                    }
                    Text("Hlasem: „více“ přečte celý článek. Dále: „další“, „uložit“, „zopakuj“, „stop“.", fontSize = 12.sp)
                }
                revision
            }
        }
    }
}

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.dualactionwindows.dawdrive.TriviaGameEngine
import com.dualactionwindows.dawdrive.TriviaQuestionBank

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
    profile: TriviaGameEngine.Profile,
    achievementTitles: List<String>,
    onVideoClick: () -> Unit,
    onRoadVoiceStart: () -> Unit,
    onRoadVoiceStop: () -> Unit,
    onLanguageChange: (TriviaGameEngine.Language) -> Unit,
    onRefreshProfile: () -> Unit
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
            when {
                selectedModule == null -> LauncherScreen(
                    onModuleSelected = { module ->
                        when (module) {
                            DawModule.Video -> onVideoClick()
                            else -> selectedModule = module
                        }
                    }
                )

                selectedModule == DawModule.Games -> TriviaProfileScreen(
                    profile = profile,
                    achievementTitles = achievementTitles,
                    onStart = onRoadVoiceStart,
                    onStop = onRoadVoiceStop,
                    onLanguageChange = onLanguageChange,
                    onRefresh = onRefreshProfile,
                    onBack = { selectedModule = null }
                )

                else -> ModulePlaceholder(
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
        Text(
            text = "DAW DRIVE",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "v0.11 Quick Trivia",
            color = Color(0xFF9AA4B2),
            fontSize = 14.sp
        )

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
private fun TriviaProfileScreen(
    profile: TriviaGameEngine.Profile,
    achievementTitles: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLanguageChange: (TriviaGameEngine.Language) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val cs = profile.language == TriviaGameEngine.Language.CS
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Quick Trivia",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (cs) {
                        "Hands-free kariéra pro Android Auto"
                    } else {
                        "Hands-free career for Android Auto"
                    },
                    color = Color(0xFFAFB7C2),
                    fontSize = 16.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallActionButton(
                    text = "EN",
                    active = profile.language == TriviaGameEngine.Language.EN
                ) {
                    onLanguageChange(TriviaGameEngine.Language.EN)
                }

                SmallActionButton(
                    text = "CZ",
                    active = profile.language == TriviaGameEngine.Language.CS
                ) {
                    onLanguageChange(TriviaGameEngine.Language.CS)
                }
            }
        }

        val careerLines = mutableListOf(
            "Level " + profile.level + " • " + localizedLevelName(profile.level, cs),
            profile.xp.toString() + " XP",
            (if (cs) "Úspěšnost " else "Accuracy ") + profile.accuracy + "%",
            (if (cs) "Nejlepší série " else "Best streak ") + profile.bestStreak,
            (if (cs) "Dokončená kola " else "Rounds completed ") + profile.roundsCompleted
        )
        profile.xpForNextLevel?.let {
            careerLines[1] = careerLines[1] +
                if (cs) " • další level při " + it + " XP"
                else " • next level at " + it + " XP"
        }

        StatCard(
            title = if (cs) "Kariéra" else "Career",
            lines = careerLines
        )

        StatCard(
            title = if (cs) "Hlasové příkazy" else "Voice commands",
            lines = if (cs) {
                listOf(
                    "„zopakuj“ • zopakuje otázku",
                    "„přeskoč“ • další otázka",
                    "„skóre“ • aktuální statistika",
                    "„úroveň“ • level a XP",
                    "„zastav hru“ • uloží a pozastaví"
                )
            } else {
                listOf(
                    ""repeat" • repeat question",
                    ""skip" • next question",
                    ""score" • current stats",
                    ""level" • level and XP",
                    ""stop game" • save and pause"
                )
            }
        )

        Text(
            text = if (cs) "Skill podle kategorií" else "Category skill",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )

        profile.categoryStats
            .sortedByDescending { it.rating }
            .forEach { stat ->
                CategoryRow(stat, cs)
            }

        StatCard(
            title = if (cs) "Achievementy" else "Achievements",
            lines = if (achievementTitles.isEmpty()) {
                listOf(
                    if (cs) "Zatím žádný. První získáš dokončením kola."
                    else "None yet. Finish a round to unlock the first one."
                )
            } else {
                achievementTitles.map { "• " + it }
            }
        )

        Text(
            text = if (cs) {
                "Běžné použití: Android Auto → DAW Drive → Quick Trivia → Play. Telefonní setup je potřeba jen jednou kvůli mikrofonu."
            } else {
                "Normal use: Android Auto → DAW Drive → Quick Trivia → Play. Phone setup is only needed once for microphone permission."
            },
            color = Color(0xFFAFB7C2),
            fontSize = 15.sp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(if (cs) "Jednorázový setup" else "One-time setup")
            }

            SmallActionButton(
                text = if (cs) "Obnovit" else "Refresh",
                active = false,
                onClick = onRefresh
            )

            SmallActionButton(
                text = "Stop",
                active = false,
                onClick = onStop
            )

            SmallActionButton(
                text = if (cs) "Zpět" else "Back",
                active = false,
                onClick = onBack
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun CategoryRow(
    stat: TriviaGameEngine.CategoryStat,
    cs: Boolean
) {
    Surface(
        color = Color(0xFF141820),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = localizedCategory(stat.category, cs),
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (stat.answered == 0) {
                    if (cs) "nové • skill " + stat.rating
                    else "new • skill " + stat.rating
                } else {
                    stat.accuracy.toString() + "% • skill " + stat.rating
                },
                color = Color(0xFFAFB7C2)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    lines: List<String>
) {
    Surface(
        color = Color(0xFF171B22),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            lines.forEach { line ->
                Text(
                    text = line,
                    color = Color(0xFFAFB7C2),
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF4A5568) else Color(0xFF242A33),
            contentColor = Color.White
        )
    ) {
        Text(text)
    }
}

private fun localizedLevelName(level: Int, cs: Boolean): String {
    return if (cs) {
        when (level) {
            1 -> "Nováček"
            2 -> "Průzkumník"
            3 -> "Vyzyvatel"
            4 -> "Expert"
            5 -> "Mistr"
            6 -> "Elita"
            else -> "Legenda"
        }
    } else {
        when (level) {
            1 -> "Rookie"
            2 -> "Explorer"
            3 -> "Challenger"
            4 -> "Expert"
            5 -> "Master"
            6 -> "Elite"
            else -> "Legend"
        }
    }
}

private fun localizedCategory(
    category: TriviaQuestionBank.Category,
    cs: Boolean
): String {
    if (!cs) {
        return category.key.replaceFirstChar { it.uppercase() }
    }

    return when (category) {
        TriviaQuestionBank.Category.GEOGRAPHY -> "Geografie"
        TriviaQuestionBank.Category.SCIENCE -> "Věda"
        TriviaQuestionBank.Category.HISTORY -> "Historie"
        TriviaQuestionBank.Category.GENERAL -> "Všeobecné"
        TriviaQuestionBank.Category.TECHNOLOGY -> "Technologie"
        TriviaQuestionBank.Category.NATURE -> "Příroda"
        TriviaQuestionBank.Category.SPORTS -> "Sport"
        TriviaQuestionBank.Category.CULTURE -> "Kultura"
        TriviaQuestionBank.Category.CARS -> "Auta"
        TriviaQuestionBank.Category.NUMBERS -> "Čísla"
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
            Text("Back to launcher")
        }
    }
}

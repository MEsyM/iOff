package com.dualactionwindows.dawdrive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualactionwindows.dawdrive.TriviaGameEngine
import com.dualactionwindows.dawdrive.TriviaQuestionBank
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private enum class DawModule(
    val title: String,
    val subtitle: String
) {
    Games("Road Games", "Quick Trivia + Kids + Spelling Bee + Guess Who"),
    Video("Video", "Web browser"),
    Erp("DAW ERP", "Deals, contacts and next actions"),
    Tools("Tools", "Vehicle and utility tools")
}

@Composable
fun DawDriveApp(
    profile: TriviaGameEngine.Profile,
    dashboard: TriviaGameEngine.DashboardData,
    achievementTitles: List<String>,
    onVideoClick: () -> Unit,
    onCarPreviewClick: () -> Unit,
    onDebugLogsClick: () -> Unit,
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
                    dashboard = dashboard,
                    achievementTitles = achievementTitles,
                    onCarPreview = onCarPreviewClick,
                    onDebugLogs = onDebugLogsClick,
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
            text = "v0.20 Road Games",
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
    dashboard: TriviaGameEngine.DashboardData,
    achievementTitles: List<String>,
    onCarPreview: () -> Unit,
    onDebugLogs: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
                    text = if (cs) "Career dashboard" else "Career dashboard",
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

        KpiStrip(profile, cs)

        DashboardCard(
            title = if (cs) "Vývoj XP" else "XP progression",
            subtitle = if (cs) "Kumulativní XP po dokončených kolech" else "Cumulative XP after completed rounds"
        ) {
            XpProgressChart(
                points = dashboard.xpProgression,
                cs = cs
            )
        }

        DashboardCard(
            title = if (cs) "Přesnost podle kategorií" else "Accuracy by category",
            subtitle = if (cs) "Jen kategorie, které už mají odpovědi" else "Only categories with recorded answers"
        ) {
            CategoryAccuracyChart(
                stats = dashboard.categoryStats,
                cs = cs
            )
        }

        DashboardCard(
            title = if (cs) "Historie kol" else "Round history",
            subtitle = if (cs) "Posledních až 8 dokončených kol" else "Up to 8 most recent completed rounds"
        ) {
            RoundHistoryList(
                rounds = dashboard.rounds.takeLast(8).reversed(),
                cs = cs
            )
        }

        DashboardCard(
            title = if (cs) "Nejčastěji chybované otázky" else "Most missed questions",
            subtitle = if (cs) "Otázky, které potřebují nejvíc opakování" else "Questions that need the most reinforcement"
        ) {
            MissedQuestionsList(
                questions = dashboard.missedQuestions,
                cs = cs
            )
        }

        StatCard(
            title = "Guess Who",
            lines = if (cs) {
                listOf(
                    "Uhodni českou nebo světovou osobnost podle až tří nápověd.",
                    "Můžeš tipnout jméno kdykoli nebo říct „další nápověda“.",
                    "1. nápověda = nejvíc XP, 3. nápověda = méně XP.",
                    "Vlastní level, XP, streak a 10 osobností v jednom kole."
                )
            } else {
                listOf(
                    "Guess a Czech or world personality from up to three clues.",
                    "Guess the name at any time or say next hint.",
                    "First-hint answers earn the most XP.",
                    "Separate level, XP, streak and 10-person rounds."
                )
            }
        )

        StatCard(
            title = "Trivia Kids 6–12",
            lines = if (cs) {
                listOf(
                    "Samostatná dětská trivia hra bez letopočtů a těžké politiky.",
                    "Zvířata, filmy a postavy, auta, vesmír, příroda, tělo, sport, jídlo a logika.",
                    "Obtížnost se automaticky přizpůsobuje výkonu dítěte.",
                    "Má vlastní XP, level, streak a 10 otázek v jednom kole."
                )
            } else {
                listOf(
                    "Separate kids trivia without date-heavy or political questions.",
                    "Animals, movies and characters, cars, space, nature, body, sports, food and logic.",
                    "Difficulty adapts automatically to the child's performance.",
                    "Separate XP, level, streak and 10-question rounds."
                )
            }
        )

        StatCard(
            title = "Spelling Bee",
            lines = if (cs) {
                listOf(
                    "Nová hands-free hra s 5 úrovněmi obtížnosti.",
                    "Poslechneš si anglické slovo a jeho význam, potom ho nahlas vyhláskuješ.",
                    "Má vlastní XP, level, streak a 10 slov v jednom kole.",
                    "Spustíš ji v Android Auto nebo v Car Preview přepínačem Spelling Bee."
                )
            } else {
                listOf(
                    "New hands-free game with 5 difficulty levels.",
                    "Hear an English word and definition, then spell it aloud.",
                    "Separate XP, level, streak and 10-word rounds.",
                    "Start it from Android Auto or select Spelling Bee in Car Preview."
                )
            }
        )

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

        StatCard(
            title = if (cs) "Hlasové příkazy" else "Voice commands",
            lines = if (cs) {
                listOf(
                    "zopakuj • zopakuje otázku",
                    "přeskoč • další otázka",
                    "skóre • aktuální statistika",
                    "úroveň • level a XP",
                    "zastav hru • uloží a pozastaví"
                )
            } else {
                listOf(
                    "repeat • repeat question",
                    "skip • next question",
                    "score • current stats",
                    "level • level and XP",
                    "stop game • save and pause"
                )
            }
        )

        Text(
            text = if (cs) {
                "Historické grafy se začnou plnit od prvního kola dokončeného ve v0.12."
            } else {
                "Historical charts start filling from the first round completed in v0.12."
            },
            color = Color(0xFF8C96A4),
            fontSize = 13.sp
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCarPreview,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(if (cs) "Car Preview" else "Car Preview")
            }
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(if (cs) "Nastavit / otevřít hry" else "Setup / Open Games")
            }
            SmallActionButton(
                text = if (cs) "Debug logy" else "Debug Logs",
                active = false,
                onClick = onDebugLogs
            )
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
private fun KpiStrip(
    profile: TriviaGameEngine.Profile,
    cs: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KpiCard(
            modifier = Modifier.weight(1f),
            label = "XP",
            value = profile.xp.toString()
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            label = if (cs) "Úspěšnost" else "Accuracy",
            value = profile.accuracy.toString() + "%"
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            label = if (cs) "Nejlepší série" else "Best streak",
            value = profile.bestStreak.toString()
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            label = if (cs) "Kola" else "Rounds",
            value = profile.roundsCompleted.toString()
        )
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF171B22),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = Color(0xFF99A3B0),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF11151B),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color(0xFF8F99A8),
                fontSize = 13.sp
            )
            content()
        }
    }
}

@Composable
private fun XpProgressChart(
    points: List<Pair<Int, Int>>,
    cs: Boolean
) {
    if (points.isEmpty()) {
        EmptyDataText(
            if (cs) "Zatím není dokončené kolo s historickým záznamem."
            else "No completed round with historical data yet."
        )
        return
    }

    val visible = points.takeLast(20)
    val maxXp = max(visible.maxOf { it.second }, 1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val left = 14f
        val right = size.width - 14f
        val top = 12f
        val bottom = size.height - 16f
        val width = right - left
        val height = bottom - top

        for (i in 0..4) {
            val y = top + height * i / 4f
            drawLine(
                color = Color(0xFF252B35),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1f
            )
        }

        val path = Path()
        visible.forEachIndexed { index, point ->
            val x = if (visible.size == 1) {
                left + width / 2f
            } else {
                left + width * index / (visible.size - 1).toFloat()
            }
            val y = bottom - (point.second.toFloat() / maxXp.toFloat()) * height

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

            drawCircle(
                color = Color(0xFFE6EDF5),
                radius = 4.5f,
                center = Offset(x, y)
            )
        }

        drawPath(
            path = path,
            color = Color(0xFF9FB4CC),
            style = Stroke(width = 4f)
        )
    }

    val first = visible.first()
    val last = visible.last()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = (if (cs) "Kolo " else "Round ") + first.first + " • " + first.second + " XP",
            color = Color(0xFF8F99A8),
            fontSize = 12.sp
        )
        Text(
            text = (if (cs) "Kolo " else "Round ") + last.first + " • " + last.second + " XP",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryAccuracyChart(
    stats: List<TriviaGameEngine.CategoryStat>,
    cs: Boolean
) {
    val active = stats
        .filter { it.answered > 0 }
        .sortedByDescending { it.accuracy }

    if (active.isEmpty()) {
        EmptyDataText(
            if (cs) "Zatím nejsou odpovědi pro porovnání kategorií."
            else "No category answers recorded yet."
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        active.forEach { stat ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = localizedCategory(stat.category, cs),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = stat.accuracy.toString() + "% • " +
                            stat.correct + "/" + stat.answered,
                        color = Color(0xFF9DA8B5),
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .background(
                            color = Color(0xFF252B35),
                            shape = RoundedCornerShape(5.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(stat.accuracy.coerceIn(0, 100) / 100f)
                            .height(9.dp)
                            .background(
                                color = Color(0xFF9FB4CC),
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundHistoryList(
    rounds: List<TriviaGameEngine.RoundHistoryEntry>,
    cs: Boolean
) {
    if (rounds.isEmpty()) {
        EmptyDataText(
            if (cs) "Historie kol se začne ukládat po dokončení dalšího kola."
            else "Round history will appear after the next completed round."
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rounds.forEach { round ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF171B22),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = (if (cs) "Kolo " else "Round ") + round.roundNumber,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatRoundDate(round.completedAt, cs),
                            color = Color(0xFF86909D),
                            fontSize = 12.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = round.correct.toString() + "/" + round.answered +
                                " • " + round.accuracy + "%",
                            color = Color.White
                        )
                        Text(
                            text = "+" + round.xpEarned + " XP • " +
                                (if (cs) "série " else "streak ") + round.bestStreak,
                            color = Color(0xFF9DA8B5),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissedQuestionsList(
    questions: List<TriviaGameEngine.MissedQuestionStat>,
    cs: Boolean
) {
    if (questions.isEmpty()) {
        EmptyDataText(
            if (cs) "Zatím nemáš žádné chybované otázky."
            else "No missed questions yet."
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        questions.forEachIndexed { index, item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF171B22),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = Color(0xFF7F8A98),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.prompt,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = localizedCategory(item.category, cs) + " • " +
                                (if (cs) "chyby " else "misses ") + item.wrongCount +
                                " • " + item.accuracy + "%",
                            color = Color(0xFF929DAA),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDataText(text: String) {
    Text(
        text = text,
        color = Color(0xFF7F8996),
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 18.dp)
    )
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

private fun formatRoundDate(
    timestamp: Long,
    cs: Boolean
): String {
    val locale = if (cs) Locale("cs", "CZ") else Locale.US
    val pattern = if (cs) "d. M. HH:mm" else "MMM d, HH:mm"
    return SimpleDateFormat(pattern, locale).format(Date(timestamp))
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
        TriviaQuestionBank.Category.MOVIES -> "Filmy"
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

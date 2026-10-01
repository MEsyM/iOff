package com.dualactionwindows.dawdrive.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualactionwindows.dawdrive.R
import com.dualactionwindows.dawdrive.TriviaGameEngine
import com.dualactionwindows.dawdrive.TriviaQuestionBank

private enum class LoneScreen {
    HOME,
    GAMES,
    STATS,
    SETTINGS,
    DIAGNOSTICS
}

private data class ModuleCard(
    val icon: String,
    val title: String,
    val subtitle: String,
    val accent: Color
)

@Composable
fun DawDriveApp(
    profile: TriviaGameEngine.Profile,
    dashboard: TriviaGameEngine.DashboardData,
    achievementTitles: List<String>,
    onVideoClick: () -> Unit,
    onCarPreviewClick: () -> Unit,
    onCarGameClick: (String) -> Unit,
    onDebugLogsClick: () -> Unit,
    micPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    speechRecognitionAvailable: Boolean,
    drivingVoiceArmed: Boolean,
    onRequestVoicePermissions: () -> Unit,
    onSetDrivingVoiceArmed: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onFamilySetupClick: () -> Unit,
    onRoadVoiceStart: () -> Unit,
    onRoadVoiceStop: () -> Unit,
    onLanguageChange: (TriviaGameEngine.Language) -> Unit,
    onRefreshProfile: () -> Unit
) {
    var screen by remember { mutableStateOf(LoneScreen.HOME) }

    BackHandler(enabled = screen != LoneScreen.HOME) {
        screen = LoneScreen.HOME
    }

    LoneRiderTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = LoneRiderColors.Background
        ) {
            when (screen) {
                LoneScreen.HOME -> HomeScreen(
                    profile = profile,
                    onGames = { screen = LoneScreen.GAMES },
                    onDrive = onCarPreviewClick,
                    onMedia = onVideoClick,
                    onStats = { screen = LoneScreen.STATS },
                    onTools = { screen = LoneScreen.DIAGNOSTICS },
                    onSettings = { screen = LoneScreen.SETTINGS }
                )

                LoneScreen.GAMES -> GamesScreen(
                    onBack = { screen = LoneScreen.HOME },
                    onQuickTrivia = onRoadVoiceStart,
                    onSpellingBee = { onCarGameClick("spelling") },
                    onGuessWho = { onCarGameClick("guesswho") },
                    onKids = { onCarGameClick("kids") },
                    onFamily = onFamilySetupClick,
                    onOpenCar = onCarPreviewClick
                )

                LoneScreen.STATS -> StatsScreen(
                    profile = profile,
                    dashboard = dashboard,
                    achievementTitles = achievementTitles,
                    onBack = { screen = LoneScreen.HOME },
                    onRefresh = onRefreshProfile
                )

                LoneScreen.SETTINGS -> SettingsScreen(
                    profile = profile,
                    micPermissionGranted = micPermissionGranted,
                    notificationPermissionGranted = notificationPermissionGranted,
                    speechRecognitionAvailable = speechRecognitionAvailable,
                    drivingVoiceArmed = drivingVoiceArmed,
                    onRequestVoicePermissions = onRequestVoicePermissions,
                    onSetDrivingVoiceArmed = onSetDrivingVoiceArmed,
                    onOpenAppSettings = onOpenAppSettings,
                    onBack = { screen = LoneScreen.HOME },
                    onLanguageChange = onLanguageChange,
                    onFamilySetup = onFamilySetupClick,
                    onDiagnostics = { screen = LoneScreen.DIAGNOSTICS }
                )

                LoneScreen.DIAGNOSTICS -> DiagnosticsScreen(
                    onBack = { screen = LoneScreen.HOME },
                    onOpenLogs = onDebugLogsClick,
                    onStopVoice = onRoadVoiceStop,
                    onOpenCar = onCarPreviewClick
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    profile: TriviaGameEngine.Profile,
    onGames: () -> Unit,
    onDrive: () -> Unit,
    onMedia: () -> Unit,
    onStats: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp < 700

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF06101E), LoneRiderColors.Background)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandHeader(
            eyebrow = "DRIVING TOGETHER  •  PLAYING FURTHER",
            onSettings = onSettings
        )

        Text(
            text = "Ready for the next adventure?",
            color = LoneRiderColors.TextSecondary,
            fontSize = 15.sp
        )

        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("🚙", "Drive", "Car preview & road voice", LoneRiderColors.Blue),
                    onClick = onDrive
                )
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("🎮", "Games", "Play together on the road", LoneRiderColors.Purple),
                    onClick = onGames
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("♫", "Media", "Music, podcasts & web", LoneRiderColors.Pink),
                    onClick = onMedia
                )
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("▥", "Stats", "XP, streaks & progress", LoneRiderColors.Cyan),
                    onClick = onStats
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("⚙", "Tools", "Diagnostics & utilities", LoneRiderColors.Amber),
                    onClick = onTools
                )
                ProfileMiniCard(
                    modifier = Modifier.weight(1f),
                    profile = profile
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("🚙", "Drive", "Car preview & road voice", LoneRiderColors.Blue),
                    onClick = onDrive
                )
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("🎮", "Games", "Play together on the road", LoneRiderColors.Purple),
                    onClick = onGames
                )
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("♫", "Media", "Music, podcasts & web", LoneRiderColors.Pink),
                    onClick = onMedia
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("▥", "Stats", "XP, streaks & progress", LoneRiderColors.Cyan),
                    onClick = onStats
                )
                HomeTile(
                    modifier = Modifier.weight(1f),
                    card = ModuleCard("⚙", "Tools", "Diagnostics & utilities", LoneRiderColors.Amber),
                    onClick = onTools
                )
                ProfileMiniCard(
                    modifier = Modifier.weight(1f),
                    profile = profile
                )
            }
        }

        Text(
            text = "Voice first • Family friendly • Distraction aware",
            color = Color(0xFF708198),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun BrandHeader(
    eyebrow: String,
    onSettings: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WolfBadge()
            Column {
                Text(
                    text = "LONE RIDER",
                    color = LoneRiderColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Text(
                    text = eyebrow,
                    color = LoneRiderColors.Cyan,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp
                )
            }
        }

        if (onSettings != null) {
            CompactButton("SETTINGS", false, onSettings)
        }
    }
}

@Composable
private fun WolfBadge() {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .width(54.dp)
            .height(54.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.lone_rider_logo),
            contentDescription = "Lone Rider logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun HomeTile(
    modifier: Modifier,
    card: ModuleCard,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = card.accent.copy(alpha = 0.16f),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Text(card.icon, fontSize = 30.sp)
            Column {
                Text(card.title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    card.subtitle,
                    color = LoneRiderColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun ProfileMiniCard(
    modifier: Modifier,
    profile: TriviaGameEngine.Profile
) {
    Surface(
        modifier = modifier.height(150.dp),
        color = LoneRiderColors.SurfaceRaised,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("PROFILE", color = LoneRiderColors.TextSecondary, fontSize = 11.sp)
            Text("Level ${levelFor(profile.xp)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("${profile.xp} XP  •  ${profile.accuracy}% accuracy", color = LoneRiderColors.Cyan, fontSize = 13.sp)
        }
    }
}

@Composable
private fun GamesScreen(
    onBack: () -> Unit,
    onQuickTrivia: () -> Unit,
    onSpellingBee: () -> Unit,
    onGuessWho: () -> Unit,
    onKids: () -> Unit,
    onFamily: () -> Unit,
    onOpenCar: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp < 700

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Games", "Voice-first road games", onBack)

        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameCard(
                    modifier = Modifier.weight(1f).height(150.dp),
                    icon = "💡",
                    title = "Quick Trivia",
                    subtitle = "Fast facts. Big fun.",
                    accent = LoneRiderColors.Purple,
                    onClick = onQuickTrivia
                )
                GameCard(
                    modifier = Modifier.weight(1f).height(150.dp),
                    icon = "🐝",
                    title = "Spelling Bee",
                    subtitle = "Spell it out together.",
                    accent = LoneRiderColors.Amber,
                    onClick = onSpellingBee
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameCard(
                    modifier = Modifier.weight(1f).height(150.dp),
                    icon = "👤",
                    title = "Guess WHO",
                    subtitle = "People, clues & personalities.",
                    accent = LoneRiderColors.Green,
                    onClick = onGuessWho
                )
                GameCard(
                    modifier = Modifier.weight(1f).height(150.dp),
                    icon = "🏆",
                    title = "Family Quiz",
                    subtitle = "Round + Battle modes.",
                    accent = LoneRiderColors.Pink,
                    onClick = onFamily
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameCard(
                    modifier = Modifier.weight(1f).height(150.dp),
                    icon = "★",
                    title = "Trivia Kids",
                    subtitle = "Adaptive questions for 6–12.",
                    accent = LoneRiderColors.Blue,
                    onClick = onKids
                )
                GameCard(
                    modifier = Modifier.weight(1f).height(150.dp),
                    icon = "🚙",
                    title = "Car Game Hub",
                    subtitle = "Pick a game and control playback.",
                    accent = LoneRiderColors.Cyan,
                    onClick = onOpenCar
                )
            }
        } else {
            Row(
                modifier = Modifier.height(210.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GameCard(
                    modifier = Modifier.weight(1f),
                    icon = "💡",
                    title = "Quick Trivia",
                    subtitle = "Fast facts. Big fun.",
                    accent = LoneRiderColors.Purple,
                    onClick = onQuickTrivia
                )
                GameCard(
                    modifier = Modifier.weight(1f),
                    icon = "🐝",
                    title = "Spelling Bee",
                    subtitle = "Spell it out together.",
                    accent = LoneRiderColors.Amber,
                    onClick = onSpellingBee
                )
                GameCard(
                    modifier = Modifier.weight(1f),
                    icon = "👤",
                    title = "Guess WHO",
                    subtitle = "People, clues & personalities.",
                    accent = LoneRiderColors.Green,
                    onClick = onGuessWho
                )
            }

            Row(
                modifier = Modifier.height(210.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GameCard(
                    modifier = Modifier.weight(1f),
                    icon = "🏆",
                    title = "Family Quiz",
                    subtitle = "Round + Battle modes.",
                    accent = LoneRiderColors.Pink,
                    onClick = onFamily
                )
                GameCard(
                    modifier = Modifier.weight(1f),
                    icon = "★",
                    title = "Trivia Kids",
                    subtitle = "Adaptive questions for 6–12.",
                    accent = LoneRiderColors.Blue,
                    onClick = onKids
                )
                GameCard(
                    modifier = Modifier.weight(1f),
                    icon = "🚙",
                    title = "Car Game Hub",
                    subtitle = "Pick a game and control playback.",
                    accent = LoneRiderColors.Cyan,
                    onClick = onOpenCar
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    modifier: Modifier,
    icon: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.16f),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Text(icon, fontSize = 36.sp)
            Column {
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, color = LoneRiderColors.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatsScreen(
    profile: TriviaGameEngine.Profile,
    dashboard: TriviaGameEngine.DashboardData,
    achievementTitles: List<String>,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val cs = profile.language == TriviaGameEngine.Language.CS
    val compact = LocalConfiguration.current.screenWidthDp < 700
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(if (cs) "Statistiky" else "Stats", "Lone Rider profile", onBack)

        if (compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatKpi(Modifier.weight(1f), "XP", profile.xp.toString(), LoneRiderColors.Purple)
                StatKpi(Modifier.weight(1f), if (cs) "Úspěšnost" else "Accuracy", "${profile.accuracy}%", LoneRiderColors.Green)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatKpi(Modifier.weight(1f), if (cs) "Nejlepší série" else "Best streak", profile.bestStreak.toString(), LoneRiderColors.Amber)
                StatKpi(Modifier.weight(1f), if (cs) "Kola" else "Rounds", profile.roundsCompleted.toString(), LoneRiderColors.Blue)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatKpi(Modifier.weight(1f), "XP", profile.xp.toString(), LoneRiderColors.Purple)
                StatKpi(Modifier.weight(1f), if (cs) "Úspěšnost" else "Accuracy", "${profile.accuracy}%", LoneRiderColors.Green)
                StatKpi(Modifier.weight(1f), if (cs) "Nejlepší série" else "Best streak", profile.bestStreak.toString(), LoneRiderColors.Amber)
                StatKpi(Modifier.weight(1f), if (cs) "Kola" else "Rounds", profile.roundsCompleted.toString(), LoneRiderColors.Blue)
            }
        }

        SectionCard(if (cs) "Kategorie" else "Category stats") {
            val active = dashboard.categoryStats.filter { it.answered > 0 }
            if (active.isEmpty()) {
                Text(if (cs) "Zatím žádná data." else "No data yet.", color = LoneRiderColors.TextSecondary)
            } else {
                active.sortedByDescending { it.accuracy }.forEach { stat ->
                    CategoryBar(localizedCategory(stat.category, cs), stat.accuracy)
                }
            }
        }

        SectionCard(if (cs) "Achievementy" else "Achievements") {
            val items = if (achievementTitles.isEmpty()) {
                listOf(if (cs) "Dokonči první kolo a odemkni achievement." else "Finish your first round to unlock an achievement.")
            } else achievementTitles
            items.take(8).forEach { item ->
                Text("• $item", color = LoneRiderColors.TextSecondary, fontSize = 14.sp)
            }
        }

        SectionCard(if (cs) "Nejčastější chyby" else "Most missed") {
            val missed = dashboard.missedQuestions.take(5)
            if (missed.isEmpty()) {
                Text(if (cs) "Žádné chybované otázky." else "No missed questions yet.", color = LoneRiderColors.TextSecondary)
            } else {
                missed.forEach {
                    Text(
                        "${it.prompt}  •  ${it.accuracy}%",
                        color = LoneRiderColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        CompactButton(if (cs) "Obnovit" else "Refresh", true, onRefresh)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsScreen(
    profile: TriviaGameEngine.Profile,
    micPermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    speechRecognitionAvailable: Boolean,
    drivingVoiceArmed: Boolean,
    onRequestVoicePermissions: () -> Unit,
    onSetDrivingVoiceArmed: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onBack: () -> Unit,
    onLanguageChange: (TriviaGameEngine.Language) -> Unit,
    onFamilySetup: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val cs = profile.language == TriviaGameEngine.Language.CS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(if (cs) "Nastavení" else "Settings", "Lone Rider", onBack)

        SettingsRow(
            title = if (cs) "Jazyk" else "Language",
            subtitle = if (cs) "Čeština / English" else "English / Czech",
            actions = {
                CompactButton("EN", profile.language == TriviaGameEngine.Language.EN) {
                    onLanguageChange(TriviaGameEngine.Language.EN)
                }
                CompactButton("CZ", profile.language == TriviaGameEngine.Language.CS) {
                    onLanguageChange(TriviaGameEngine.Language.CS)
                }
            }
        )

        SettingsRow(
            title = if (cs) "Mikrofon" else "Microphone",
            subtitle = if (micPermissionGranted) {
                if (cs) "Povoleno • Lone Rider může poslouchat odpovědi" else "Allowed • Lone Rider can listen for answers"
            } else {
                if (cs) "Není povoleno • bez toho nelze odpovídat hlasem" else "Not allowed • voice answers will not work"
            },
            actions = {
                CompactButton(
                    if (micPermissionGranted) {
                        if (cs) "POVOLENO" else "ALLOWED"
                    } else {
                        if (cs) "POVOLIT" else "ALLOW"
                    },
                    micPermissionGranted,
                    onRequestVoicePermissions
                )
            }
        )

        SettingsRow(
            title = if (cs) "Driving Voice Mode" else "Driving Voice Mode",
            subtitle = when {
                !speechRecognitionAvailable ->
                    if (cs) "Rozpoznávání řeči není v telefonu dostupné" else "Speech recognition is not available on this phone"
                !micPermissionGranted ->
                    if (cs) "Nejdřív povol mikrofon" else "Allow microphone access first"
                drivingVoiceArmed ->
                    if (cs) "AKTIVNÍ • hands-free režim je připraven pro jízdu" else "ACTIVE • hands-free mode is ready for driving"
                else ->
                    if (cs) "Vypnuto • aktivuj před jízdou" else "Off • arm it before driving"
            },
            actions = {
                if (!micPermissionGranted) {
                    CompactButton(
                        if (cs) "POVOLIT MIC" else "ALLOW MIC",
                        false,
                        onRequestVoicePermissions
                    )
                } else {
                    CompactButton(
                        if (drivingVoiceArmed) {
                            if (cs) "VYPNOUT" else "TURN OFF"
                        } else {
                            if (cs) "AKTIVOVAT" else "ARM"
                        },
                        drivingVoiceArmed,
                        { onSetDrivingVoiceArmed(!drivingVoiceArmed) }
                    )
                }
            }
        )

        SettingsRow(
            title = if (cs) "Notifikace" else "Notifications",
            subtitle = if (notificationPermissionGranted) {
                if (cs) "Povoleno • uvidíš stav Road Voice" else "Allowed • Road Voice status can be shown"
            } else {
                if (cs) "Doporučeno povolit kvůli běhu na pozadí" else "Recommended for background Road Voice status"
            },
            actions = {
                CompactButton(
                    if (notificationPermissionGranted) {
                        if (cs) "POVOLENO" else "ALLOWED"
                    } else {
                        if (cs) "POVOLIT" else "ALLOW"
                    },
                    notificationPermissionGranted,
                    onRequestVoicePermissions
                )
            }
        )

        SettingsRow(
            title = if (cs) "Systémová oprávnění" else "System permissions",
            subtitle = if (cs) "Otevře nastavení aplikace v Androidu" else "Open Android app permissions and settings",
            actions = {
                CompactButton(if (cs) "OTEVŘÍT" else "OPEN", false, onOpenAppSettings)
            }
        )

        SettingsRow(
            title = if (cs) "Rodinný režim" else "Family mode",
            subtitle = if (cs) "Hráči, jména, obtížnost a Battle" else "Players, names, difficulty and Battle",
            actions = { CompactButton(if (cs) "NASTAVIT" else "SET UP", true, onFamilySetup) }
        )

        SettingsRow(
            title = if (cs) "Zvuk" else "Audio",
            subtitle = if (cs) "Přehrávání přes audio systém auta" else "Playback through the car audio system"
        )

        SettingsRow(
            title = if (cs) "Přístupnost" else "Accessibility",
            subtitle = if (cs) "Velké prvky, vysoký kontrast, voice-first" else "Large targets, high contrast, voice-first"
        )

        SettingsRow(
            title = if (cs) "Diagnostika" else "Diagnostics",
            subtitle = if (cs) "Stav aplikace, mikrofon a logy" else "App status, microphone and logs",
            actions = { CompactButton(if (cs) "OTEVŘÍT" else "OPEN", false, onDiagnostics) }
        )
    }
}

@Composable
private fun DiagnosticsScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onStopVoice: () -> Unit,
    onOpenCar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Diagnostics", "Lone Rider system status", onBack)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(Modifier.weight(1f), "APP", "OK")
            StatusCard(Modifier.weight(1f), "MIC", "READY")
            StatusCard(Modifier.weight(1f), "AUDIO", "READY")
            StatusCard(Modifier.weight(1f), "CAR", "AVAILABLE")
        }

        SectionCard("Runtime") {
            Text("Package: com.dualactionwindows.dawdrive", color = LoneRiderColors.TextSecondary, fontSize = 13.sp)
            Text("Brand: Lone Rider", color = LoneRiderColors.TextSecondary, fontSize = 13.sp)
            Text("Voice loop: Android SpeechRecognizer + TTS", color = LoneRiderColors.TextSecondary, fontSize = 13.sp)
            Text("Car integration: Android media browser / car preview", color = LoneRiderColors.TextSecondary, fontSize = 13.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactButton("VIEW LOGS", true, onOpenLogs)
            CompactButton("OPEN CAR", false, onOpenCar)
            CompactButton("STOP VOICE", false, onStopVoice)
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactButton("‹ BACK", false, onBack)
            Column {
                Text(title, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = LoneRiderColors.TextSecondary, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WolfBadge()
            Text("LONE RIDER", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun StatKpi(
    modifier: Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier.height(100.dp),
        color = accent.copy(alpha = 0.13f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, color = LoneRiderColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LoneRiderColors.Surface,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun CategoryBar(
    title: String,
    accuracy: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = LoneRiderColors.TextSecondary, fontSize = 13.sp)
            Text("$accuracy%", color = Color.White, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFF172234), RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((accuracy.coerceIn(0, 100)) / 100f)
                    .height(8.dp)
                    .background(LoneRiderColors.Cyan, RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    actions: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LoneRiderColors.Surface,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = LoneRiderColors.TextSecondary, fontSize = 12.sp)
            }
            if (actions != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions()
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier.height(90.dp),
        color = LoneRiderColors.Green.copy(alpha = 0.10f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, color = LoneRiderColors.TextSecondary, fontSize = 11.sp)
            Text(value, color = LoneRiderColors.Green, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompactButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) LoneRiderColors.Blue else LoneRiderColors.SurfaceRaised,
            contentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

private fun levelFor(xp: Int): Int = (xp / 1000) + 1

private fun localizedCategory(
    category: TriviaQuestionBank.Category,
    cs: Boolean
): String {
    if (!cs) return category.key.replaceFirstChar { it.uppercase() }
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

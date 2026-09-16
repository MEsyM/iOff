package cz.ioff.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.ioff.app.data.IOffRepository
import cz.ioff.app.domain.focus.FocusPhase
import cz.ioff.app.system.SettingsNavigator
import cz.ioff.app.ui.components.IOffBottomBar
import cz.ioff.app.ui.components.MainTab
import cz.ioff.app.ui.screens.*
import cz.ioff.app.ui.screens.focus.*
import cz.ioff.app.ui.theme.IOffBackground
import kotlinx.coroutines.delay

private enum class DetailDestination { NONE, HEALTH, SHUTDOWN, PROTECTED_APPS }

@Composable
fun IOffApp(
    repository: IOffRepository,
    focusViewModel: FocusViewModel,
    settingsNavigator: SettingsNavigator,
    showLaunchScreen: Boolean = true
) {
    var launchVisible by rememberSaveable { mutableStateOf(showLaunchScreen) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    var detail by rememberSaveable { mutableStateOf(DetailDestination.NONE) }
    var revision by remember { mutableIntStateOf(0) }
    val focus by focusViewModel.uiState.collectAsStateWithLifecycle()
    revision

    BackHandler(enabled = detail != DetailDestination.NONE) { detail = DetailDestination.NONE }

    LaunchedEffect(launchVisible) {
        if (launchVisible) {
            delay(900)
            launchVisible = false
        }
    }

    if (launchVisible) {
        SplashScreen()
        return
    }

    Scaffold(
        containerColor = IOffBackground,
        bottomBar = {
            if (focus.phase != FocusPhase.COMPLETING) {
                IOffBottomBar(selectedTab) { tab ->
                    selectedTab = tab
                    detail = DetailDestination.NONE
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when {
                focus.phase == FocusPhase.ACTIVE -> ActiveFocusScreen(
                    state = focus,
                    onUrge = focusViewModel::recordUrge,
                    onParkIdea = { repository.parkIdea(it); revision++ },
                    onEnd = focusViewModel::endFocus
                )
                focus.phase == FocusPhase.COMPLETING -> FocusCompleteScreen(
                    state = focus,
                    onSave = { score, output ->
                        focusViewModel.saveCompletedSession(score, output)
                        revision++
                    }
                )
                detail == DetailDestination.HEALTH -> HealthScreen(repository, onBack = { detail = DetailDestination.NONE })
                detail == DetailDestination.SHUTDOWN -> ShutdownScreen(
                    repository = repository,
                    onBack = { detail = DetailDestination.NONE },
                    onSaved = { detail = DetailDestination.NONE; revision++ }
                )
                detail == DetailDestination.PROTECTED_APPS -> ProtectedAppsScreen(
                    repository = repository,
                    onBack = { detail = DetailDestination.NONE }
                )
                selectedTab == MainTab.TODAY -> TodayScreen(
                    repository = repository,
                    onStartFocus = { goal ->
                        focusViewModel.setGoal(goal)
                        selectedTab = MainTab.FOCUS
                    },
                    onHealth = { detail = DetailDestination.HEALTH }
                )
                selectedTab == MainTab.FOCUS -> FocusSetupScreen(
                    state = focus,
                    onGoalChange = focusViewModel::setGoal,
                    onDurationChange = focusViewModel::setDuration,
                    onStart = focusViewModel::startFocus,
                    onEnableDnd = settingsNavigator::openDoNotDisturbAccess
                )
                selectedTab == MainTab.IDEAS -> IdeasScreen(repository)
                selectedTab == MainTab.PROGRESS -> ProgressScreen(repository)
                else -> MoreScreen(
                    repository = repository,
                    settingsNavigator = settingsNavigator,
                    dndReady = focus.dndReady,
                    onHealth = { detail = DetailDestination.HEALTH },
                    onShutdown = { detail = DetailDestination.SHUTDOWN },
                    onProtectedApps = { detail = DetailDestination.PROTECTED_APPS },
                    onDataReset = { focusViewModel.recoverFocus(); revision++ }
                )
            }
        }
    }
}

package cz.ioff.app.screenshot

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import cz.ioff.app.*
import cz.ioff.app.data.IOffRepository
import cz.ioff.app.domain.focus.FocusPhase
import cz.ioff.app.domain.focus.FocusSession
import cz.ioff.app.system.SettingsNavigator
import cz.ioff.app.ui.screens.*
import cz.ioff.app.ui.screens.focus.*
import cz.ioff.app.ui.theme.IOffBackground
import cz.ioff.app.ui.theme.IOffTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class IOffScreenshotTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()
    private lateinit var context: Context
    private lateinit var repository: IOffRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val preferences = context.getSharedPreferences(IOffRepository.PREFERENCES_NAME, 0)
        preferences.edit().clear().commit()
        repository = IOffRepository(preferences)
        seed()
    }

    private fun seed() {
        repository.preferences.edit()
            .putInt("day", 4)
            .putString("one_thing_4", "Finish DAW proposal")
            .putInt(MetricKeys.experiment(4, "mins"), 135)
            .putInt(MetricKeys.experiment(4, "opens"), 14)
            .putInt(MetricKeys.experiment(4, "distracting"), 18)
            .putInt(MetricKeys.experiment(4, "urges"), 3)
            .putInt(MetricKeys.experiment(4, "shield"), 5)
            .putInt(MetricKeys.experiment(4, "bypasses"), 1)
            .putInt(MetricKeys.experiment(4, "sessions"), 2)
            .putInt(MetricKeys.experiment(4, "focusSum"), 17)
            .putString("ideas", IdeaCodec.encode(listOf(
                ParkedIdea(1_725_000_000_000L, "New window system for cabins", IdeaState.LATER),
                ParkedIdea(1_724_996_000_000L, "Autonomous trading agent v2", IdeaState.DO),
                ParkedIdea(1_724_900_000_000L, "Marketing campaign idea", IdeaState.LATER)
            )))
            .commit()
        DailyLifeStore.from(repository.preferences).save(
            4,
            DailyLife(
                true, true, true,
                listOf(SexEntry(1_725_000_000_000L, "Logged")),
                listOf(SportEntry(1L, "Gym - Strength", 45), SportEntry(2L, "Mountain biking", 80))
            )
        )
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { IOffTheme { Surface(Modifier.fillMaxSize(), color = IOffBackground) { content() } } }
        compose.waitForIdle()
        compose.onRoot(useUnmergedTree = true).captureRoboImage("src/test/screenshots/$name")
    }

    @Test fun today() = shot("01_today.png") { TodayScreen(repository, {}, {}) }
    @Test fun focusSetup() = shot("02_focus_setup.png") {
        FocusSetupScreen(FocusUiState(goal = "Finish DAW proposal", isLoading = false, dndReady = true), {}, {}, {}, {})
    }
    @Test fun activeFocus() = shot("03_active_focus.png") {
        ActiveFocusScreen(FocusUiState(FocusPhase.ACTIVE, "Finish DAW proposal", 60, "x", 2_607_000, 2, true, null, false), {}, {}, {})
    }
    @Test fun focusComplete() = shot("04_focus_complete.png") {
        val session = FocusSession("x", 0, 3_600_000, 4, 60, 60, 2, null, "Finish DAW proposal", "", false)
        FocusCompleteScreen(FocusUiState(phase = FocusPhase.COMPLETING, completedSession = session, isLoading = false), { _, _ -> })
    }
    @Test fun progress() = shot("05_progress.png") { ProgressScreen(repository) }
    @Test fun ideas() = shot("06_idea_parking.png") { IdeasScreen(repository) }
    @Test fun shutdown() = shot("07_daily_shutdown.png") { ShutdownScreen(repository, {}, {}) }
    @Test fun more() = shot("08_more.png") { MoreScreen(repository, NoOpSettings, true, {}, {}, {}, {}) }
    @Test fun shield() = shot("09_shield.png") { ShieldScreen() }
    @Test fun splash() = shot("00_splash.png") { SplashScreen() }
    @Test fun health() = shot("10_health.png") { HealthScreen(repository, {}) }
    @Test fun protectedApps() = shot("11_protected_apps.png") {
        ProtectedAppsContent(
            apps = listOf(
                InstalledAppUi("com.instagram.android", "Instagram"),
                InstalledAppUi("com.android.chrome", "Chrome"),
                InstalledAppUi("com.google.android.youtube", "YouTube"),
                InstalledAppUi("com.spotify.music", "Spotify"),
                InstalledAppUi("com.whatsapp", "WhatsApp")
            ),
            protectedPackages = setOf("com.instagram.android", "com.android.chrome", "com.google.android.youtube")
        )
    }

    private object NoOpSettings : SettingsNavigator {
        override fun openDoNotDisturbAccess() = Unit
        override fun openAccessibilityAccess() = Unit
        override fun openUsageAccess() = Unit
        override fun openNotificationAccess() = Unit
    }
}

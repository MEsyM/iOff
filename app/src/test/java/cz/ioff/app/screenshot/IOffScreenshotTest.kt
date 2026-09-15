package cz.ioff.app.screenshot

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import cz.ioff.app.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class IOffScreenshotTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()
    private lateinit var context: Context
    private lateinit var store: Store

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ioff", 0).edit().clear().commit()
        store = Store(context)
        seed()
    }

    private fun seed() {
        store.p.edit()
            .putInt("day", 4)
            .putString("one_thing_4", "Ship iOff v0.5 release candidate")
            .putInt(MetricKeys.experiment(4, "mins"), 120)
            .putInt(MetricKeys.experiment(4, "opens"), 14)
            .putInt(MetricKeys.experiment(4, "distracting"), 18)
            .putInt(MetricKeys.experiment(4, "urges"), 3)
            .putInt(MetricKeys.experiment(4, "shield"), 5)
            .putInt(MetricKeys.experiment(4, "bypasses"), 1)
            .putInt(MetricKeys.experiment(4, "sessions"), 2)
            .putInt(MetricKeys.experiment(4, "focusSum"), 17)
            .putString("ideas", IdeaCodec.prepend("", 1_725_000_000_000L, "Voice capture for Idea Parking"))
            .commit()
        DailyLifeStore.from(store.p).save(
            4,
            DailyLife(
                true, true, true,
                listOf(SexEntry(1L, "Connection")),
                listOf(SportEntry(2L, "Trail ride", 45))
            )
        )
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { Theme { Surface(Modifier.fillMaxSize(), color = Bg) { content() } } }
        compose.waitForIdle()
        compose.onRoot(useUnmergedTree = true).captureRoboImage("src/test/screenshots/$name")
    }

    @Test fun today() = shot("01_today.png") { V05Today(store, {}, {}) }

    @Test fun focusSetup() = shot("02_focus_setup.png") {
        ScreenshotPage("Focus") {
            Text("Finish one thing.", style = MaterialTheme.typography.headlineLarge)
            Text("Everything else can wait.", color = Muted)
            ScreenshotCard {
                Text("WHAT ARE YOU FINISHING?", color = Muted)
                OutlinedTextField("Ship iOff v0.5 release candidate", {}, Modifier.fillMaxWidth(), enabled = false)
                Text("DURATION", color = Muted, modifier = Modifier.padding(top = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(30, 60, 90).forEach { FilterChip(it == 60, {}, { Text("$it min") }) } }
            }
            ScreenshotCard { Text("Focus Protection"); Text("Do Not Disturb is ready", color = Mint) }
            Button({}, Modifier.fillMaxWidth()) { Text("Begin Focus  →") }
        }
    }

    @Test fun activeFocus() = shot("03_active_focus.png") {
        Box(Modifier.fillMaxSize().background(Bg).padding(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 210.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("●  FOCUS ACTIVE", color = Mint)
                Text("42:17", style = MaterialTheme.typography.displayLarge)
                Text("Ship iOff v0.5 release candidate", style = MaterialTheme.typography.titleLarge)
                Text("One task. One screen.", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton({}) { Text("⚡ Urge") }; OutlinedButton({}) { Text("＋ Park idea") } }
            }
        }
    }

    @Test fun focusComplete() = shot("04_focus_complete.png") {
        ScreenshotPage("Complete") {
            Text("Protected.", color = Mint)
            Text("60 minutes of real work.", style = MaterialTheme.typography.headlineLarge)
            ScreenshotCard {
                OutlinedTextField("Release gate implemented and verified", {}, Modifier.fillMaxWidth(), enabled = false, label = { Text("What exists now?") })
                Text("FOCUS QUALITY", color = Muted, modifier = Modifier.padding(top = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (6..10).forEach { FilterChip(it == 9, {}, { Text("$it") }) } }
            }
            Button({}, Modifier.fillMaxWidth()) { Text("Save session  ✓") }
        }
    }

    @Test fun progress() = shot("05_progress.png") { AttentionProgress(store) }
    @Test fun ideaParking() = shot("06_idea_parking.png") { Ideas(store) {} }
    @Test fun dailyShutdown() = shot("07_daily_shutdown.png") { Shutdown(store) {} }

    @Test fun more() = shot("08_more.png") {
        ScreenshotPage("More") {
            Text("Protection & settings", style = MaterialTheme.typography.headlineLarge)
            listOf("Idea Parking", "Daily Shutdown", "Morning Protection", "Usage access", "Notification access", "Testing tools").forEach { label ->
                ScreenshotCard { Text(label, style = MaterialTheme.typography.titleMedium); Text("Configured for the v0.5 experiment", color = Muted) }
            }
        }
    }

    @Test fun shield() = shot("09_shield.png") {
        Box(Modifier.fillMaxSize().background(Bg).padding(32.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text("iOff", color = Mint, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                Text("Instagram can wait.", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(16.dp))
                Text("You chose to finish\n“Ship iOff v0.5 release candidate”\n\n42 min remaining", color = Muted)
                Spacer(Modifier.height(32.dp))
                Button({}, Modifier.fillMaxWidth()) { Text("RETURN TO FOCUS") }
                TextButton({}, Modifier.fillMaxWidth()) { Text("I REALLY NEED INSTAGRAM") }
            }
        }
    }
}

@androidx.compose.runtime.Composable private fun ScreenshotPage(title: String, content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(20.dp)) {
        Row { Text("iOff", color = Mint, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.weight(1f)); Text(title.uppercase(), color = Muted) }
        Spacer(Modifier.height(28.dp)); content()
    }
}

@androidx.compose.runtime.Composable private fun ScreenshotCard(content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Surface)) { Column(Modifier.padding(18.dp), content = content) }
}

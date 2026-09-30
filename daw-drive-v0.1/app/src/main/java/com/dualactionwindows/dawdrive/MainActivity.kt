package com.dualactionwindows.dawdrive

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.dualactionwindows.dawdrive.ui.DawDriveApp

class MainActivity : ComponentActivity() {

    private var resumeGameAfterPermission = false
    private var openGamesAfterPermission = false

    private val requestRoadVoicePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val micGranted =
                grants[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

            if (micGranted) {
                startRoadVoiceService(resumeGameAfterPermission)
                if (openGamesAfterPermission) {
                    openCarPreview()
                }
            }

            resumeGameAfterPermission = false
            openGamesAfterPermission = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val triviaEngine = TriviaGameEngine(this)

        setContent {
            var profile by remember {
                mutableStateOf(triviaEngine.profile())
            }
            var dashboard by remember {
                mutableStateOf(triviaEngine.dashboardData())
            }

            DawDriveApp(
                profile = profile,
                dashboard = dashboard,
                achievementTitles = profile.achievements
                    .map { triviaEngine.achievementTitle(it) }
                    .sorted(),
                onVideoClick = {
                    val targetDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
                    val options = ActivityOptions.makeBasic().apply {
                        launchDisplayId = targetDisplayId
                    }

                    startActivity(
                        Intent(this, VideoActivity::class.java),
                        options.toBundle()
                    )
                },
                onCarPreviewClick = {
                    openCarPreview()
                },
                onDebugLogsClick = {
                    startActivity(
                        Intent(this, DebugLogsActivity::class.java)
                    )
                },
                onFamilyGameClick = {
                    startActivity(
                        Intent(this, FamilyGameActivity::class.java)
                    )
                },
                onRoadVoiceStart = {
                    ensureRoadVoiceSetup(
                        resumeGame = false,
                        openGamesAfterSetup = true
                    )
                },
                onRoadVoiceStop = {
                    startService(
                        Intent(this, RoadGameMediaService::class.java)
                            .setAction(RoadGameMediaService.ACTION_STOP_VOICE)
                    )
                },
                onLanguageChange = { language ->
                    triviaEngine.setLanguage(language)
                    profile = triviaEngine.profile()
                    dashboard = triviaEngine.dashboardData()
                },
                onRefreshProfile = {
                    profile = triviaEngine.profile()
                    dashboard = triviaEngine.dashboardData()
                }
            )
        }

        handleRoadVoiceIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRoadVoiceIntent(intent)
    }

    private fun handleRoadVoiceIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ENABLE_ROAD_VOICE, false) == true) {
            ensureRoadVoiceSetup(
                resumeGame = intent.getBooleanExtra(EXTRA_RESUME_GAME, false),
                openGamesAfterSetup = true
            )
            intent.removeExtra(EXTRA_ENABLE_ROAD_VOICE)
        }
    }

    private fun ensureRoadVoiceSetup(
        resumeGame: Boolean,
        openGamesAfterSetup: Boolean = false
    ) {
        val missing = buildList {
            if (
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }

            if (
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missing.isEmpty()) {
            startRoadVoiceService(resumeGame)
            if (openGamesAfterSetup) {
                openCarPreview()
            }
        } else {
            resumeGameAfterPermission = resumeGame
            openGamesAfterPermission = openGamesAfterSetup
            requestRoadVoicePermissions.launch(missing.toTypedArray())
        }
    }

    private fun openCarPreview() {
        startActivity(
            Intent(this, CarPreviewActivity::class.java)
        )
    }

    private fun startRoadVoiceService(resumeGame: Boolean) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, RoadGameMediaService::class.java)
                .setAction(RoadGameMediaService.ACTION_START_VOICE)
                .putExtra(RoadGameMediaService.EXTRA_RESUME_GAME, resumeGame)
        )
    }

    companion object {
        const val EXTRA_ENABLE_ROAD_VOICE = "enable_road_voice"
        const val EXTRA_RESUME_GAME = "resume_game"
        const val EXTRA_NEEDS_MIC_PERMISSION = "needs_mic_permission"
    }
}

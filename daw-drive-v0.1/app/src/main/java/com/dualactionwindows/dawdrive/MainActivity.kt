package com.dualactionwindows.dawdrive

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.SpeechRecognizer
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

    private val prefs by lazy {
        getSharedPreferences("lone_rider_settings", MODE_PRIVATE)
    }

    private var drivingVoiceArmed by mutableStateOf(false)
    private var micPermissionGranted by mutableStateOf(false)
    private var notificationPermissionGranted by mutableStateOf(false)
    private var speechRecognitionAvailable by mutableStateOf(false)

    private var resumeGameAfterPermission = false
    private var openGamesAfterPermission = false
    private var pendingGameAfterPermission: String? = null

    private val requestRoadVoicePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val micGranted =
                grants[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

            refreshPermissionState()

            if (micGranted) {
                startRoadVoiceService(resumeGameAfterPermission)
                if (openGamesAfterPermission) {
                    openCarPreview(pendingGameAfterPermission)
                }
            }

            resumeGameAfterPermission = false
            openGamesAfterPermission = false
            pendingGameAfterPermission = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val triviaEngine = TriviaGameEngine(this)
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        refreshPermissionState()
        drivingVoiceArmed = prefs.getBoolean(KEY_DRIVING_VOICE_ARMED, false)

        if (drivingVoiceArmed && micPermissionGranted) {
            startRoadVoiceService(resumeGame = false)
        }

        setContent {
            var profile by remember {
                mutableStateOf(triviaEngine.profile())
            }
            var dashboard by remember {
                mutableStateOf(triviaEngine.dashboardData())
            }

            DawDriveApp(
                appVersion = appVersion,
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
                onCarGameClick = { game ->
                    openCarPreview(game)
                },
                onFamilySetupClick = {
                    startActivity(Intent(this, FamilySetupActivity::class.java))
                },
                onEnglishSetupClick = {
                    startActivity(Intent(this, EnglishSetupActivity::class.java))
                },
                onBrainTrainerClick = {
                    openCarPreview("brain")
                },
                onDebugLogsClick = {
                    startActivity(
                        Intent(this, DebugLogsActivity::class.java)
                    )
                },
                micPermissionGranted = micPermissionGranted,
                notificationPermissionGranted = notificationPermissionGranted,
                speechRecognitionAvailable = speechRecognitionAvailable,
                drivingVoiceArmed = drivingVoiceArmed,
                onRequestVoicePermissions = {
                    ensureRoadVoiceSetup(
                        resumeGame = false,
                        openGamesAfterSetup = false
                    )
                },
                onSetDrivingVoiceArmed = { armed ->
                    drivingVoiceArmed = armed
                    prefs.edit().putBoolean(KEY_DRIVING_VOICE_ARMED, armed).apply()

                    if (armed) {
                        ensureRoadVoiceSetup(
                            resumeGame = false,
                            openGamesAfterSetup = false
                        )
                    } else {
                        startService(
                            Intent(this, RoadGameMediaService::class.java)
                                .setAction(RoadGameMediaService.ACTION_STOP_VOICE)
                        )
                    }
                },
                onOpenAppSettings = {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + packageName)
                        )
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
                onTriviaCategoryToggle = { category, favorite ->
                    triviaEngine.setCategoryFavorite(category, favorite)
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

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRoadVoiceIntent(intent)
    }

    private fun refreshPermissionState() {
        micPermissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        notificationPermissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        speechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this)
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

    private fun openCarPreview(game: String? = null) {
        val intent = Intent(this, CarPreviewActivity::class.java)
        if (game != null) {
            intent.putExtra(CarPreviewActivity.EXTRA_GAME, game)
        }
        startActivity(intent)
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
        private const val KEY_DRIVING_VOICE_ARMED = "driving_voice_armed"
        const val EXTRA_ENABLE_ROAD_VOICE = "enable_road_voice"
        const val EXTRA_RESUME_GAME = "resume_game"
        const val EXTRA_NEEDS_MIC_PERMISSION = "needs_mic_permission"
    }
}

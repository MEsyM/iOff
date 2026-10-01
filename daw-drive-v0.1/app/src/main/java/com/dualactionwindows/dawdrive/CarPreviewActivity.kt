package com.dualactionwindows.dawdrive

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.dualactionwindows.dawdrive.ui.CarPreviewScreen

class CarPreviewActivity : ComponentActivity() {

    private var controller: MediaControllerCompat? = null
    private lateinit var mediaBrowser: MediaBrowserCompat

    private var title by mutableStateOf("Quick Trivia")
    private var subtitle by mutableStateOf("Phone Car Preview")
    private var question by mutableStateOf("Press Play to start")
    private var isPlaying by mutableStateOf(false)
    private var connected by mutableStateOf(false)
    private var selectedGame by mutableStateOf(Game.TRIVIA)
    private var pendingAfterPermission: (() -> Unit)? = null

    private enum class Game(val mediaId: String) {
        TRIVIA(MEDIA_ID_TRIVIA),
        SPELLING(MEDIA_ID_SPELLING),
        GUESS_WHO(MEDIA_ID_GUESS_WHO),
        KIDS(MEDIA_ID_KIDS),
        FAMILY(MEDIA_ID_FAMILY)
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata ?: return
            title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                ?: selectedGameTitle()
            subtitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                ?: "Lone Rider"
            question = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: "Ready"
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startRoadVoice()
                pendingAfterPermission?.invoke()
            }
            pendingAfterPermission = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, RoadGameMediaService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    controller = MediaControllerCompat(
                        this@CarPreviewActivity,
                        mediaBrowser.sessionToken
                    ).also {
                        MediaControllerCompat.setMediaController(this@CarPreviewActivity, it)
                        it.registerCallback(controllerCallback)
                        controllerCallback.onMetadataChanged(it.metadata)
                        controllerCallback.onPlaybackStateChanged(it.playbackState)
                    }
                    connected = true
                }

                override fun onConnectionSuspended() {
                    connected = false
                }

                override fun onConnectionFailed() {
                    connected = false
                }
            },
            null
        )

        setContent {
            CarPreviewScreen(
                title = title,
                subtitle = subtitle,
                question = question,
                selectedGame = when (selectedGame) {
                    Game.TRIVIA -> "trivia"
                    Game.SPELLING -> "spelling"
                    Game.GUESS_WHO -> "guesswho"
                    Game.KIDS -> "kids"
                    Game.FAMILY -> "family"
                },
                isPlaying = isPlaying,
                connected = connected,
                onSelectTrivia = {
                    selectGame(Game.TRIVIA)
                },
                onSelectSpelling = {
                    selectGame(Game.SPELLING)
                },
                onSelectGuessWho = {
                    selectGame(Game.GUESS_WHO)
                },
                onSelectKids = {
                    selectGame(Game.KIDS)
                },
                onSelectFamily = {
                    selectGame(Game.FAMILY)
                },
                onFamilySetup = {
                    startActivity(Intent(this, FamilySetupActivity::class.java))
                },
                onPlayPause = {
                    runWithPermissions {
                        val transport = controller?.transportControls
                        if (isPlaying) {
                            transport?.pause()
                        } else {
                            transport?.playFromMediaId(selectedGame.mediaId, null)
                        }
                    }
                },
                onRepeat = {
                    runWithPermissions {
                        controller?.transportControls?.skipToPrevious()
                    }
                },
                onNext = {
                    runWithPermissions {
                        controller?.transportControls?.skipToNext()
                    }
                },
                onStop = {
                    stopRoadVoiceCompletely()
                },
                onClose = {
                    stopRoadVoiceCompletely()
                    finish()
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onStop() {
        controller?.unregisterCallback(controllerCallback)
        if (mediaBrowser.isConnected) {
            mediaBrowser.disconnect()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            stopRoadVoiceCompletely()
        }
        super.onDestroy()
    }

    private fun selectGame(game: Game) {
        if (selectedGame == game) return

        controller?.transportControls?.stop()
        selectedGame = game
        title = selectedGameTitle()
        subtitle = "Phone Car Preview"
        question = "Press Play to start"
        isPlaying = false
    }

    private fun selectedGameTitle(): String =
        when (selectedGame) {
            Game.TRIVIA -> "Quick Trivia"
            Game.SPELLING -> "Spelling Bee"
            Game.GUESS_WHO -> "Guess Who"
            Game.KIDS -> "Trivia Kids 6–12"
            Game.FAMILY -> "Family"
        }

    private fun runWithPermissions(action: () -> Unit) {
        val missing = buildList {
            if (
                ContextCompat.checkSelfPermission(
                    this@CarPreviewActivity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }

            if (
                ContextCompat.checkSelfPermission(
                    this@CarPreviewActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missing.isEmpty()) {
            startRoadVoice()
            action()
        } else {
            pendingAfterPermission = action
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private fun startRoadVoice() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, RoadGameMediaService::class.java)
                .setAction(RoadGameMediaService.ACTION_START_VOICE)
        )
    }

    private fun stopRoadVoiceCompletely() {
        controller?.transportControls?.stop()
        startService(
            Intent(this, RoadGameMediaService::class.java)
                .setAction(RoadGameMediaService.ACTION_STOP_VOICE)
        )
        isPlaying = false
    }

    companion object {
        private const val MEDIA_ID_TRIVIA = "trivia_career"
        private const val MEDIA_ID_SPELLING = "spelling_bee"
        private const val MEDIA_ID_GUESS_WHO = "guess_who"
        private const val MEDIA_ID_KIDS = "kids_trivia"
        private const val MEDIA_ID_FAMILY = "family_game"
    }
}

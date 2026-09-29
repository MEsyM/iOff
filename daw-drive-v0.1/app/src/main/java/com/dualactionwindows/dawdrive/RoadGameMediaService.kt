package com.dualactionwindows.dawdrive

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import java.util.Locale

class RoadGameMediaService : MediaBrowserServiceCompat(), TextToSpeech.OnInitListener {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var currentPack = "trivia"
    private var currentIndex = 0

    private val packs = mapOf(
        "trivia" to listOf(
            "Quick trivia. What is the capital city of Australia? Think of your answer, then say next.",
            "Which planet is known as the Red Planet? Think of your answer, then say next.",
            "How many sides does a hexagon have? Think of your answer, then say next.",
            "What is the largest ocean on Earth? Think of your answer, then say next.",
            "Which element has the chemical symbol O? Think of your answer, then say next."
        ),
        "words" to listOf(
            "Word challenge. Name three animals beginning with the letter B. Then say next.",
            "Name three countries beginning with the letter S. Then say next.",
            "Name four foods beginning with the letter C. Then say next.",
            "Name three professions beginning with the letter D. Then say next.",
            "Name five words that rhyme with light. Then say next."
        ),
        "math" to listOf(
            "Mental math. What is 17 plus 28? Then say next.",
            "What is 12 times 8? Then say next.",
            "What is 150 minus 67? Then say next.",
            "What is half of 246? Then say next.",
            "What is 25 percent of 200? Then say next."
        )
    )

    override fun onCreate() {
        super.onCreate()

        tts = TextToSpeech(this, this)

        mediaSession = MediaSessionCompat(this, "DAWDriveRoadGames").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    speakCurrent()
                }

                override fun onPause() {
                    tts.stop()
                    setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onStop() {
                    tts.stop()
                    setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
                }

                override fun onSkipToNext() {
                    move(1)
                }

                override fun onSkipToPrevious() {
                    move(-1)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    selectPack(mediaId)
                    currentIndex = 0
                    speakCurrent()
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    val q = query.orEmpty().lowercase(Locale.US)
                    currentPack = when {
                        "math" in q || "number" in q -> "math"
                        "word" in q || "letter" in q -> "words"
                        else -> "trivia"
                    }
                    currentIndex = 0
                    speakCurrent()
                }
            })

            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }

        sessionToken = mediaSession.sessionToken
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateMetadata()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            ttsReady = true
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }

        result.sendResult(
            mutableListOf(
                mediaItem("trivia", "Quick Trivia", "Short general-knowledge questions"),
                mediaItem("words", "Word Challenge", "Hands-free word prompts"),
                mediaItem("math", "Mental Math", "Short mental arithmetic")
            )
        )
    }

    private fun mediaItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()

        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun selectPack(mediaId: String?) {
        currentPack = if (packs.containsKey(mediaId)) mediaId!! else "trivia"
    }

    private fun move(delta: Int) {
        val items = packs[currentPack].orEmpty()
        if (items.isEmpty()) return
        currentIndex = (currentIndex + delta + items.size) % items.size
        speakCurrent()
    }

    private fun speakCurrent() {
        val items = packs[currentPack].orEmpty()
        if (items.isEmpty() || !ttsReady) {
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            return
        }

        updateMetadata()
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)

        tts.speak(
            items[currentIndex],
            TextToSpeech.QUEUE_FLUSH,
            null,
            "road-game-$currentPack-$currentIndex"
        )
    }

    private fun updateMetadata() {
        val title = when (currentPack) {
            "words" -> "Word Challenge"
            "math" -> "Mental Math"
            else -> "Quick Trivia"
        }

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "DAW Drive Road Games")
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, (currentIndex + 1).toLong())
                .build()
        )
    }

    private fun setPlaybackState(state: Int) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        private const val ROOT_ID = "road_games_root"
    }
}

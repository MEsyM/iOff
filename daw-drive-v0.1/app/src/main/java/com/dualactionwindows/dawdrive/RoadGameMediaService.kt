package com.dualactionwindows.dawdrive

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import java.util.Locale

class RoadGameMediaService : MediaBrowserServiceCompat(), TextToSpeech.OnInitListener {

    data class Challenge(
        val prompt: String,
        val acceptedAnswers: List<String> = emptyList(),
        val validator: ((String) -> Boolean)? = null
    )

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var awaitingAnswer = false
    private var currentPack = "trivia"
    private var currentIndex = 0
    private var score = 0
    private var attempted = 0

    private val packs = mapOf(
        "trivia" to listOf(
            Challenge("Quick trivia. What is the capital city of Australia?", listOf("canberra")),
            Challenge("Which planet is known as the Red Planet?", listOf("mars")),
            Challenge("How many sides does a hexagon have?", listOf("6", "six")),
            Challenge("What is the largest ocean on Earth?", listOf("pacific", "pacific ocean")),
            Challenge("Which element has the chemical symbol O?", listOf("oxygen"))
        ),
        "words" to listOf(
            Challenge("Word challenge. Name three animals beginning with the letter B.", validator = startsWithCountValidator('b', 3)),
            Challenge("Name three countries beginning with the letter S.", validator = startsWithCountValidator('s', 3)),
            Challenge("Name four foods beginning with the letter C.", validator = startsWithCountValidator('c', 4)),
            Challenge("Name three professions beginning with the letter D.", validator = startsWithCountValidator('d', 3))
        ),
        "math" to listOf(
            Challenge("Mental math. What is 17 plus 28?", listOf("45", "forty five", "forty-five")),
            Challenge("What is 12 times 8?", listOf("96", "ninety six", "ninety-six")),
            Challenge("What is 150 minus 67?", listOf("83", "eighty three", "eighty-three")),
            Challenge("What is half of 246?", listOf("123", "one hundred twenty three", "one hundred and twenty three")),
            Challenge("What is 25 percent of 200?", listOf("50", "fifty"))
        )
    )

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)

        mediaSession = MediaSessionCompat(this, "DAWDriveRoadGames").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = speakCurrent()

                override fun onPause() {
                    awaitingAnswer = false
                    tts.stop()
                    setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onStop() {
                    awaitingAnswer = false
                    tts.stop()
                    setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
                }

                override fun onSkipToNext() = move(1)
                override fun onSkipToPrevious() = move(-1)

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    selectPack(mediaId)
                    currentIndex = 0
                    score = 0
                    attempted = 0
                    speakCurrent()
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    val raw = query.orEmpty().trim()
                    val normalized = normalize(raw)

                    if (awaitingAnswer && normalized.isNotBlank()) {
                        evaluateAnswer(raw)
                        return
                    }

                    currentPack = when {
                        "math" in normalized || "number" in normalized -> "math"
                        "word" in normalized || "letter" in normalized -> "words"
                        else -> "trivia"
                    }
                    currentIndex = 0
                    score = 0
                    attempted = 0
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
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == FEEDBACK_UTTERANCE_ID) {
                        moveToNextAfterFeedback()
                    }
                }
            })
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }

        result.sendResult(
            mutableListOf(
                mediaItem("trivia", "Quick Trivia", "Answer using Android Auto voice search"),
                mediaItem("words", "Word Challenge", "Speak several words as your answer"),
                mediaItem("math", "Mental Math", "Say the result by voice")
            )
        )
    }

    private fun mediaItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun selectPack(mediaId: String?) {
        currentPack = if (packs.containsKey(mediaId)) mediaId!! else "trivia"
        awaitingAnswer = false
    }

    private fun move(delta: Int) {
        val items = packs[currentPack].orEmpty()
        if (items.isEmpty()) return
        awaitingAnswer = false
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
        awaitingAnswer = true

        val instruction = items[currentIndex].prompt +
            " Use the steering wheel voice button or Google Assistant and say your answer for DAW Drive."

        tts.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, QUESTION_UTTERANCE_ID)
    }

    private fun evaluateAnswer(rawAnswer: String) {
        val challenge = packs[currentPack].orEmpty().getOrNull(currentIndex) ?: return
        awaitingAnswer = false
        attempted += 1

        val normalized = normalize(rawAnswer)
        val correct = challenge.validator?.invoke(normalized)
            ?: challenge.acceptedAnswers.any {
                val accepted = normalize(it)
                normalized == accepted || normalized.contains(accepted)
            }

        if (correct) score += 1

        val feedback = if (correct) {
            "Correct. Score " + score + " out of " + attempted + ". Next question."
        } else {
            val answerHint = challenge.acceptedAnswers.firstOrNull()
            if (answerHint != null) {
                "Not quite. The answer is " + answerHint + ". Score " + score + " out of " + attempted + ". Next question."
            } else {
                "I couldn't validate that answer. Score " + score + " out of " + attempted + ". Next question."
            }
        }

        tts.speak(feedback, TextToSpeech.QUEUE_FLUSH, null, FEEDBACK_UTTERANCE_ID)
    }

    private fun moveToNextAfterFeedback() {
        val items = packs[currentPack].orEmpty()
        if (items.isEmpty()) return
        currentIndex = (currentIndex + 1) % items.size
        speakCurrent()
    }

    private fun updateMetadata() {
        val title = when (currentPack) {
            "words" -> "Word Challenge"
            "math" -> "Mental Math"
            else -> "Quick Trivia"
        }

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title + " - " + (currentIndex + 1))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "DAW Drive Road Games - Score " + score + "/" + attempted)
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

    private fun startsWithCountValidator(letter: Char, required: Int): (String) -> Boolean = { answer ->
        answer
            .split(Regex("[,;\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .count { it.startsWith(letter, ignoreCase = true) } >= required
    }

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        private const val ROOT_ID = "road_games_root"
        private const val QUESTION_UTTERANCE_ID = "road_game_question"
        private const val FEEDBACK_UTTERANCE_ID = "road_game_feedback"
    }
}

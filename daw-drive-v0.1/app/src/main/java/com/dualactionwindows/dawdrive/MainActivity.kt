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
import androidx.core.content.ContextCompat
import com.dualactionwindows.dawdrive.ui.DawDriveApp

class MainActivity : ComponentActivity() {

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRoadVoiceService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DawDriveApp(
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
                onRoadVoiceStart = {
                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startRoadVoiceService()
                    } else {
                        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onRoadVoiceStop = {
                    startService(
                        Intent(this, RoadGameMediaService::class.java)
                            .setAction(RoadGameMediaService.ACTION_STOP_VOICE)
                    )
                }
            )
        }
    }

    private fun startRoadVoiceService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, RoadGameMediaService::class.java)
                .setAction(RoadGameMediaService.ACTION_START_VOICE)
        )
    }
}

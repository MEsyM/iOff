package com.dualactionwindows.dawdrive

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dualactionwindows.dawdrive.ui.DawDriveApp

class MainActivity : ComponentActivity() {
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
                }
            )
        }
    }
}

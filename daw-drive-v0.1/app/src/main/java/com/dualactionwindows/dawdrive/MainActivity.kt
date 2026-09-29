package com.dualactionwindows.dawdrive

import android.content.Intent
import android.os.Bundle
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
                    startActivity(Intent(this, VideoActivity::class.java))
                }
            )
        }
    }
}

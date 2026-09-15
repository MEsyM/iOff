package cz.ioff.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cz.ioff.app.data.IOffRepository
import cz.ioff.app.data.SharedPreferencesFocusRepository
import cz.ioff.app.domain.focus.DefaultFocusRecovery
import cz.ioff.app.system.AndroidDndController
import cz.ioff.app.system.AndroidSettingsNavigator
import cz.ioff.app.ui.IOffApp
import cz.ioff.app.ui.screens.focus.FocusViewModel
import cz.ioff.app.ui.theme.IOffTheme
import cz.ioff.app.util.SystemClock

class MainActivity : ComponentActivity() {
    private lateinit var focusViewModel: FocusViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val preferences = getSharedPreferences(IOffRepository.PREFERENCES_NAME, MODE_PRIVATE)
        val appRepository = IOffRepository(preferences)
        val focusRepository = SharedPreferencesFocusRepository(preferences)
        val dndController = AndroidDndController(this, preferences)
        focusViewModel = ViewModelProvider(
            this,
            FocusViewModelFactory(
                repository = focusRepository,
                dndController = dndController,
                experimentDay = appRepository::day
            )
        )[FocusViewModel::class.java]

        setContent {
            IOffTheme {
                IOffApp(
                    repository = appRepository,
                    focusViewModel = focusViewModel,
                    settingsNavigator = AndroidSettingsNavigator(this)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::focusViewModel.isInitialized) focusViewModel.refreshSystemState()
    }
}

private class FocusViewModelFactory(
    private val repository: SharedPreferencesFocusRepository,
    private val dndController: AndroidDndController,
    private val experimentDay: () -> Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FocusViewModel(
        repository = repository,
        recovery = DefaultFocusRecovery(repository),
        dnd = dndController,
        clock = SystemClock,
        experimentDay = experimentDay
    ) as T
}

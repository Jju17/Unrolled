package dev.rahier.unrolled

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository: PhotoRepository = MediaStorePhotoRepository(applicationContext)
        val settingsRepository: SettingsRepository = SharedPrefsSettingsRepository(applicationContext)
        val progressRepository: SwipeProgressRepository =
            SharedPrefsSwipeProgressRepository(applicationContext)
        val statsRepository: StatsRepository = SharedPrefsStatsRepository(applicationContext)
        val reminderScheduler: ReminderScheduler = AlarmReminderScheduler(applicationContext)
        val factory = SwipeViewModel.Factory(
            photoRepository = repository,
            settingsRepository = settingsRepository,
            progressRepository = progressRepository,
            statsRepository = statsRepository,
            reminderScheduler = reminderScheduler
        )
        val locationDescriber = LocationDescriber(applicationContext, repository)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    val viewModel: SwipeViewModel = viewModel(factory = factory)
                    CompositionLocalProvider(
                        LocalLocationDescriber provides locationDescriber
                    ) {
                        SwipeScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

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

        // Repository wraps applicationContext — safe to let the VM hold
        // the reference across config changes.
        val repository: PhotoRepository = MediaStorePhotoRepository(applicationContext)
        val settingsRepository: SettingsRepository = SharedPrefsSettingsRepository(applicationContext)
        val factory = SwipeViewModel.Factory(
            photoRepository = repository,
            settingsRepository = settingsRepository
        )
        // Bound to the application context so it survives config changes
        // alongside the VM's repository reference.
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

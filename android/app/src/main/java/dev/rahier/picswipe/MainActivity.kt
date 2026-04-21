package dev.rahier.picswipe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Repository wraps applicationContext — safe to let the VM hold
        // the reference across config changes.
        val repository = PhotoRepository(applicationContext)
        val factory = SwipeViewModel.Factory(photoRepository = repository)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    val viewModel: SwipeViewModel = viewModel(factory = factory)
                    SwipeScreen(viewModel = viewModel)
                }
            }
        }
    }
}

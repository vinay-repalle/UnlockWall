package io.github.vinayrepalle.unlockwall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import io.github.vinayrepalle.unlockwall.ui.UnlockWallScreens
import io.github.vinayrepalle.unlockwall.ui.UnlockWallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Picks dark or light system bar icons to match the phone's setting.
        enableEdgeToEdge()
        setContent {
            UnlockWallTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    UnlockWallScreens(Modifier.padding(insets))
                }
            }
        }
    }
}

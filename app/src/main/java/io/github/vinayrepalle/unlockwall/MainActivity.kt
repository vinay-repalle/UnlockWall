package io.github.vinayrepalle.unlockwall

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import io.github.vinayrepalle.unlockwall.ui.MainScreen
import io.github.vinayrepalle.unlockwall.ui.UnlockWallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light for now, so dark icons on the system bars.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            UnlockWallTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    MainScreen(Modifier.padding(insets))
                }
            }
        }
    }
}

package io.nativestarter.showcase.bluesky

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.nativestarter.showcase.bluesky.shell.BlueskyRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge like RN (react-native-edge-to-edge): app content draws behind the system bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as BlueskyApp).container
        // Reads the encrypted session off the main thread; the UI stays in the restoring state until it finishes.
        lifecycleScope.launch { container.sessionManager.restore() }
        setContent { BlueskyRoot(container) }
    }
}

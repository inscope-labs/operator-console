package com.inscopelabs.buildengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.inscopelabs.buildengine.ui.navigation.AppNavHost
import com.inscopelabs.buildengine.ui.theme.OperatorConsoleTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity hosting the Compose navigation graph.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: FLAG_SECURE must be applied to this window so the console is
 * not observable in screenshots or the recent-apps thumbnail. See core/security/SecureWindow.kt.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OperatorConsoleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}

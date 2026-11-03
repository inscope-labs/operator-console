package com.inscopelabs.buildengine

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.inscopelabs.buildengine.core.security.ActivityBiometricPromptHost
import com.inscopelabs.buildengine.core.security.BiometricPromptHost
import com.inscopelabs.buildengine.core.security.SecureWindow
import com.inscopelabs.buildengine.ui.navigation.AppNavHost
import com.inscopelabs.buildengine.ui.theme.OperatorConsoleTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single Activity hosting the Compose navigation graph.
 *
 * Design review §13: FLAG_SECURE must be applied to this window so the console is
 * not observable in screenshots or the recent-apps thumbnail. See core/security/SecureWindow.kt.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val promptHost by lazy { ActivityBiometricPromptHost(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecureWindow.apply(window)
        enableEdgeToEdge()
        setContent {
            OperatorConsoleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BiometricPromptHost.registerHost(promptHost)
    }

    override fun onPause() {
        super.onPause()
        BiometricPromptHost.unregisterHost(promptHost)
    }
}

package com.inscopelabs.buildengine.core.security

import android.view.Window
import android.view.WindowManager

/**
 * Suppresses screenshots and the recent-apps thumbnail for the console window.
 *
 * Design review §13: FLAG_SECURE on the operator window.
 */
object SecureWindow {

    fun apply(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
}

package com.inscopelabs.buildengine.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.domain.model.ConnectionStatus
import com.inscopelabs.buildengine.ui.components.ConnectionStatusBanner
import com.inscopelabs.buildengine.ui.components.ErrorBanner
import com.inscopelabs.buildengine.ui.theme.OperatorConsoleTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 8 regression gate: default / not-configured posture is visible and does not
 * require a live gateway.
 *
 * Cold start with scaffold config shows the connection banner as "Not configured"
 * and the dashboard idle message — no network call is implied by composing these.
 */
@RunWith(AndroidJUnit4::class)
class NotConfiguredUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notConfiguredBannerIsDisplayed() {
        composeRule.setContent {
            OperatorConsoleTheme {
                ConnectionStatusBanner(
                    status = ConnectionStatus.NotConfigured,
                    onOpenSettings = {},
                )
            }
        }
        composeRule.onNodeWithTag("connection_not_configured").assertIsDisplayed()
    }

    @Test
    fun idleDataNotLoadedMessageIsDisplayed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.data_not_loaded)
        composeRule.setContent {
            OperatorConsoleTheme {
                Column {
                    ErrorBanner(message = message, onRetry = {})
                }
            }
        }
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun notConfiguredBannerAndIdleMessageComposeTogether() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val idle = context.getString(R.string.data_not_loaded)
        composeRule.setContent {
            OperatorConsoleTheme {
                Column {
                    ConnectionStatusBanner(
                        status = ConnectionStatus.NotConfigured,
                        onOpenSettings = {},
                    )
                    ErrorBanner(message = idle, onRetry = {})
                }
            }
        }
        composeRule.onNodeWithTag("connection_not_configured").assertIsDisplayed()
        composeRule.onNodeWithText(idle).assertIsDisplayed()
    }
}

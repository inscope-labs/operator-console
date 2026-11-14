package com.inscopelabs.buildengine.ui.publish

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.ui.theme.OperatorConsoleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 3.3 (T3-301, T3-303): end-to-end Compose flow driving [PublishConfirmScreen] into
 * [PublishProgressScreen] sharing one [PublishViewModel] against fake gateway repositories.
 */
@RunWith(AndroidJUnit4::class)
class PublishFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inAppConfirmFlowSubmitsOnceAndDisplaysVerifiedSuccessWithAuditRef() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opRepo = PublishTestFixtures.FakePublishOperationRepository().apply {
            onPublish = { _, _ ->
                OperationOutcome.Succeeded(
                    auditRef = "audit-flow-99",
                    headSha = PublishTestFixtures.FULL_HEAD_SHA,
                )
            }
        }
        val viewModel = PublishTestFixtures.createPublishViewModel(
            context = context,
            order = PublishTestFixtures.ORDER_ID,
            tier = AuthTier.IN_APP_CONFIRM,
            operationRepository = opRepo,
        )
        var showProgress by mutableStateOf(false)

        composeRule.setContent {
            OperatorConsoleTheme {
                if (!showProgress) {
                    PublishConfirmScreen(
                        viewModel = viewModel,
                        onSubmitted = { showProgress = true },
                    )
                } else {
                    PublishProgressScreen(viewModel = viewModel)
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("publish_head").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val prefix = PublishTestFixtures.FULL_HEAD_SHA.take(PublishSummary.TYPED_HEAD_LENGTH)
        composeRule.onNodeWithTag("publish_typed_head")
            .performScrollTo()
            .performTextInput(prefix)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("publish_confirm_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("operation_result_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals(1, opRepo.publishCalls)
        composeRule.onNodeWithTag("operation_result_message")
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.publish_result_succeeded))
        composeRule.onNodeWithTag("operation_result_audit_ref")
            .assertIsDisplayed()
            .assertTextContains("audit-flow-99")
    }

    @Test
    fun declinedConfirmationFlowSendsNothingAndDisplaysDeclinedBanner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opRepo = PublishTestFixtures.FakePublishOperationRepository()
        val viewModel = PublishTestFixtures.createPublishViewModel(
            context = context,
            order = PublishTestFixtures.ORDER_ID,
            tier = AuthTier.HARDWARE_CONFIRM,
            operationRepository = opRepo,
            biometricAnswer = false,
        )
        var showProgress by mutableStateOf(false)

        composeRule.setContent {
            OperatorConsoleTheme {
                if (!showProgress) {
                    PublishConfirmScreen(
                        viewModel = viewModel,
                        onSubmitted = { showProgress = true },
                    )
                } else {
                    PublishProgressScreen(viewModel = viewModel)
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("publish_head").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("publish_confirm_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("operation_result_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals(0, opRepo.publishCalls)
        composeRule.onNodeWithTag("operation_result_message")
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.publish_result_declined))
        composeRule.onNodeWithTag("operation_result_audit_ref").assertDoesNotExist()
    }
}

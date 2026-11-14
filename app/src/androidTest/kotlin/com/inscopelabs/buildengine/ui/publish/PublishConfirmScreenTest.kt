package com.inscopelabs.buildengine.ui.publish

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.ui.theme.OperatorConsoleTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 3.3 (T3-301, T3-303): Compose UI tests for [PublishConfirmScreen].
 */
@RunWith(AndroidJUnit4::class)
class PublishConfirmScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun awaitConfirmScreenReady() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("publish_head").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun showsFullHeadBranchPlanOrderUnavailableTierAndGuardSummaryWithDisabledButton() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = PublishTestFixtures.createPublishViewModel(
            context = context,
            order = null,
            tier = AuthTier.HARDWARE_CONFIRM,
        )

        composeRule.setContent {
            OperatorConsoleTheme {
                PublishConfirmScreen(viewModel = viewModel)
            }
        }
        awaitConfirmScreenReady()

        composeRule.onNodeWithTag("publish_head")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(PublishTestFixtures.FULL_HEAD_SHA)
        composeRule.onNodeWithTag("publish_branch")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(PublishTestFixtures.BRANCH_NAME)
        composeRule.onNodeWithText(PublishTestFixtures.PLAN_ID)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("publish_order")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.publish_confirm_order_unavailable))
        composeRule.onNodeWithTag("publish_tier")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.publish_tier_hardware))
        composeRule.onNodeWithTag("publish_guards")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(context.getString(R.string.publish_confirm_guards))
        composeRule.onNodeWithTag("publish_confirm_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun confirmButtonIsDisabledWhenTierIsUnknown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = PublishTestFixtures.createPublishViewModel(
            context = context,
            order = PublishTestFixtures.ORDER_ID,
            tier = null,
        )

        composeRule.setContent {
            OperatorConsoleTheme {
                PublishConfirmScreen(viewModel = viewModel)
            }
        }
        awaitConfirmScreenReady()

        composeRule.onNodeWithTag("publish_tier")
            .performScrollTo()
            .assertTextContains(context.getString(R.string.publish_tier_unknown))
        composeRule.onNodeWithTag("publish_confirm_button")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun inAppTierRequiresTyped7CharPrefixBeforeEnablingConfirm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = PublishTestFixtures.createPublishViewModel(
            context = context,
            order = PublishTestFixtures.ORDER_ID,
            tier = AuthTier.IN_APP_CONFIRM,
        )

        composeRule.setContent {
            OperatorConsoleTheme {
                PublishConfirmScreen(viewModel = viewModel)
            }
        }
        awaitConfirmScreenReady()

        composeRule.onNodeWithTag("publish_tier")
            .performScrollTo()
            .assertTextContains(context.getString(R.string.publish_tier_in_app))

        // Empty prefix keeps confirm disabled.
        composeRule.onNodeWithTag("publish_confirm_button")
            .performScrollTo()
            .assertIsNotEnabled()

        // 6 characters is not enough.
        val expectedPrefix = PublishTestFixtures.FULL_HEAD_SHA.take(PublishSummary.TYPED_HEAD_LENGTH)
        composeRule.onNodeWithTag("publish_typed_head")
            .performScrollTo()
            .performTextInput(expectedPrefix.take(6))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("publish_confirm_button")
            .performScrollTo()
            .assertIsNotEnabled()

        // Completing the 7th character enables confirm.
        composeRule.onNodeWithTag("publish_typed_head")
            .performTextInput(expectedPrefix.drop(6))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("publish_confirm_button")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun oneTapTriggersOneSubmission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gate = CompletableDeferred<OperationOutcome>()
        val opRepo = PublishTestFixtures.FakePublishOperationRepository().apply {
            onPublish = { _, _ -> gate.await() }
        }
        val viewModel = PublishTestFixtures.createPublishViewModel(
            context = context,
            order = PublishTestFixtures.ORDER_ID,
            tier = AuthTier.HARDWARE_CONFIRM,
            operationRepository = opRepo,
        )
        var submittedCount = 0

        composeRule.setContent {
            OperatorConsoleTheme {
                PublishConfirmScreen(
                    viewModel = viewModel,
                    onSubmitted = { submittedCount++ },
                )
            }
        }
        awaitConfirmScreenReady()

        composeRule.onNodeWithTag("publish_confirm_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(1, submittedCount)
        assertEquals(1, opRepo.publishCalls)
        composeRule.onNodeWithTag("publish_confirm_button").assertDoesNotExist()

        gate.complete(OperationOutcome.Succeeded("audit-one-tap", PublishTestFixtures.FULL_HEAD_SHA))
        composeRule.waitForIdle()
        assertEquals(1, opRepo.publishCalls)
    }
}

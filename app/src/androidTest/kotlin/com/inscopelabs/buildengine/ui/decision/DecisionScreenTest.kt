package com.inscopelabs.buildengine.ui.decision

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.write.DecideFrameworkUseCase
import com.inscopelabs.buildengine.ui.publish.PublishTestFixtures
import com.inscopelabs.buildengine.ui.theme.OperatorConsoleTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 3.4 (T3-404 / PR3-003 / PR3-025): Compose UI test for [DecisionScreen].
 */
@RunWith(AndroidJUnit4::class)
class DecisionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun decisionScreenShowsBlockerAndKeepsSubmitDisabledWhenFieldsPopulated() {
        val clock = FixedClock(1700000000L)
        val nonceSource = DeviceNonceSource(SecureRandomSource())
        val minter = CapabilityMinter(
            signer = HmacCapabilitySigner("k1", "test-secret-key-32-bytes-long!!"),
            nonceSource = nonceSource,
            clock = clock,
        )
        val useCase = DecideFrameworkUseCase(
            operationRepository = PublishTestFixtures.FakePublishOperationRepository(),
            confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
                capabilityMinter = minter,
                biometricGate = BiometricGate { _, _ -> true },
                clock = clock,
            ),
            nonceSource = nonceSource,
        )
        val viewModel = DecisionViewModel(SavedStateHandle(mapOf("decisionId" to "D-01")), useCase)

        composeRule.setContent {
            OperatorConsoleTheme {
                DecisionScreen(viewModel = viewModel)
            }
        }

        composeRule.onNodeWithTag("decision_contract_blocker").assertIsDisplayed()
        composeRule.onNodeWithTag("decision_plan_input").performScrollTo().performTextInput("atlas")
        composeRule.onNodeWithTag("decision_task_input").performScrollTo().performTextInput("10008")
        composeRule.onNodeWithTag("decision_order_input").performScrollTo().performTextInput("order-7731")
        composeRule.onNodeWithTag("decision_branch_input").performScrollTo().performTextInput("task-10008-foo")
        composeRule.onNodeWithTag("decision_head_input").performScrollTo().performTextInput("a".repeat(40))
        composeRule.onNodeWithTag("decision_attribution_input").performScrollTo().performTextInput("Architect")
        composeRule.onNodeWithTag("decision_rationale_input").performScrollTo().performTextInput("Policy alignment")
        composeRule.onNodeWithTag("decision_scope_input").performScrollTo().performTextInput("gateway")
        composeRule.onNodeWithTag("decision_evidence_input").performScrollTo().performTextInput("Audit log #1")

        composeRule.onNodeWithTag("decision_confirm_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
}

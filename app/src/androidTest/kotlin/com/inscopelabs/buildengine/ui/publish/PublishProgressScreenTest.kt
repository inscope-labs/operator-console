package com.inscopelabs.buildengine.ui.publish

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.ui.components.OperationResultBanner
import com.inscopelabs.buildengine.ui.theme.OperatorConsoleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 3.3 (T3-301, T3-303): Compose UI tests for [PublishProgressScreen] and
 * [OperationResultBanner].
 */
@RunWith(AndroidJUnit4::class)
class PublishProgressScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun distinctTextRenderedForEveryRefusalAndFailureKind() {
        val refusalBanners = listOf(
            PublishBanner(PublishBannerKind.SCOPE_REFUSED, code = "BRANCH_MISMATCH"),
            PublishBanner(PublishBannerKind.REPLAYED, code = "NONCE_REPLAYED"),
            PublishBanner(PublishBannerKind.HEAD_DRIFTED, code = "HEAD_DRIFT"),
            PublishBanner(PublishBannerKind.HALT_ACTIVE, code = "HALT_ACTIVE"),
            PublishBanner(PublishBannerKind.REFUSED, code = "DENIED_PATH"),
            PublishBanner(PublishBannerKind.CONFLICT, code = "SUBMISSION_IMMUTABLE"),
            PublishBanner(PublishBannerKind.TIMEOUT),
            PublishBanner(PublishBannerKind.UNCERTAIN),
            PublishBanner(PublishBannerKind.RECOVERY_REQUIRED),
            PublishBanner(PublishBannerKind.DECLINED),
            PublishBanner(PublishBannerKind.AUTH_FAILED),
            PublishBanner(PublishBannerKind.LOCKED_OUT),
            PublishBanner(PublishBannerKind.KEY_INVALIDATED),
            PublishBanner(PublishBannerKind.NOT_ENROLLED),
            PublishBanner(PublishBannerKind.TTL_EXPIRED),
            PublishBanner(PublishBannerKind.TIER_NOT_ALLOWED),
            PublishBanner(PublishBannerKind.ENVIRONMENT_MISMATCH, detail = "staging / test"),
            PublishBanner(PublishBannerKind.HEAD_UNAVAILABLE),
            PublishBanner(PublishBannerKind.INVALID_TARGET, detail = "missing order"),
        )

        var activeBanner by mutableStateOf(refusalBanners.first())
        composeRule.setContent {
            OperatorConsoleTheme {
                OperationResultBanner(
                    message = publishBannerMessage(activeBanner),
                    success = activeBanner.isSuccess,
                    auditRef = activeBanner.auditRef,
                )
            }
        }

        val renderedMessages = mutableSetOf<String>()
        for (banner in refusalBanners) {
            composeRule.runOnIdle { activeBanner = banner }
            composeRule.waitForIdle()
            val text = composeRule.onNodeWithTag("operation_result_message")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString(separator = "") { it.text }
            assertNotEquals("Banner message for ${banner.kind} must not be blank", "", text.trim())
            renderedMessages += text
        }

        assertEquals(
            "Every refusal kind must render distinct operator-facing text",
            refusalBanners.size,
            renderedMessages.size,
        )
    }

    @Test
    fun auditRefIsDisplayedWhenPresentAndOmittedWhenAbsent() {
        var auditRef by mutableStateOf<String?>("audit-20261113-42")
        composeRule.setContent {
            OperatorConsoleTheme {
                OperationResultBanner(
                    message = "Published.",
                    success = true,
                    auditRef = auditRef,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("operation_result_audit_ref")
            .assertIsDisplayed()
            .assertTextContains("audit-20261113-42")

        composeRule.runOnIdle { auditRef = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("operation_result_audit_ref").assertDoesNotExist()
    }

    @Test
    fun onlyVerifiedSuccessIsStyledAsSuccessOnProgressScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val opRepo = PublishTestFixtures.FakePublishOperationRepository()
        val viewModel = PublishTestFixtures.createPublishViewModel(
            context = context,
            order = PublishTestFixtures.ORDER_ID,
            tier = AuthTier.HARDWARE_CONFIRM,
            operationRepository = opRepo,
        )

        var primaryContainerColor = Color.Unspecified
        var errorContainerColor = Color.Unspecified

        composeRule.setContent {
            OperatorConsoleTheme {
                primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
                errorContainerColor = MaterialTheme.colorScheme.errorContainer
                PublishProgressScreen(viewModel = viewModel)
            }
        }

        // Wait for Ready, then confirm a verified success.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value is PublishUiState.Ready
        }
        opRepo.onPublish = { _, _ ->
            OperationOutcome.Succeeded(auditRef = "audit-ok-1", headSha = PublishTestFixtures.FULL_HEAD_SHA)
        }
        composeRule.runOnIdle { viewModel.confirm() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("operation_result_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("operation_result_audit_ref")
            .assertIsDisplayed()
            .assertTextContains("audit-ok-1")
        val successPixel = composeRule.onNodeWithTag("operation_result_banner")
            .captureToImage()
            .toPixelMap()[2, 2]
        assertEquals(primaryContainerColor.toArgb(), successPixel.toArgb())

        // Reload and test UncertainCompletion (even with auditRef): must be styled as error, not success.
        composeRule.runOnIdle { viewModel.load() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value is PublishUiState.Ready
        }
        opRepo.onPublish = { _, _ ->
            OperationOutcome.UncertainCompletion(
                idempotencyKey = "a".repeat(32),
                headSha = PublishTestFixtures.FULL_HEAD_SHA,
                auditRef = "audit-uncertain-2",
            )
        }
        composeRule.runOnIdle { viewModel.confirm() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("operation_result_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("operation_result_audit_ref")
            .assertIsDisplayed()
            .assertTextContains("audit-uncertain-2")
        val uncertainPixel = composeRule.onNodeWithTag("operation_result_banner")
            .captureToImage()
            .toPixelMap()[2, 2]
        assertEquals(errorContainerColor.toArgb(), uncertainPixel.toArgb())
        assertNotEquals(primaryContainerColor.toArgb(), uncertainPixel.toArgb())
    }
}

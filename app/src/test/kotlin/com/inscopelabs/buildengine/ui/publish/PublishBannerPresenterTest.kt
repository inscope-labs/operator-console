package com.inscopelabs.buildengine.ui.publish

import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.domain.usecase.write.PublishResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.3 (T3-301, T3-303): a rejection must be legible. The four core refusals are four
 * different banners, and only a verified success is presented as success (G-08).
 */
class PublishBannerPresenterTest {

    private fun executed(outcome: OperationOutcome) =
        PublishBannerPresenter.present(PublishResult.Executed(outcome))

    @Test
    fun `the four core refusals are four distinct banners`() {
        val scope = executed(OperationOutcome.Refused("BRANCH_MISMATCH")).kind
        val replay = executed(OperationOutcome.Conflict("NONCE_REPLAYED")).kind
        val drift = executed(OperationOutcome.Conflict("HEAD_DRIFT")).kind
        val halt = executed(OperationOutcome.Refused("HALT_ACTIVE")).kind

        assertEquals(PublishBannerKind.SCOPE_REFUSED, scope)
        assertEquals(PublishBannerKind.REPLAYED, replay)
        assertEquals(PublishBannerKind.HEAD_DRIFTED, drift)
        assertEquals(PublishBannerKind.HALT_ACTIVE, halt)
        assertEquals(4, setOf(scope, replay, drift, halt).size)
    }

    @Test
    fun `a moved head from the gateway is the head drift banner`() {
        assertEquals(PublishBannerKind.HEAD_DRIFTED, executed(OperationOutcome.Conflict("HEAD_MOVED")).kind)
    }

    @Test
    fun `other refusals and conflicts keep their code`() {
        val refused = executed(OperationOutcome.Refused("DENIED_PATH"))
        val conflict = executed(OperationOutcome.Conflict("SUBMISSION_IMMUTABLE"))
        assertEquals(PublishBannerKind.REFUSED, refused.kind)
        assertEquals("DENIED_PATH", refused.code)
        assertEquals(PublishBannerKind.CONFLICT, conflict.kind)
        assertEquals("SUBMISSION_IMMUTABLE", conflict.code)
    }

    @Test
    fun `only a verified success is a success and it carries the audit reference`() {
        val ok = executed(OperationOutcome.Succeeded(auditRef = "audit-9", headSha = "a".repeat(40)))
        assertTrue(ok.isSuccess)
        assertEquals("audit-9", ok.auditRef)

        val notSuccess = listOf(
            executed(OperationOutcome.Timeout("a".repeat(32))),
            executed(OperationOutcome.UncertainCompletion("a".repeat(32), headSha = "b".repeat(40), auditRef = "x")),
            executed(OperationOutcome.RecoveryRequired("a".repeat(32))),
            executed(OperationOutcome.Refused("HALT_ACTIVE")),
            executed(OperationOutcome.Conflict("NONCE_REPLAYED")),
        )
        notSuccess.forEach { assertFalse(it.kind.name, it.isSuccess) }
    }

    @Test
    fun `uncertain timeout and recovery are distinct banners`() {
        val kinds = setOf(
            executed(OperationOutcome.Timeout("a".repeat(32))).kind,
            executed(OperationOutcome.UncertainCompletion("a".repeat(32))).kind,
            executed(OperationOutcome.RecoveryRequired("a".repeat(32))).kind,
        )
        assertEquals(3, kinds.size)
    }

    @Test
    fun `nothing-was-sent results are distinct and never a success`() {
        val results = mapOf(
            PublishBannerKind.HEAD_DRIFTED to MintResult.HeadDrifted("b".repeat(40)),
            PublishBannerKind.HEAD_UNAVAILABLE to MintResult.HeadUnavailable("offline"),
            PublishBannerKind.DECLINED to MintResult.Declined,
            PublishBannerKind.AUTH_FAILED to MintResult.AuthFailed("no"),
            PublishBannerKind.LOCKED_OUT to MintResult.LockedOut,
            PublishBannerKind.KEY_INVALIDATED to MintResult.KeyInvalidated,
            PublishBannerKind.NOT_ENROLLED to MintResult.NotEnrolled,
            PublishBannerKind.TTL_EXPIRED to MintResult.ExpiredWhileConfirming,
            PublishBannerKind.TIER_NOT_ALLOWED to MintResult.TierNotAllowed(),
            PublishBannerKind.ENVIRONMENT_MISMATCH to MintResult.EnvironmentMismatch("test", "production"),
        )
        for ((kind, reason) in results) {
            val banner = PublishBannerPresenter.present(PublishResult.NotAuthorized(reason))
            assertEquals(kind, banner.kind)
            assertFalse(banner.isSuccess)
        }
        assertEquals(
            PublishBannerKind.INVALID_TARGET,
            PublishBannerPresenter.present(PublishResult.InvalidTarget("bad")).kind,
        )
    }

    @Test
    fun `a head drift before sending reports the live head`() {
        val live = "c".repeat(40)
        val banner = PublishBannerPresenter.present(PublishResult.NotAuthorized(MintResult.HeadDrifted(live)))
        assertEquals(live, banner.detail)
    }
}

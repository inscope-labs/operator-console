package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.dto.RevokeKeyRequestDto
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.remote.GatewayApi
import java.io.IOException

/**
 * Gateway-side revocation of the active device key (Stage 3.5, T3-503; BD-1.10).
 *
 * Invoked when the local key has been invalidated (biometric enrolment change,
 * lock-screen removal) and the console must move to the NeedsReenrollment flow:
 * the old `kid` is revoked at the gateway (operator bearer credential plus the
 * typed confirmation `REVOKE <kid>`, audited per BD-1.10) before the local
 * enrolment record is retired and the local key material is deleted.
 *
 * Fail closed (G-08): the local record is only retired after the gateway has
 * confirmed the revocation. If the gateway refuses or cannot be reached, nothing
 * is changed locally and the operator can retry.
 */
class RevokeDeviceKeyUseCase(
    private val gatewayApi: GatewayApi,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val deleteLocalKey: (alias: String) -> Unit = {},
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    sealed interface Result {
        /** The gateway confirmed the revocation; the local record was retired and the key deleted. */
        data class Revoked(val kid: String, val revokedAt: Long) : Result

        /** The gateway refused or answered with an error; nothing was changed locally. */
        data class GatewayRefused(val detail: String) : Result

        /** The gateway could not be reached; nothing was changed locally. */
        data class Unreachable(val detail: String) : Result

        /** There is no active enrolment to revoke. */
        data object NotEnrolled : Result
    }

    suspend operator fun invoke(reason: String): Result {
        if (reason.isBlank()) {
            return Result.GatewayRefused("A revocation reason is required")
        }
        val active = enrollmentStore.activeRecord() ?: return Result.NotEnrolled
        val kid = active.kid
        return try {
            val response = gatewayApi.revokeKey(
                RevokeKeyRequestDto(
                    kid = kid,
                    reason = reason,
                    confirmation = REVOCATION_CONFIRMATION_PREFIX + kid,
                ),
            )
            if (!response.revoked || response.kid != kid) {
                return Result.GatewayRefused("the gateway did not confirm revocation of $kid")
            }
            enrollmentStore.retire(kid, nowEpochSeconds())
            runCatching { deleteLocalKey(active.keyAlias) }
            Result.Revoked(kid, response.revokedAt)
        } catch (error: GatewayHttpException) {
            Result.GatewayRefused(
                "HTTP ${error.statusCode}: ${error.message ?: "the gateway refused the revocation"}",
            )
        } catch (error: IOException) {
            Result.Unreachable(error.message ?: "the gateway could not be reached")
        }
    }

    companion object {
        /** The typed confirmation gesture the gateway requires (contract §3.21). */
        const val REVOCATION_CONFIRMATION_PREFIX: String = "REVOKE "
    }
}

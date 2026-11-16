package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthorityPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthoritySnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.validation.EnvironmentIsolationValidator
import javax.inject.Inject

/**
 * MP-012: Resolves current operator credential posture, enrolled device key tier, and I-11
 * environment isolation parity before allowing transition or upload confirmation.
 */
class ResolveAuthorityUseCase @Inject constructor(
    private val authorityPort: AuthorityPort,
    private val environmentIsolationValidator: EnvironmentIsolationValidator,
) {

    data class AuthorityReport(
        val snapshot: AuthoritySnapshot,
        val environmentValidation: EnvironmentIsolationValidator.Result,
        val canAct: Boolean,
        val blockingReason: String?,
    )

    suspend operator fun invoke(operationName: String = "SET_IN_PROGRESS"): AuthorityReport {
        val snapshot = authorityPort.currentAuthority(operationName)
        val envResult = environmentIsolationValidator.validate(snapshot)
        val blockingReason = when {
            !envResult.matched -> envResult.reason
            !snapshot.hasOperatorCredential -> "Operator bearer credential missing; configure in Settings."
            !snapshot.enrolled -> "Device key not enrolled for ${snapshot.expectedEnvironment.wireValue}."
            snapshot.confirmationTier == null -> "No confirmation tier resolved for active device key."
            !snapshot.tierAllowed -> snapshot.tierReason
            else -> null
        }
        return AuthorityReport(
            snapshot = snapshot,
            environmentValidation = envResult,
            canAct = blockingReason == null,
            blockingReason = blockingReason,
        )
    }
}

package com.inscopelabs.buildengine.manualpipeline.data.integration

import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.security.ConfirmationTierPolicy
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.HealthSnapshotStore
import com.inscopelabs.buildengine.data.local.OperatorCredentialStore
import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthorityPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthoritySnapshot
import javax.inject.Inject

/**
 * Integrates host credential existence, device enrolment, confirmation tier policy (BD-3.1(a)),
 * and environment parity (I-11) into [AuthorityPort] without ever returning raw tokens.
 */
class OperatorCredentialAdapter @Inject constructor(
    private val credentialStore: OperatorCredentialStore,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val baseUrlStore: GatewayBaseUrlStore,
    private val healthSnapshotStore: HealthSnapshotStore,
    private val biometricGate: BiometricGate,
) : AuthorityPort {

    override suspend fun currentAuthority(operationName: String): AuthoritySnapshot {
        val expectedRaw = baseUrlStore.readExpectedEnvironment()
        val reportedRaw = healthSnapshotStore.read()?.environment
        val expectedEnv = Environment.fromWire(expectedRaw)
        val reportedEnv = Environment.fromWire(reportedRaw)

        val hasCredential = !credentialStore.read(expectedRaw).isNullOrBlank()
        val activeRecord = enrollmentStore.activeRecord(expectedRaw)
        val enrolled = activeRecord != null
        val authTier = activeRecord?.authTier?.let { AuthTier.fromWireValue(it) }
        val confirmationTier = authTier?.let { ConfirmationTier.fromWire(it.wireValue) }

        val hardwareAvailable = runCatching { biometricGate.hardwareConfirmAvailable() }.getOrDefault(false)
        val opType = OperationType.entries.firstOrNull {
            it.name.equals(operationName.trim(), ignoreCase = true)
        } ?: OperationType.SET_IN_PROGRESS

        val policy = ConfirmationTierPolicy(hardwareConfirmAvailable = { hardwareAvailable })
        val decision = policy.resolve(opType, authTier)
        val reason = if (authTier == null) {
            ConfirmationTierPolicy.explainForSummary(opType, null)
        } else {
            decision.reason
        }

        return AuthoritySnapshot(
            hasOperatorCredential = hasCredential,
            enrolled = enrolled,
            activeKid = activeRecord?.kid,
            confirmationTier = confirmationTier,
            hardwareConfirmAvailable = decision.hardwareAvailable,
            expectedEnvironment = expectedEnv,
            reportedEnvironment = reportedEnv,
            tierReason = reason,
            tierAllowed = enrolled && decision.allowed,
            explicitDowngrade = decision.explicitDowngrade,
        )
    }

    override suspend fun isEnvironmentMatched(): Boolean {
        val expectedRaw = baseUrlStore.readExpectedEnvironment()
        val reportedRaw = healthSnapshotStore.read()?.environment
        val expected = Environment.fromWire(expectedRaw)
        val reported = Environment.fromWire(reportedRaw)
        return expected != Environment.UNPROBED &&
            reported != Environment.UNPROBED &&
            expected == reported
    }
}

package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment

/**
 * Port querying operator authority, enrolment state, confirmation tier, and environment parity
 * without exposing raw bearer credentials to the domain layer.
 */
interface AuthorityPort {
    suspend fun currentAuthority(operationName: String = "SET_IN_PROGRESS"): AuthoritySnapshot
    suspend fun isEnvironmentMatched(): Boolean
}

data class AuthoritySnapshot(
    val hasOperatorCredential: Boolean,
    val enrolled: Boolean,
    val activeKid: String?,
    val confirmationTier: ConfirmationTier?,
    val hardwareConfirmAvailable: Boolean,
    val expectedEnvironment: Environment,
    val reportedEnvironment: Environment,
    val tierReason: String,
    val tierAllowed: Boolean = true,
    val explicitDowngrade: Boolean = false,
) {
    val environmentMatched: Boolean
        get() = expectedEnvironment != Environment.UNPROBED &&
            reportedEnvironment != Environment.UNPROBED &&
            expectedEnvironment == reportedEnvironment

    val canAct: Boolean
        get() = hasOperatorCredential && enrolled && environmentMatched && tierAllowed && confirmationTier != null
}

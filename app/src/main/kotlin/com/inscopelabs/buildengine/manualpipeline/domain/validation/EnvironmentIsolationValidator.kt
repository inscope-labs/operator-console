package com.inscopelabs.buildengine.manualpipeline.domain.validation

import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthoritySnapshot
import javax.inject.Inject

/**
 * Enforces I-11 environment isolation: expected and gateway-reported environments must match
 * cleanly and never default silently when unprobed or mismatched.
 */
class EnvironmentIsolationValidator @Inject constructor() {

    data class Result(
        val matched: Boolean,
        val expected: Environment,
        val reported: Environment,
        val reason: String,
    )

    fun validate(authority: AuthoritySnapshot): Result {
        val exp = authority.expectedEnvironment
        val rep = authority.reportedEnvironment
        return when {
            exp == Environment.UNPROBED || rep == Environment.UNPROBED -> Result(
                matched = false,
                expected = exp,
                reported = rep,
                reason = "Environment has not been probed yet (expected=${exp.wireValue}, gateway=${rep.wireValue}). Write actions disabled.",
            )
            exp != rep -> Result(
                matched = false,
                expected = exp,
                reported = rep,
                reason = "Environment mismatch: expected '${exp.wireValue}' but gateway reported '${rep.wireValue}'. All privileged actions disabled.",
            )
            else -> Result(
                matched = true,
                expected = exp,
                reported = rep,
                reason = "Environment verified (${exp.wireValue}).",
            )
        }
    }
}

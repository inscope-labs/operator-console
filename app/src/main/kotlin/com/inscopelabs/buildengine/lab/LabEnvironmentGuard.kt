package com.inscopelabs.buildengine.lab

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot

/**
 * Lab paths refuse any gateway whose environment is not `"test"` (ADR 0004).
 *
 * The guard is programmatic and cannot be overridden by UI controls. Call it before
 * every Lab scenario or mutant capability run.
 */
object LabEnvironmentGuard {

    const val ENVIRONMENT_MISMATCH_CODE: String = "ENVIRONMENT_MISMATCH"

    /**
     * @return success when [snapshot] reports a test environment; otherwise a failure
     *   with [ENVIRONMENT_MISMATCH_CODE] and operator-facing copy.
     */
    fun requireTestEnvironment(snapshot: GatewayHealthSnapshot?): AppResult<GatewayHealthSnapshot> {
        if (snapshot == null) {
            return AppResult.Failure(
                code = ENVIRONMENT_MISMATCH_CODE,
                message = "Lab Mode requires a successful health probe against a test gateway. " +
                    "Use Settings → Test connection first.",
            )
        }
        if (!snapshot.isTestEnvironment) {
            return AppResult.Failure(
                code = ENVIRONMENT_MISMATCH_CODE,
                message = "Lab Mode refuses this gateway: environment is " +
                    "\"${snapshot.environment.ifEmpty { "(missing)" }}\", not \"test\". " +
                    "Synthetic Lab traffic must never reach production (ADR 0004).",
            )
        }
        return AppResult.Success(snapshot)
    }
}

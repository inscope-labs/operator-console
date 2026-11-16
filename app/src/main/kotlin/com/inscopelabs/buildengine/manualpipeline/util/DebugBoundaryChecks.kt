package com.inscopelabs.buildengine.manualpipeline.util

import com.inscopelabs.buildengine.BuildConfig
import com.inscopelabs.buildengine.manualpipeline.domain.port.RedactorPort

/**
 * Debug-only invariant assertions ensuring no forbidden credential/capability strings cross
 * logging or persistence boundaries (I-04). No-ops in release builds.
 */
object DebugBoundaryChecks {

    fun assertNoSecretLeaks(value: String, label: String, redactor: RedactorPort) {
        if (BuildConfig.DEBUG && redactor.containsForbidden(value)) {
            error("Boundary violation ($label): forbidden secret pattern detected")
        }
    }

    fun assertValidIdempotencyKey(key: String) {
        if (BuildConfig.DEBUG && !IdempotencyKey.isValid(key)) {
            error("Boundary violation: expected 32-hex idempotency key, got length=${key.length}")
        }
    }
}

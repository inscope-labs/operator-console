package com.inscopelabs.buildengine.manualpipeline.domain.port

/**
 * Pure Kotlin time port for domain logic and tests.
 */
interface Clock {
    fun nowEpochMillis(): Long
    fun nowEpochSeconds(): Long = nowEpochMillis() / 1000L
}

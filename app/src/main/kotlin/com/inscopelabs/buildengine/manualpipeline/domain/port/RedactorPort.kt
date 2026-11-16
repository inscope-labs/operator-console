package com.inscopelabs.buildengine.manualpipeline.domain.port

/**
 * Port abstracting log and diagnostic string redaction (I-04).
 */
interface RedactorPort {
    fun redact(input: String): String
    fun containsForbidden(input: String): Boolean
}

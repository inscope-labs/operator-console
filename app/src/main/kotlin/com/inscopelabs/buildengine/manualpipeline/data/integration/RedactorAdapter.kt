package com.inscopelabs.buildengine.manualpipeline.data.integration

import com.inscopelabs.buildengine.manualpipeline.domain.port.RedactorPort
import com.inscopelabs.buildengine.util.Redactor
import javax.inject.Inject

/**
 * Adapter delegating to the host's [Redactor] gate (I-04).
 */
class RedactorAdapter @Inject constructor() : RedactorPort {
    override fun redact(input: String): String = Redactor.redact(input)
    override fun containsForbidden(input: String): Boolean = Redactor.containsForbidden(input)
}

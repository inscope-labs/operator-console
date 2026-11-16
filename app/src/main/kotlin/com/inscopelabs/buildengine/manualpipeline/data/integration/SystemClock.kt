package com.inscopelabs.buildengine.manualpipeline.data.integration

import com.inscopelabs.buildengine.core.time.Clock as HostClock
import com.inscopelabs.buildengine.manualpipeline.domain.port.Clock
import javax.inject.Inject

/**
 * Bridges host wall-clock [HostClock] into [Clock].
 */
class SystemClock @Inject constructor(
    private val hostClock: HostClock,
) : Clock {
    override fun nowEpochMillis(): Long = hostClock.now().toEpochMilli()
    override fun nowEpochSeconds(): Long = hostClock.nowEpochSeconds()
}

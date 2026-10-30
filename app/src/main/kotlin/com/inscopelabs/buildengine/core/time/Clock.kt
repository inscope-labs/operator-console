package com.inscopelabs.buildengine.core.time

import java.time.Instant

/**
 * Injectable time source, so capability TTL behaviour is testable.
 *
 * Design review §5: the gateway enforces a maximum TTL on the capability's expiry.
 *
 * The capability's `exp` field is an absolute Unix timestamp, so the clock is a
 * wall clock and not a monotonic one: a capability minted at `now + 300` must expire
 * five minutes after *wall-clock* now, not five minutes after the device's last
 * reboot. Injecting the source is what lets the TTL be exercised without waiting
 * five minutes for it.
 */
interface Clock {
    fun nowEpochSeconds(): Long
}

class SystemClock : Clock {
    override fun nowEpochSeconds(): Long = Instant.now().epochSecond
}

package com.inscopelabs.buildengine.core.time

/**
 * Injectable time source, so capability TTL behaviour is testable.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the gateway enforces a maximum TTL on the capability's expiry.
 */
interface Clock {
    fun nowEpochSeconds(): Long
}

class SystemClock : Clock {
    override fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000L
}

package com.inscopelabs.buildengine.core.network

import com.inscopelabs.buildengine.util.Redactor
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory ring buffer of recent gateway activity for the diagnostics panel.
 *
 * Recording is gated by [isEnabled]. Entries are always redacted before storage.
 * Nothing here is written to durable storage — support export is an explicit share.
 */
@Singleton
class GatewayActivityLog @Inject constructor() {

    private val lock = Any()
    private val buffer = ArrayDeque<GatewayActivityEvent>(MAX_EVENTS + 1)

    private val _events = MutableStateFlow<List<GatewayActivityEvent>>(emptyList())
    val events: StateFlow<List<GatewayActivityEvent>> = _events.asStateFlow()

    @Volatile
    var isEnabled: Boolean = false

    fun record(event: GatewayActivityEvent) {
        if (!isEnabled) return
        val safe = event.copy(
            path = Redactor.redact(event.path),
            detail = event.detail?.let { Redactor.redact(it) },
        )
        synchronized(lock) {
            buffer.addFirst(safe)
            while (buffer.size > MAX_EVENTS) {
                buffer.removeLast()
            }
            _events.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _events.value = emptyList()
        }
    }

    /** Newest-first redacted lines for share/export. */
    fun exportText(): String = synchronized(lock) {
        if (buffer.isEmpty()) {
            return@synchronized "(no gateway activity recorded)"
        }
        buffer.joinToString(separator = "\n") { event ->
            val ts = event.epochMillis
            "$ts ${event.summaryLine()}"
        }
    }

    companion object {
        const val MAX_EVENTS: Int = 50
    }
}

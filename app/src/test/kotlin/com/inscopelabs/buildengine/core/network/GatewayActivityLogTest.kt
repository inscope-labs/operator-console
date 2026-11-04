package com.inscopelabs.buildengine.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayActivityLogTest {

    @Test
    fun `records nothing when disabled`() {
        val log = GatewayActivityLog()
        log.isEnabled = false
        log.record(
            GatewayActivityEvent(
                method = "GET",
                path = "/health",
                outcome = GatewayActivityEvent.Outcome.SUCCESS,
                statusCode = 200,
                epochMillis = 1L,
            ),
        )
        assertTrue(log.events.value.isEmpty())
    }

    @Test
    fun `records when enabled and keeps newest first`() {
        val log = GatewayActivityLog()
        log.isEnabled = true
        log.record(
            GatewayActivityEvent("GET", "/health", GatewayActivityEvent.Outcome.SUCCESS, 200, null, 1L),
        )
        log.record(
            GatewayActivityEvent("GET", "/plans", GatewayActivityEvent.Outcome.REFUSED, 401, null, 2L),
        )
        val events = log.events.value
        assertEquals(2, events.size)
        assertEquals("/plans", events[0].path)
        assertEquals("/health", events[1].path)
    }

    @Test
    fun `caps at MAX_EVENTS`() {
        val log = GatewayActivityLog()
        log.isEnabled = true
        repeat(GatewayActivityLog.MAX_EVENTS + 10) { i ->
            log.record(
                GatewayActivityEvent("GET", "/p$i", GatewayActivityEvent.Outcome.SUCCESS, 200, null, i.toLong()),
            )
        }
        assertEquals(GatewayActivityLog.MAX_EVENTS, log.events.value.size)
    }

    @Test
    fun `export text is redacted free of bearer tokens`() {
        val log = GatewayActivityLog()
        log.isEnabled = true
        log.record(
            GatewayActivityEvent(
                method = "POST",
                path = "/publish",
                outcome = GatewayActivityEvent.Outcome.SUCCESS,
                statusCode = 200,
                detail = "Bearer supersecrettokenvalue",
                epochMillis = 1L,
            ),
        )
        val export = log.exportText()
        assertTrue(export.contains("[REDACTED]") || !export.contains("supersecrettokenvalue"))
        assertTrue(export.contains("POST /publish"))
    }

    @Test
    fun `clear empties the buffer`() {
        val log = GatewayActivityLog()
        log.isEnabled = true
        log.record(
            GatewayActivityEvent("GET", "/health", GatewayActivityEvent.Outcome.SUCCESS, 200, null, 1L),
        )
        log.clear()
        assertTrue(log.events.value.isEmpty())
    }
}

package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.time.FixedClock
import java.util.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals

import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The session repository's semantics.
 *
 * The properties that matter are the ones the threat model cares about: the session
 * lives in memory and nowhere else, it opens once, and ending it really ends it.
 */
class SessionRepositoryImplTest {

    private val clock = FixedClock()
    private val randomSource = SecureRandomSource(Random(20261030L))
    private val repository = SessionRepositoryImpl(clock, randomSource)

    @Test
    fun `no session exists before one is opened`() {
        assertNull(repository.current())
    }

    @Test
    fun `starting opens a session`() = runTest {
        val session = repository.start()
        assertEquals(session, repository.current())
        assertEquals(clock.nowEpochSeconds(), session.startedAtEpochSeconds)
    }

    @Test
    fun `starting twice returns the same session`() = runTest {
        // A configuration change must not rotate the session's identity under the
        // operator.
        val first = repository.start()
        val second = repository.start()
        assertEquals(first, second)
    }

    @Test
    fun `ending drops the session`() = runTest {
        repository.start()
        repository.end()
        assertNull(repository.current())
    }

    @Test
    fun `ending a session that never opened is harmless`() = runTest {
        repository.end()
        assertNull(repository.current())
    }

    @Test
    fun `a session reopened after an end is open again`() = runTest {
        repository.start()
        repository.end()
        val reopened = repository.start()
        assertEquals(reopened, repository.current())
    }

    @Test
    fun `the operator id is not empty`() = runTest {
        val session = repository.start()
        assert(session.operatorId.isNotEmpty())
    }
}

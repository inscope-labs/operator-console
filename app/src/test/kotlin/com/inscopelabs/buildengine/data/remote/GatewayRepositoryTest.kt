package com.inscopelabs.buildengine.data.remote

import com.inscopelabs.buildengine.core.network.ErrorMapper
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Covers the gateway error catalogue mapping, including the V5-V8 rejections:
 * a scope mismatch, a replayed capability, a drifted head and an active halt.
 *
 * SCAFFOLD PLACEHOLDER — replace with MockWebServer-backed assertions.
 */
class GatewayRepositoryTest {

    @Test
    fun `error mapper exposes the catalogue surface`() {
        // The mapper must expose a stable error code for every status it handles.
        // SCAFFOLD: replace with the real catalogue assertions once mapStatusCode
        // is implemented — invoking it here would throw NotImplementedError.
        assertNotNull(ErrorMapper::class.java.getMethod("mapStatusCode", Int::class.javaPrimitiveType))
        assertNotNull(ErrorMapper::class.java.getMethod("map", Int::class.javaPrimitiveType, String::class.java))
    }
}

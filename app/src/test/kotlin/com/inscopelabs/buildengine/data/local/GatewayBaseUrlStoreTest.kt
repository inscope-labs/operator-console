package com.inscopelabs.buildengine.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayBaseUrlStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(debugDefault: String? = null): GatewayBaseUrlStore {
        val file = File(temporaryFolder.root, "gateway_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { file },
        )
        return GatewayBaseUrlStore(dataStore = dataStore, debugDefault = debugDefault)
    }

    @Test
    fun `default is unset and not configured`() = runTest {
        val store = store()
        assertEquals("", store.read())
        assertFalse(GatewayEndpoints.isConfigured(store.read()))
    }

    @Test
    fun `save and read round trip`() = runTest {
        val store = store()
        store.save("https://gateway.example.com")
        assertEquals("https://gateway.example.com", store.read())
        assertTrue(GatewayEndpoints.isConfigured(store.read()))
    }

    @Test
    fun `clear restores unset when no debug default`() = runTest {
        val store = store()
        store.save("https://gateway.example.com")
        store.clear()
        assertEquals("", store.read())
        assertFalse(GatewayEndpoints.isConfigured(store.read()))
    }

    @Test
    fun `debug default applies when unset`() = runTest {
        val store = store(debugDefault = "http://10.0.2.2:8787")
        assertEquals("http://10.0.2.2:8787", store.read())
        assertTrue(GatewayEndpoints.isConfigured(store.read()))
    }

    @Test
    fun `stored value overrides debug default`() = runTest {
        val store = store(debugDefault = "http://10.0.2.2:8787")
        store.save("https://gateway.example.com")
        assertEquals("https://gateway.example.com", store.read())
    }

    @Test
    fun `clear falls back to debug default`() = runTest {
        val store = store(debugDefault = "http://10.0.2.2:8787")
        store.save("https://gateway.example.com")
        store.clear()
        assertEquals("http://10.0.2.2:8787", store.read())
    }

    @Test
    fun `trailing slash is stripped on save`() = runTest {
        val store = store()
        store.save("https://gateway.example.com/")
        assertEquals("https://gateway.example.com", store.read())
    }
}

package com.inscopelabs.buildengine.data.enrollment

import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DeviceEnrollmentStoreTest {

    private lateinit var preferences: FakeSharedPreferences
    private lateinit var store: DeviceEnrollmentStore

    @Before
    fun setUp() {
        preferences = FakeSharedPreferences()
        store = DeviceEnrollmentStore(preferences = preferences)
    }

    @Test
    fun `recordPending and activate manages lifecycle`() {
        val now = 1700000000L
        store.recordPending(
            kid = "k1",
            keyAlias = "op-key-1",
            publicKeyBase64 = "pub_key_base64_1",
            now = now,
        )

        val pending = store.record("k1")
        assertNotNull(pending)
        assertEquals("k1", pending?.kid)
        assertEquals("op-key-1", pending?.keyAlias)
        assertEquals("pub_key_base64_1", pending?.publicKeyBase64)
        assertEquals(DeviceEnrollmentStore.Status.PENDING, pending?.status)
        assertNull(store.activeKid())

        store.activate("k1", now + 10)

        val active = store.activeRecord()
        assertNotNull(active)
        assertEquals("k1", active?.kid)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, active?.status)
        assertEquals("k1", store.activeKid())
        assertEquals("pub_key_base64_1", store.enrollmentFor("k1"))
        assertEquals("op-key-1", store.keyAliasFor("k1"))
    }

    @Test
    fun `activating a new key retires the previous active key`() {
        val t0 = 1700000000L
        store.recordPending("k1", "key-1", "pub1", t0)
        store.activate("k1", t0)

        val t1 = t0 + 100
        store.recordPending("k2", "key-2", "pub2", t1)
        store.activate("k2", t1)

        assertEquals("k2", store.activeKid())

        val oldRecord = store.record("k1")
        assertNotNull(oldRecord)
        assertEquals(DeviceEnrollmentStore.Status.RETIRED, oldRecord?.status)
        assertEquals(t1, oldRecord?.retiredAtEpochSeconds)

        val newRecord = store.record("k2")
        assertNotNull(newRecord)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, newRecord?.status)
    }

    @Test
    fun `removePending deletes pending but refuses to remove active`() {
        val now = 1700000000L
        store.recordPending("k1", "key-1", "pub1", now)
        store.removePending("k1")
        assertNull(store.record("k1"))

        store.recordPending("k2", "key-2", "pub2", now)
        store.activate("k2", now)

        try {
            store.removePending("k2")
            fail("Expected check failure when removing active key as pending")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("active") == true)
        }
    }

    @Test
    fun `clear deletes all metadata`() {
        val now = 1700000000L
        store.recordPending("k1", "key-1", "pub1", now)
        store.activate("k1", now)
        store.clear()

        assertNull(store.activeKid())
        assertNull(store.activeRecord())
        assertNull(store.record("k1"))
    }

    @Test
    fun `validation refuses invalid kid, alias, or blank key`() {
        try {
            store.recordPending("invalid kid!", "alias", "pub", 0L)
            fail("Expected refusal of invalid kid")
        } catch (_: IllegalArgumentException) {}

        try {
            store.recordPending("k1", "invalid alias with spaces!", "pub", 0L)
            fail("Expected refusal of invalid alias")
        } catch (_: IllegalArgumentException) {}

        try {
            store.recordPending("k1", "valid-alias", "   ", 0L)
            fail("Expected refusal of blank public key")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `record handles corrupted entry fail-closed`() {
        preferences.edit().putString("enrolment.k1", "not\nenough\nparts").commit()
        try {
            store.record("k1")
            fail("Expected IllegalArgumentException for corrupt entry")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Corrupt") == true)
        }
    }

    @Test
    fun `persists and decodes authTier`() {
        val now = 1700000000L
        store.recordPending("k_tier", "key_alias", "pub_key", now, "hardware_confirm")
        store.activate("k_tier", now)

        assertEquals("hardware_confirm", store.authTierFor("k_tier"))
        assertEquals("hardware_confirm", store.activeRecord()?.authTier)
    }

    // --- Per-environment enrollment & refusal (T2-204 / B.4 item 1) -------------

    @Test
    fun `a key enrolled for staging is never offered for production`() {
        val now = 1700000000L
        store.recordPending(
            kid = "k_stg",
            keyAlias = "op_staging_gateway_local",
            publicKeyBase64 = "pub_staging_123",
            now = now,
            authTier = "hardware_confirm",
            environment = "staging",
        )
        store.activate("k_stg", now)

        val stagingRecord = store.activeRecord(environment = "staging")
        assertNotNull(stagingRecord)
        assertEquals("k_stg", stagingRecord?.kid)
        assertEquals("staging", stagingRecord?.environment)
        assertEquals("staging", store.environmentFor("k_stg"))

        // Cross-environment refusal: staging key must NEVER be offered for production or test
        assertNull(store.activeRecord(environment = "production"))
        assertNull(store.activeKid(environment = "production"))
        assertNull(store.activeRecord(environment = "test"))
        assertNull(store.activeKid(environment = "test"))
    }

    @Test
    fun `per-environment active keys coexist without cross-contamination`() {
        val now = 1700000000L
        // Enrol staging key
        store.recordPending("k_stg", "op_staging_host", "pub_stg", now, "hardware_confirm", "staging")
        store.activate("k_stg", now)

        // Enrol prod key
        store.recordPending("k_prd", "op_production_host", "pub_prd", now + 1, "hardware_confirm", "production")
        store.activate("k_prd", now + 1)

        // Enrol test key
        store.recordPending("k_tst", "op_test_host", "pub_tst", now + 2, "hardware_confirm", "test")
        store.activate("k_tst", now + 2)

        assertEquals("k_stg", store.activeRecord(environment = "staging")?.kid)
        assertEquals("k_prd", store.activeRecord(environment = "production")?.kid)
        assertEquals("k_tst", store.activeRecord(environment = "test")?.kid)

        assertEquals("k_stg", store.activeKid(environment = "staging"))
        assertEquals("k_prd", store.activeKid(environment = "production"))
        assertEquals("k_tst", store.activeKid(environment = "test"))
    }
}

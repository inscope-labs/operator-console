package com.inscopelabs.buildengine.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The selection store's round trip.
 *
 * The selection is the console's one piece of mutable local state that is not
 * secret, and it is stored in plain DataStore preferences. The test that matters is
 * the round trip: a value written under a key must come back under that same key,
 * which is the thing that breaks when a store and a key set drift apart.
 */
class SelectionStoreTest {

    @Test
    fun `nothing is selected to begin with`() = runTest {
        val store = newStore()
        assertNull(store.selectedPlan())
        assertNull(store.selectedRepository())
    }

    @Test
    fun `the selected plan round trips`() = runTest {
        val store = newStore()
        store.selectPlan("plan-2026-10-30")
        assertEquals("plan-2026-10-30", store.selectedPlan())
    }

    @Test
    fun `the selected repository round trips`() = runTest {
        val store = newStore()
        store.selectRepository("acme/consumer")
        assertEquals("acme/consumer", store.selectedRepository())
    }

    @Test
    fun `the plan and the repository are independent`() = runTest {
        val store = newStore()
        store.selectPlan("plan-1")
        store.selectRepository("acme/consumer")
        assertEquals("plan-1", store.selectedPlan())
        assertEquals("acme/consumer", store.selectedRepository())
    }

    @Test
    fun `selecting replaces the previous selection`() = runTest {
        val store = newStore()
        store.selectPlan("plan-1")
        store.selectPlan("plan-2")
        assertEquals("plan-2", store.selectedPlan())
    }

    @Test
    fun `an empty selection clears it`() = runTest {
        // "Nothing selected" has exactly one representation on disk: no key at all.
        val store = newStore()
        store.selectPlan("plan-1")
        store.selectPlan("")
        assertNull(store.selectedPlan())
    }

    @Test
    fun `a selection is trimmed`() = runTest {
        val store = newStore()
        store.selectPlan("  plan-1  ")
        assertEquals("plan-1", store.selectedPlan())
    }

    private suspend fun newStore(): SelectionStore {
        val directory = Files.createTempDirectory("selection-store").toFile()
        val file = File(directory, "selection.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { file }
        return SelectionStore(dataStore)
    }
}

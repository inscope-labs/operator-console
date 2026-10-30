package com.inscopelabs.buildengine.data.local

/**
 * The operator's repository and plan selection.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §1: selection state stays on the device, and it is not security
 * state — so plain application storage is sufficient.
 */
class SelectionStore {

    suspend fun selectedPlan(): String? = TODO("SCAFFOLD: read the selected plan id.")

    suspend fun selectPlan(planId: String): Unit = TODO("SCAFFOLD: persist the selection.")

    suspend fun selectedRepository(): String? =
        TODO("SCAFFOLD: read the selected repository.")

    suspend fun selectRepository(repository: String): Unit =
        TODO("SCAFFOLD: persist the selection.")
}

package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Display-only implementation stage metadata derived from plan `stages.json`.
 * Never authoritative for capability minting or state transitions.
 */
data class ImplementationStage(
    val id: String,
    val title: String,
    val orderIndex: Int,
    val milestone: String = "",
    val summary: String = "",
)

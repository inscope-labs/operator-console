package com.inscopelabs.buildengine.manualpipeline.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe `@Serializable` navigation keys inside the manualpipeline module.
 */
@Serializable
data class ManualPipelineWorkspaceRoute(
    val initialTaskId: String? = null,
)

@Serializable
data object ManualPipelineRecoveryRoute

@Serializable
data object ManualPipelineHandoffRoute

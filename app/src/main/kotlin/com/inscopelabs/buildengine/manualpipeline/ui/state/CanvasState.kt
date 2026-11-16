package com.inscopelabs.buildengine.manualpipeline.ui.state

/**
 * Dual-canvas workspace mode for the manual pipeline (`Inspect` vs `Act`).
 */
enum class CanvasMode {
    INSPECT,
    ACT,
}

enum class InspectTab(val label: String) {
    TASK("Task"),
    ARTIFACTS("Artifacts"),
    HISTORY("History"),
}

data class CanvasState(
    val mode: CanvasMode = CanvasMode.INSPECT,
    val activeInspectTab: InspectTab = InspectTab.TASK,
    val selectedTaskId: String? = null,
    val recoveryOpen: Boolean = false,
    val handoffOpen: Boolean = false,
)

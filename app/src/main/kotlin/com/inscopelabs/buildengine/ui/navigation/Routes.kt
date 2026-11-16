package com.inscopelabs.buildengine.ui.navigation

/**
 * The navigation graph's routes.
 */
object Routes {

    const val DASHBOARD: String = "dashboard"
    const val PLANS: String = "plans"
    const val PLAN_DETAIL: String = "plans/{planId}"
    const val TASK_DETAIL: String = "tasks/{taskId}"
    const val PUBLISH: String = "publish/{taskId}"
    const val PUBLISH_PROGRESS: String = "publish/{taskId}/progress"
    const val DECISION: String = "decision/{decisionId}"
    const val ENROLLMENT: String = "enrollment"
    const val SETTINGS: String = "settings"
    const val PREFLIGHT: String = "preflight"
    const val MANUAL_PIPELINE: String = "manual_pipeline"
    const val MANUAL_PIPELINE_TASK: String = "manual_pipeline/{taskId}"

    fun planDetail(planId: String): String = "plans/$planId"

    fun taskDetail(taskId: String): String = "tasks/$taskId"

    fun publish(taskId: String): String = "publish/$taskId"

    fun publishProgress(taskId: String): String = "publish/$taskId/progress"

    fun decision(decisionId: String): String = "decision/$decisionId"

    fun manualPipelineTask(taskId: String): String = "manual_pipeline/$taskId"
}

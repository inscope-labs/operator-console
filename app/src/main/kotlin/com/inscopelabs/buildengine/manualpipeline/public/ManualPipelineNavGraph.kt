package com.inscopelabs.buildengine.manualpipeline.`public`

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.inscopelabs.buildengine.manualpipeline.ui.navigation.ManualPipelineHost

object ManualPipelineNavRoutes {
    const val WORKSPACE: String = "manual_pipeline"
    const val WORKSPACE_TASK: String = "manual_pipeline/{taskId}"

    fun workspaceForTask(taskId: String): String = "manual_pipeline/$taskId"
}

/**
 * Attaches the manual pipeline routes to the host [NavGraphBuilder].
 */
fun NavGraphBuilder.manualPipelineNavGraph(
    navController: NavHostController,
) {
    composable(ManualPipelineNavRoutes.WORKSPACE) {
        ManualPipelineHost(
            initialTaskId = null,
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = ManualPipelineNavRoutes.WORKSPACE_TASK,
        arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
    ) { backStackEntry ->
        val taskId = backStackEntry.arguments?.getString("taskId")
        ManualPipelineHost(
            initialTaskId = taskId,
            onBack = { navController.popBackStack() },
        )
    }
}

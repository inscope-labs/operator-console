package com.inscopelabs.buildengine.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inscopelabs.buildengine.ui.dashboard.DashboardScreen
import com.inscopelabs.buildengine.ui.decision.DecisionScreen
import com.inscopelabs.buildengine.ui.enrollment.EnrollmentScreen
import com.inscopelabs.buildengine.ui.plans.PlanDetailScreen
import com.inscopelabs.buildengine.ui.plans.PlanListScreen
import com.inscopelabs.buildengine.ui.preflight.PreflightScreen
import com.inscopelabs.buildengine.ui.publish.PublishConfirmScreen
import com.inscopelabs.buildengine.ui.publish.PublishProgressScreen
import com.inscopelabs.buildengine.ui.settings.SettingsScreen
import com.inscopelabs.buildengine.ui.taskdetail.TaskDetailScreen

/**
 * The console's navigation graph.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToPlan = { planId -> navController.navigate(Routes.planDetail(planId)) },
                onNavigateToTask = { taskId -> navController.navigate(Routes.taskDetail(taskId)) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToPlans = { navController.navigate(Routes.PLANS) },
                onNavigateToPreflight = { navController.navigate(Routes.PREFLIGHT) },
            )
        }
        composable(Routes.PLANS) {
            PlanListScreen(
                onPlanClick = { planId -> navController.navigate(Routes.planDetail(planId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PLAN_DETAIL,
            arguments = listOf(navArgument("planId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getString("planId").orEmpty()
            PlanDetailScreen(
                planId = planId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.TASK_DETAIL,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId").orEmpty()
            TaskDetailScreen(
                taskId = taskId,
                onBack = { navController.popBackStack() },
                onPublishClick = { id -> navController.navigate(Routes.publish(id)) },
            )
        }
        composable(
            route = Routes.PUBLISH,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId").orEmpty()
            PublishConfirmScreen(
                onSubmitted = { navController.navigate(Routes.publishProgress(taskId)) },
            )
        }
        composable(
            route = Routes.PUBLISH_PROGRESS,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { backStackEntry ->
            // Share the confirmation destination's view model: one submission, one state.
            val confirmEntry = remember(backStackEntry) { navController.getBackStackEntry(Routes.PUBLISH) }
            PublishProgressScreen(viewModel = hiltViewModel(confirmEntry))
        }
        composable(
            route = Routes.DECISION,
            arguments = listOf(navArgument("decisionId") { type = NavType.StringType }),
        ) {
            DecisionScreen()
        }
        composable(Routes.ENROLLMENT) {
            EnrollmentScreen()
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToPreflight = { navController.navigate(Routes.PREFLIGHT) },
            )
        }
        composable(Routes.PREFLIGHT) {
            PreflightScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToEnrollment = { navController.navigate(Routes.ENROLLMENT) },
            )
        }
    }
}

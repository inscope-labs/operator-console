package com.inscopelabs.buildengine.ui.navigation

import androidx.compose.runtime.Composable
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
import com.inscopelabs.buildengine.ui.publish.PublishConfirmScreen
import com.inscopelabs.buildengine.ui.publish.PublishProgressScreen
import com.inscopelabs.buildengine.ui.settings.SettingsScreen
import com.inscopelabs.buildengine.ui.taskdetail.TaskDetailScreen

/**
 * The console's navigation graph.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
@Composable
fun AppNavHost(navController: androidx.navigation.NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) { DashboardScreen() }
        composable(Routes.PLANS) { PlanListScreen() }
        composable(
            route = Routes.PLAN_DETAIL,
            arguments = listOf(navArgument("planId") { type = NavType.StringType }),
        ) { PlanDetailScreen() }
        composable(
            route = Routes.TASK_DETAIL,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { TaskDetailScreen() }
        composable(
            route = Routes.PUBLISH,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { PublishConfirmScreen() }
        composable(
            route = Routes.PUBLISH_PROGRESS,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { PublishProgressScreen() }
        composable(
            route = Routes.DECISION,
            arguments = listOf(navArgument("decisionId") { type = NavType.StringType }),
        ) { DecisionScreen() }
        composable(Routes.ENROLLMENT) { EnrollmentScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}

package com.inscopelabs.buildengine.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.inscopelabs.buildengine.ui.components.ConnectionStatusBanner
import com.inscopelabs.buildengine.ui.navigation.AppNavHost
import com.inscopelabs.buildengine.ui.navigation.Routes

/**
 * Application shell: connection status banner above the navigation graph.
 *
 * The banner is always visible so the operator knows gateway connectivity at a glance.
 * Tapping it navigates to Settings.
 */
@Composable
fun ConsoleShell(
    modifier: Modifier = Modifier,
    viewModel: ConnectionStatusViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val status by viewModel.status.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("console_shell"),
        topBar = {
            ConnectionStatusBanner(
                status = status,
                onOpenSettings = {
                    val current = navController.currentDestination?.route
                    if (current != Routes.SETTINGS) {
                        navController.navigate(Routes.SETTINGS) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AppNavHost(navController = navController)
        }
    }
}

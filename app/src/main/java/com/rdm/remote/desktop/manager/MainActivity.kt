package com.rdm.remote.desktop.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rdm.remote.desktop.manager.ui.navigation.Screen
import com.rdm.remote.desktop.manager.ui.screens.AllServersScreen
import com.rdm.remote.desktop.manager.ui.screens.ClientListScreen
import com.rdm.remote.desktop.manager.ui.screens.ServerListScreen
import com.rdm.remote.desktop.manager.ui.screens.SettingsScreen
import com.rdm.remote.desktop.manager.ui.theme.RemoteDesktopManagerTheme
import com.rdm.remote.desktop.manager.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as RdmApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RemoteDesktopManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Collect Toast / Snack messages from ViewModel
                    LaunchedEffect(Unit) {
                        viewModel.userMessage.collectLatest { msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.ClientList.route
                    ) {
                        // 1. Clients List Screen
                        composable(Screen.ClientList.route) {
                            ClientListScreen(
                                viewModel = viewModel,
                                onClientClick = { clientId ->
                                    navController.navigate(Screen.ServerList.createRoute(clientId))
                                },
                                onAllServersClick = {
                                    navController.navigate(Screen.AllServers.route)
                                },
                                onSettingsClick = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        // 2. Server List Screen for specific client
                        composable(
                            route = Screen.ServerList.route,
                            arguments = listOf(
                                navArgument("clientId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val clientId = backStackEntry.arguments?.getLong("clientId") ?: 0L
                            ServerListScreen(
                                clientId = clientId,
                                viewModel = viewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 3. All Servers Screen (Global Search)
                        composable(Screen.AllServers.route) {
                            AllServersScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 4. Settings & Backup Screen
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

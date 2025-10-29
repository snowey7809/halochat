package com.rapo.haloai.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.rapo.haloai.presentation.screens.*
import com.rapo.haloai.presentation.theme.HaloAITheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloAITheme {
                val systemUiController = rememberSystemUiController()
                val statusBarColor = MaterialTheme.colorScheme.primaryContainer

                LaunchedEffect(systemUiController) {
                    systemUiController.setStatusBarColor(statusBarColor)
                }

                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate("chat") 
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = "Models") },
                    label = { Text("Models") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate("models")
                    }
                )
                 NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate("settings")
                    }
                )
            }
        },
        content = {
            NavHost(navController = navController, startDestination = "chat") {
                composable("chat") {
                    ChatScreen(navController = navController, onMenuClick = { scope.launch { drawerState.open() } })
                }
                composable("models") {
                    ModelsScreen(navController = navController)
                }
                composable("settings") {
                    SettingsScreen(onNavigateToExperimental = { navController.navigate("experimental") })
                }
                 composable("experimental") {
                    ExperimentalScreen()
                }
            }
        }
    )
}

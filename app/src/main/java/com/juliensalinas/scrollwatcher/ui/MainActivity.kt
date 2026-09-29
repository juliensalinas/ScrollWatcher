package com.juliensalinas.scrollwatcher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.juliensalinas.scrollwatcher.ui.evilapps.EvilAppsScreen
import com.juliensalinas.scrollwatcher.ui.evilapps.EvilAppsViewModel
import com.juliensalinas.scrollwatcher.ui.home.HomeScreen
import com.juliensalinas.scrollwatcher.ui.home.HomeViewModel
import com.juliensalinas.scrollwatcher.ui.permissions.PermissionsScreen
import com.juliensalinas.scrollwatcher.ui.theme.ScrollWatcherTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        setContent {
            ScrollWatcherTheme {
                ScrollWatcherNav()
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Dest("home", "Home", Icons.Filled.Home)
    data object Evil : Dest("evil", "Evil apps", Icons.Filled.Apps)
    data object Permissions : Dest("permissions", "Permissions", Icons.Filled.Security)
}

/**
 * Navigate between top-level bottom-bar destinations.
 *
 * Intentionally does NOT use saveState/restoreState: mixing those with an extra
 * navigate(Permissions) from Home left the UI stuck on Permissions after tapping Home.
 * Free tab switching is preferred; Permissions shows green checks when granted.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            inclusive = false
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

@Composable
private fun ScrollWatcherNav() {
    val navController = rememberNavController()
    val destinations = listOf(Dest.Home, Dest.Evil, Dest.Permissions)
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = { navController.navigateTopLevel(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Home.route) {
                val vm: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = vm,
                    onOpenPermissions = {
                        navController.navigateTopLevel(Dest.Permissions.route)
                    },
                )
            }
            composable(Dest.Evil.route) {
                val vm: EvilAppsViewModel = viewModel()
                EvilAppsScreen(viewModel = vm)
            }
            composable(Dest.Permissions.route) {
                PermissionsScreen()
            }
        }
    }
}

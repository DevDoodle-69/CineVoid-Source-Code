package com.nzr.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nzr.ui.detail.DetailScreen
import com.nzr.ui.watch.WatchScreen
import com.nzr.ui.splash.SplashScreen
import com.nzr.util.NetworkMonitor

@Composable
fun RootNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)
    
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController, 
            startDestination = "splash",
            enterTransition = {
                slideInHorizontally(initialOffsetX = { 500 }) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) + slideOutHorizontally(targetOffsetX = { -500 })
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -500 }) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) + slideOutHorizontally(targetOffsetX = { 500 })
            }
        ) {
            composable("splash") { SplashScreen(navController) }
            composable("main") { MainScreen(rootNavController = navController) }
            composable("detail/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                DetailScreen(navController, id)
            }
            composable("watch/{id}?s={s}&e={e}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                val s = backStackEntry.arguments?.getString("s")?.toIntOrNull() ?: 1
                val e = backStackEntry.arguments?.getString("e")?.toIntOrNull() ?: 1
                WatchScreen(navController, id, s, e)
            }
        }
        
        AnimatedVisibility(
            visible = !isOnline,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.SignalWifiOff, contentDescription = "Offline", tint = MaterialTheme.colorScheme.onErrorContainer)
                Text("You are offline. Please check your connection.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

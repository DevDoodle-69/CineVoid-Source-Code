package com.nzr.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nzr.ui.home.HomeScreen
import com.nzr.ui.movies.MoviesScreen
import com.nzr.ui.search.SearchScreen
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.nzr.ui.shared.tvFocusGlow
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.launch
import androidx.compose.material3.NavigationDrawerItemDefaults

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Row

@Composable
fun MainScreen(rootNavController: NavHostController) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val isTv = remember { 
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                navItems.forEach { (info, icon) ->
                    val (route, label) = info
                    NavigationDrawerItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentRoute == route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0,0,0,0)
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) },
                    exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) },
                    popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) },
                    popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) }
                ) {
                    composable("home") { HomeScreen(rootNavController, onOpenDrawer = { scope.launch { drawerState.open() } }, onSearchClick = { navController.navigate("search") }) }
                    composable("movies") { MoviesScreen(rootNavController, type = "movie", paddingBottom = 0.dp) }
                    composable("series") { MoviesScreen(rootNavController, type = "series", paddingBottom = 0.dp) }
                    composable("search") { SearchScreen(rootNavController, paddingBottom = 0.dp) }
                }
            }
        }
    }
}

val navItems = listOf(
    Pair("home", "Home") to Icons.Rounded.Home,
    Pair("movies", "Movies") to Icons.Rounded.LocalMovies,
    Pair("series", "Series") to Icons.Rounded.Tv,
    Pair("search", "Search") to Icons.Rounded.Search
)

@Composable
fun SideNavigationRail(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
    ) {
        navItems.forEach { (info, icon) ->
            val (route, label) = info
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            
            NavigationRailItem(
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                modifier = Modifier.tvFocusGlow(true, isFocused, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f),
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)
                ),
                interactionSource = interactionSource
            )
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val context = LocalContext.current
    val isTv = remember { 
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
    ) {
        navItems.forEach { (info, icon) ->
            val (route, label) = info
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                modifier = Modifier.tvFocusGlow(isTv, isFocused, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f),
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)
                ),
                interactionSource = interactionSource
            )
        }
    }
}

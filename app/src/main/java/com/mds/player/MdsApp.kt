package com.mds.player

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mds.player.ui.CatalogScreen
import com.mds.player.ui.DetailScreen
import com.mds.player.ui.FavoritesScreen
import com.mds.player.ui.MdsNavigationBar
import com.mds.player.ui.MiniPlayer
import com.mds.player.ui.PlayerScreen
import com.mds.player.ui.SearchScreen
import com.mds.player.ui.TopDestination
import com.mds.player.ui.theme.MdsTheme

private const val ROUTE_PLAYER = "player"
private val TOP_LEVEL_ROUTES = setOf("catalog", "search", "favorites")

@Composable
fun MdsApp(viewModel: MainViewModel) {
    MdsTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val playerState by viewModel.playbackState.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(viewModel) {
            viewModel.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        val showBottomContent = currentRoute in TOP_LEVEL_ROUTES

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column {
                    if (showBottomContent && playerState.hasMedia) {
                        MiniPlayer(
                            state = playerState,
                            onOpen = { navController.navigate(ROUTE_PLAYER) },
                            onTogglePlayPause = viewModel::togglePlayPause
                        )
                    }
                    if (showBottomContent) {
                        MdsNavigationBar(
                            currentRoute = currentRoute,
                            onNavigate = { destination ->
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopDestination.CATALOG.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(TopDestination.CATALOG.route) {
                    CatalogScreen(
                        viewModel = viewModel,
                        onOpen = { item, queue ->
                            viewModel.openDetail(item, queue)
                            navController.navigate("detail/${Uri.encode(item.id)}")
                        }
                    )
                }
                composable(TopDestination.SEARCH.route) {
                    SearchScreen(
                        viewModel = viewModel,
                        onOpen = { item, queue ->
                            viewModel.openDetail(item, queue)
                            navController.navigate("detail/${Uri.encode(item.id)}")
                        }
                    )
                }
                composable(TopDestination.FAVORITES.route) {
                    FavoritesScreen(
                        viewModel = viewModel,
                        onOpen = { item, queue ->
                            viewModel.openDetail(item, queue)
                            navController.navigate("detail/${Uri.encode(item.id)}")
                        }
                    )
                }
                composable(
                    route = "detail/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { entry ->
                    DetailScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenPlayer = { navController.navigate(ROUTE_PLAYER) }
                    )
                }
                composable(ROUTE_PLAYER) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

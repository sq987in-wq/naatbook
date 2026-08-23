package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.NaatEntity
import com.example.ui.components.GlobalMiniPlayer
import com.example.ui.components.NaatBottomNavigation
import com.example.ui.editor.AddNaatModal
import com.example.ui.library.LibraryScreen
import com.example.ui.reader.LyricsReaderScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.NaatViewModel

private object NaatRoutes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val READER = "reader"
    const val EDITOR = "editor"
}

private const val TOP_LEVEL_FADE_MILLIS = 140

@Composable
fun NaatApp(viewModel: NaatViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showAddModal by viewModel.showAddModal.collectAsState()
    val selectedNaat by viewModel.selectedNaat.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val favoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val isSystemDark = isSystemInDarkTheme()
    val darkThemeEnabled = when (themeMode) {
        "white" -> false
        "black" -> true
        else -> isSystemDark
    }

    val statusMessage by viewModel.statusMessage.collectAsState()
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    // ViewModel mutations which finish a route asynchronously (save/delete)
    // also pop the corresponding Navigation-Compose destination.
    LaunchedEffect(currentRoute, showAddModal) {
        if (currentRoute == NaatRoutes.EDITOR && !showAddModal) {
            navController.popBackStack()
        }
    }
    LaunchedEffect(currentRoute, selectedNaat) {
        if (currentRoute == NaatRoutes.READER && selectedNaat == null) {
            navController.popBackStack()
        }
    }

    BackHandler(
        enabled = currentRoute == NaatRoutes.EDITOR ||
            currentRoute == NaatRoutes.READER ||
            currentRoute == NaatRoutes.SETTINGS ||
            (currentRoute == NaatRoutes.LIBRARY &&
                (selectedFolder != null || favoritesOnly || searchQuery.isNotBlank()))
    ) {
        when (currentRoute) {
            NaatRoutes.EDITOR -> closeEditor(viewModel, navController)
            NaatRoutes.READER -> closeReader(viewModel, navController)
            NaatRoutes.SETTINGS -> {
                viewModel.selectTab(0)
                viewModel.resetLibraryToHome()
                navController.popBackStack()
            }
            NaatRoutes.LIBRARY -> when {
                selectedFolder != null -> viewModel.selectFolder(null)
                favoritesOnly -> viewModel.setFavoritesOnly(false)
                else -> viewModel.setSearchQuery("")
            }
        }
    }

    MyApplicationTheme(darkTheme = darkThemeEnabled) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val showBottomBar = currentRoute == null ||
                currentRoute == NaatRoutes.LIBRARY || currentRoute == NaatRoutes.SETTINGS
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                bottomBar = {
                    if (showBottomBar) {
                        Column {
                            GlobalMiniPlayer(
                                viewModel = viewModel,
                                onOpen = {
                                    viewModel.openNowPlayingEntry { found ->
                                        if (found && navController.currentDestination?.route != NaatRoutes.READER) {
                                            navController.navigate(NaatRoutes.READER) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            )
                            NaatBottomNavigation(
                                // Navigation is the source of truth for selection;
                                // this also stays correct after back-stack restore.
                                currentTab = if (currentRoute == NaatRoutes.SETTINGS) 2 else 0,
                                onTabSelected = { tab ->
                                    navigateToTab(tab, viewModel, navController)
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NaatNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        // Keep a themed layer behind both destinations throughout
                        // their crossfade; otherwise a transparent animation frame
                        // can expose the Activity window (black in light mode).
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}

@Composable
private fun NaatNavHost(
    navController: NavHostController,
    viewModel: NaatViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NaatRoutes.LIBRARY,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(
            route = NaatRoutes.LIBRARY,
            enterTransition = {
                if (initialState.destination.route == NaatRoutes.SETTINGS) {
                    fadeIn(tween(TOP_LEVEL_FADE_MILLIS))
                } else {
                    EnterTransition.None
                }
            },
            exitTransition = {
                if (targetState.destination.route == NaatRoutes.SETTINGS) {
                    fadeOut(tween(TOP_LEVEL_FADE_MILLIS))
                } else {
                    ExitTransition.None
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == NaatRoutes.SETTINGS) {
                    fadeIn(tween(TOP_LEVEL_FADE_MILLIS))
                } else {
                    EnterTransition.None
                }
            }
        ) {
            LibraryScreen(
                viewModel = viewModel,
                onOpenReader = { naat -> openReader(naat, viewModel, navController) },
                onEdit = { naat -> openEditor(naat, viewModel, navController) }
            )
        }
        composable(
            route = NaatRoutes.SETTINGS,
            enterTransition = {
                if (initialState.destination.route == NaatRoutes.LIBRARY) {
                    fadeIn(tween(TOP_LEVEL_FADE_MILLIS))
                } else {
                    EnterTransition.None
                }
            },
            exitTransition = {
                if (targetState.destination.route == NaatRoutes.LIBRARY) {
                    fadeOut(tween(TOP_LEVEL_FADE_MILLIS))
                } else {
                    ExitTransition.None
                }
            },
            popExitTransition = {
                if (targetState.destination.route == NaatRoutes.LIBRARY) {
                    fadeOut(tween(TOP_LEVEL_FADE_MILLIS))
                } else {
                    ExitTransition.None
                }
            }
        ) {
            SettingsScreen(viewModel = viewModel)
        }
        composable(
            route = NaatRoutes.READER,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            viewModel.selectedNaat.value?.let { naat ->
                LyricsReaderScreen(
                    naat = naat,
                    viewModel = viewModel,
                    onClose = { closeReader(viewModel, navController) },
                    onEdit = { openEditor(it, viewModel, navController) }
                )
            }
        }
        composable(
            route = NaatRoutes.EDITOR,
            enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
            popExitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() }
        ) {
            AddNaatModal(
                viewModel = viewModel,
                onClose = { closeEditor(viewModel, navController) }
            )
        }
    }
}

private fun navigateToTab(
    tab: Int,
    viewModel: NaatViewModel,
    navController: NavHostController
) {
    val targetRoute = when (tab) {
        0 -> NaatRoutes.LIBRARY
        2 -> NaatRoutes.SETTINGS
        else -> null
    }

    if (targetRoute != null) {
        // launchSingleTop alone still dispatches a navigation operation and can
        // restart destination transitions on some Navigation-Compose versions.
        // Make reselecting the active tab a strict no-op before mutating UI state.
        if (navController.currentDestination?.route == targetRoute) return

        viewModel.selectTab(tab)
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        return
    }

    if (tab == 1 && navController.currentDestination?.route != NaatRoutes.EDITOR) {
        viewModel.selectTab(tab)
        navController.navigate(NaatRoutes.EDITOR) { launchSingleTop = true }
    }
}

private fun openReader(
    naat: NaatEntity,
    viewModel: NaatViewModel,
    navController: NavHostController
) {
    viewModel.selectNaat(naat)
    navController.navigate(NaatRoutes.READER) { launchSingleTop = true }
}

private fun closeReader(viewModel: NaatViewModel, navController: NavHostController) {
    viewModel.selectNaat(null)
    navController.popBackStack()
}

private fun openEditor(
    naat: NaatEntity,
    viewModel: NaatViewModel,
    navController: NavHostController
) {
    viewModel.startEditNaat(naat)
    navController.navigate(NaatRoutes.EDITOR) { launchSingleTop = true }
}

private fun closeEditor(viewModel: NaatViewModel, navController: NavHostController) {
    if (viewModel.isSaving.value) return
    viewModel.setShowAddModal(false)
    navController.popBackStack()
}

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

@Composable
fun NaatApp(viewModel: NaatViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val currentTab by viewModel.currentTab.collectAsState()
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
                bottomBar = {
                    if (showBottomBar) {
                        Column {
                            GlobalMiniPlayer(
                                viewModel = viewModel,
                                onOpen = {
                                    viewModel.openNowPlayingEntry()
                                    if (viewModel.selectedNaat.value != null) {
                                        navController.navigate(NaatRoutes.READER) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            )
                            NaatBottomNavigation(
                                currentTab = currentTab,
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
        composable(NaatRoutes.LIBRARY) {
            LibraryScreen(
                viewModel = viewModel,
                onOpenReader = { naat -> openReader(naat, viewModel, navController) },
                onEdit = { naat -> openEditor(naat, viewModel, navController) }
            )
        }
        composable(NaatRoutes.SETTINGS) {
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
    viewModel.selectTab(tab)
    when (tab) {
        0 -> navController.navigate(NaatRoutes.LIBRARY) {
            popUpTo(NaatRoutes.LIBRARY) { inclusive = false }
            launchSingleTop = true
        }
        1 -> navController.navigate(NaatRoutes.EDITOR) { launchSingleTop = true }
        2 -> navController.navigate(NaatRoutes.SETTINGS) { launchSingleTop = true }
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
    viewModel.setShowAddModal(false)
    navController.popBackStack()
}

package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    const val HOME = "home"
    const val READER = "reader"
    const val EDITOR = "editor"
}

@Composable
fun NaatApp(viewModel: NaatViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val tabStateHolder = rememberSaveableStateHolder()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val showAddModal by viewModel.showAddModal.collectAsStateWithLifecycle()
    val selectedNaat by viewModel.selectedNaat.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var detailNavigationPending by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) { detailNavigationPending = false }

    val darkThemeEnabled = when (themeMode) {
        "white" -> false
        "black" -> true
        else -> isSystemInDarkTheme()
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    // State is the single authority for asynchronous save/delete/close completion.
    LaunchedEffect(currentRoute, showAddModal) {
        if (currentRoute == NaatRoutes.EDITOR && !showAddModal) navController.popBackStack()
    }
    LaunchedEffect(currentRoute, selectedNaat) {
        if (currentRoute == NaatRoutes.READER && selectedNaat == null) navController.popBackStack()
    }

    AppBackHandler(currentRoute, currentTab, viewModel)


    fun openReader(id: Int) {
        if (detailNavigationPending || currentRoute != NaatRoutes.HOME) return
        detailNavigationPending = true
        viewModel.loadNaat(
            id = id,
            onLoaded = { naat ->
                viewModel.selectNaat(naat)
                navController.navigate(NaatRoutes.READER) { launchSingleTop = true }
            },
            onFailure = { detailNavigationPending = false }
        )
    }

    fun openEditorEntry(naat: NaatEntity) {
        if (detailNavigationPending || currentRoute == NaatRoutes.EDITOR) return
        detailNavigationPending = true
        viewModel.startEditNaat(naat)
        navController.navigate(NaatRoutes.EDITOR) { launchSingleTop = true }
    }

    fun openEditorById(id: Int) {
        if (detailNavigationPending || currentRoute != NaatRoutes.HOME) return
        detailNavigationPending = true
        viewModel.loadNaat(
            id = id,
            onLoaded = { naat ->
                viewModel.startEditNaat(naat)
                navController.navigate(NaatRoutes.EDITOR) { launchSingleTop = true }
            },
            onFailure = { detailNavigationPending = false }
        )
    }

    fun openAdd() {
        if (detailNavigationPending || currentRoute != NaatRoutes.HOME) return
        detailNavigationPending = true
        viewModel.selectTab(1)
        navController.navigate(NaatRoutes.EDITOR) { launchSingleTop = true }
    }

    MyApplicationTheme(darkTheme = darkThemeEnabled) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val atHome = currentRoute == null || currentRoute == NaatRoutes.HOME
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                bottomBar = {
                    if (atHome) {
                        Column {
                            GlobalMiniPlayer(
                                viewModel = viewModel,
                                onOpen = {
                                    viewModel.openNowPlayingEntry { found ->
                                        if (found && !detailNavigationPending) {
                                            detailNavigationPending = true
                                            navController.navigate(NaatRoutes.READER) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            )
                            NaatBottomNavigation(
                                currentTab = currentTab,
                                onTabSelected = { tab ->
                                    when (tab) {
                                        0, 2 -> if (tab != currentTab) viewModel.selectTab(tab)
                                        1 -> openAdd()
                                    }
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = NaatRoutes.HOME,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    composable(NaatRoutes.HOME) {
                        // Top-level tabs switch in-place: no back-stack transaction, fade, or
                        // simultaneous full-screen composition.
                        tabStateHolder.SaveableStateProvider(currentTab) {
                            when (currentTab) {
                                2 -> SettingsScreen(viewModel)
                                else -> LibraryScreen(
                                    viewModel = viewModel,
                                    onOpenReader = { id -> openReader(id) },
                                    onEdit = { id -> openEditorById(id) }
                                )
                            }
                        }
                    }
                    composable(NaatRoutes.READER) {
                        selectedNaat?.let { naat ->
                            LyricsReaderScreen(
                                naat = naat,
                                viewModel = viewModel,
                                onClose = { viewModel.selectNaat(null) },
                                onEdit = { entry -> openEditorEntry(entry) }
                            )
                        }
                    }
                    composable(NaatRoutes.EDITOR) {
                        AddNaatModal(
                            viewModel = viewModel,
                            onClose = { if (!viewModel.isSaving.value) viewModel.setShowAddModal(false) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun AppBackHandler(currentRoute: String?, currentTab: Int, viewModel: NaatViewModel) {
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    BackHandler(
        enabled = currentRoute == NaatRoutes.EDITOR || currentRoute == NaatRoutes.READER ||
            (currentRoute == NaatRoutes.HOME && currentTab == 2) ||
            (currentRoute == NaatRoutes.HOME && currentTab == 0 &&
                (selectedFolder != null || favoritesOnly || searchQuery.isNotBlank()))
    ) {
        when {
            currentRoute == NaatRoutes.EDITOR -> viewModel.setShowAddModal(false)
            currentRoute == NaatRoutes.READER -> viewModel.selectNaat(null)
            currentRoute == NaatRoutes.HOME && currentTab == 2 -> {
                viewModel.selectTab(0)
                viewModel.resetLibraryToHome()
            }
            selectedFolder != null -> viewModel.selectFolder(null)
            favoritesOnly -> viewModel.setFavoritesOnly(false)
            else -> viewModel.setSearchQuery("")
        }
    }
}

package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isAttaching by viewModel.isAttachingFile.collectAsStateWithLifecycle()

    var detailNavigationPending by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute, showAddModal) {
        // The sheet has no navigation destination. Release the coalescing gate only
        // once either a real detail route or the state-owned editor is visible.
        if (currentRoute != NaatRoutes.HOME || showAddModal) detailNavigationPending = false
    }

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

    // Reader closure remains state-driven. Editor closure is sheet state-driven, so
    // there is no second NavHost pop authority for add/edit requests.
    LaunchedEffect(currentRoute, selectedNaat) {
        if (currentRoute == NaatRoutes.READER && selectedNaat == null) navController.popBackStack()
    }

    AppBackHandler(currentRoute, currentTab, showAddModal, viewModel)

    fun openReader(id: Int) {
        if (detailNavigationPending || showAddModal || currentRoute != NaatRoutes.HOME) return
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
        if (detailNavigationPending || showAddModal) return
        detailNavigationPending = true
        viewModel.startEditNaat(naat)
    }

    fun openEditorById(id: Int) {
        if (detailNavigationPending || showAddModal || currentRoute != NaatRoutes.HOME) return
        detailNavigationPending = true
        viewModel.loadNaat(
            id = id,
            onLoaded = { naat -> viewModel.startEditNaat(naat) },
            onFailure = { detailNavigationPending = false }
        )
    }

    fun openAdd() {
        if (detailNavigationPending || showAddModal || currentRoute != NaatRoutes.HOME) return
        detailNavigationPending = true
        // A FAB press always represents a deliberately fresh new entry, never a
        // dormant edit draft. startAddDraft handles recorder/file cleanup off-main.
        viewModel.startAddDraft(forceFresh = true)
    }

    MyApplicationTheme(darkTheme = darkThemeEnabled) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // The sheet is intentionally bounded below the full window height so
                // it remains a sheet on compact phones instead of becoming a status-bar
                // touching fullscreen editor. Its LazyColumn owns the inner scrolling.
                val sheetMaxHeight = maxHeight * 0.90f
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
                                            if (found && !detailNavigationPending && !showAddModal) {
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
                                        if (tab != currentTab) viewModel.selectTab(tab)
                                    },
                                    onAddRequested = ::openAdd
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NaatRoutes.HOME,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable(NaatRoutes.HOME) {
                            // Top-level tabs switch in-place: no back-stack transaction,
                            // fade, or simultaneous full-screen composition.
                            tabStateHolder.SaveableStateProvider(currentTab) {
                                when (currentTab) {
                                    2 -> SettingsScreen(viewModel)
                                    else -> LibraryScreen(
                                        viewModel = viewModel,
                                        onOpenReader = ::openReader,
                                        onEdit = ::openEditorById
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
                                    onEdit = ::openEditorEntry
                                )
                            }
                        }
                    }
                }

                if (showAddModal) {
                    val canDismissSheet = !isSaving && !isAttaching
                    val sheetState = rememberModalBottomSheetState(
                        skipPartiallyExpanded = true,
                        confirmValueChange = { target ->
                            target != SheetValue.Hidden || canDismissSheet
                        }
                    )
                    ModalBottomSheet(
                        onDismissRequest = { viewModel.setShowAddModal(false) },
                        sheetState = sheetState
                    ) {
                        AddNaatModal(
                            viewModel = viewModel,
                            onClose = { viewModel.setShowAddModal(false) },
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .heightIn(max = sheetMaxHeight)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBackHandler(
    currentRoute: String?,
    currentTab: Int,
    showAddModal: Boolean,
    viewModel: NaatViewModel
) {
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.showFavoritesOnly.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    BackHandler(
        enabled = showAddModal || currentRoute == NaatRoutes.READER ||
            (currentRoute == NaatRoutes.HOME && currentTab == 2) ||
            (currentRoute == NaatRoutes.HOME && currentTab == 0 &&
                (selectedFolder != null || favoritesOnly || searchQuery.isNotBlank()))
    ) {
        when {
            showAddModal -> viewModel.setShowAddModal(false)
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

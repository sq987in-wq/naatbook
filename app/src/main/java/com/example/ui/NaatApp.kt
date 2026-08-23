package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.RecordingState
import com.example.data.NaatCategories
import com.example.data.NaatEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NastaliqFamily
import com.example.ui.theme.HighContrastRed
import com.example.ui.theme.HighContrastGray
import com.example.viewmodel.NaatViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaatApp(viewModel: NaatViewModel) {
    val context = LocalContext.current
    
    // Core states
    val currentTab by viewModel.currentTab.collectAsState()
    val showAddModal by viewModel.showAddModal.collectAsState()
    val selectedNaat by viewModel.selectedNaat.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val favoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    // Derived dark/light boolean
    val isSystemDark = isSystemInDarkTheme()
    val darkThemeEnabled = when (themeMode) {
        "white" -> false
        "black" -> true
        else -> isSystemDark
    }

    // Listens for status messages (backup/restore, warnings)
    val statusMessage by viewModel.statusMessage.collectAsState()
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    // In-app back navigation: every inner layer pops back towards the main
    // Library home before the app exits. The order mirrors the visual stack
    // (topmost first):
    //   add/edit modal -> lyrics reader -> settings tab -> folder view
    //   -> favorites filter -> search results -> (fall through = exit app)
    // Favorites and search are included so back never falls through to the
    // system while a filtered/results view is visible.
    BackHandler(
        enabled = showAddModal || selectedNaat != null || currentTab != 0 ||
                selectedFolder != null || favoritesOnly || searchQuery.isNotBlank()
    ) {
        when {
            showAddModal -> viewModel.setShowAddModal(false)
            selectedNaat != null -> viewModel.selectNaat(null)
            currentTab != 0 -> {
                // Returning from Settings always lands on a clean Library home.
                viewModel.selectTab(0)
                viewModel.resetLibraryToHome()
            }
            selectedFolder != null -> viewModel.selectFolder(null)
            favoritesOnly -> viewModel.setFavoritesOnly(false)
            else -> viewModel.setSearchQuery("")
        }
    }

    MyApplicationTheme(darkTheme = darkThemeEnabled) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    bottomBar = {
                        if (selectedNaat == null && !showAddModal) {
                            NaatBottomNavigation(
                                currentTab = currentTab,
                                onTabSelected = { viewModel.selectTab(it) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Display screens
                        when (currentTab) {
                            0 -> LibraryScreen(viewModel = viewModel)
                            2 -> SettingsScreen(viewModel = viewModel)
                        }
                    }
                }

                // Overlay order matters: later composables draw on top, and the
                // BackHandler pops them in the same order. The reader sits above
                // the tab content; the add/edit modal sits above everything, so
                // editing an entry from inside the reader always opens the modal
                // on top of it (and back closes the modal, re-revealing the reader).

                // 1. Performance Reader Screen (Full Screen Overlay)
                AnimatedVisibility(
                    visible = selectedNaat != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    selectedNaat?.let { naat ->
                        LyricsReaderScreen(
                            naat = naat,
                            viewModel = viewModel,
                            onClose = { viewModel.selectNaat(null) }
                        )
                    }
                }

                // 2. Add New / Edit Modal Overlay (Full Screen, topmost layer)
                AnimatedVisibility(
                    visible = showAddModal,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    AddNaatModal(
                        viewModel = viewModel,
                        onClose = { viewModel.setShowAddModal(false) }
                    )
                }
            }
        }
    }
}

// --- Dynamic Bottom Navigation Bar ---
@Composable
fun NaatBottomNavigation(
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    // Child composables must NEVER re-read the system dark flag: the app's
    // explicit White/Black/System mode is already resolved into the colorScheme.
    // Trusting the scheme keeps every component consistent with the chosen mode.
    val bg = MaterialTheme.colorScheme.background
    val textAndIconsColor = MaterialTheme.colorScheme.onBackground
    val borderCol = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .background(bg)
            .navigationBarsPadding()
    ) {
        // Divider line at top matching HTML's border-t border-[#1F1F1F]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(borderCol)
        )

        // Custom Navigation Bar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. My Library Tab
            val isLibrarySelected = currentTab == 0
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabSelected(0) }
                    )
                    .testTag("nav_tab_library"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(
                            if (isLibrarySelected) textAndIconsColor.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "My Library",
                        tint = if (isLibrarySelected) textAndIconsColor else textAndIconsColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "LIBRARY",
                    fontSize = 10.sp,
                    fontWeight = if (isLibrarySelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLibrarySelected) textAndIconsColor else textAndIconsColor.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
            }

            // 2. Middle floating prominent "+" Button raised up
            Box(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = (-16).dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(textAndIconsColor)
                        .clickable { onTabSelected(1) }
                        .testTag("nav_tab_add"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add new entry",
                        tint = bg,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 3. App Settings Tab
            val isSettingsSelected = currentTab == 2
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabSelected(2) }
                    )
                    .testTag("nav_tab_settings"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(
                            if (isSettingsSelected) textAndIconsColor.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "App Settings",
                        tint = if (isSettingsSelected) textAndIconsColor else textAndIconsColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "SETTINGS",
                    fontSize = 10.sp,
                    fontWeight = if (isSettingsSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSettingsSelected) textAndIconsColor else textAndIconsColor.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// --- Tab A: My Library Screen ---
@Composable
fun LibraryScreen(viewModel: NaatViewModel) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val filteredNaats by viewModel.filteredNaats.collectAsState()
    val allNaatsList by viewModel.allNaats.collectAsState()
    val favoritesOnly by viewModel.showFavoritesOnly.collectAsState()

    // Entry pending deletion - deletions also remove attached audio, so confirm first
    var deleteCandidate by remember { mutableStateOf<NaatEntity?>(null) }

    // Speech-to-Text Voice input launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spokenText = results?.firstOrNull() ?: ""
                viewModel.setSearchQuery(spokenText)
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val searchBg = MaterialTheme.colorScheme.surfaceVariant
        val searchBorder = MaterialTheme.colorScheme.outline

        // Core Top Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, searchBorder, RoundedCornerShape(24.dp))
                .background(searchBg)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search icon",
                tint = HighContrastGray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            // BasicTextField is used instead of OutlinedTextField: the M3 text field
            // enforces a 56.dp minimum height, which clipped the text in this 48.dp
            // pill. Here we control padding, line height and vertical centering
            // ourselves so the placeholder/input can never be cut off.
            BasicTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .wrapContentHeight(Alignment.CenterVertically)
                    .testTag("global_search_input"),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search Naats, Poets, Lyrics...",
                                color = HighContrastGray,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    lineHeight = 20.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            IconButton(
                onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak Naat keyword...")
                        }
                        speechRecognizerLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Speech-to-Text not supported or offline", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("speech_to_text_mic")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice search",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedFolder == null) {
            if (searchQuery.isBlank() && !favoritesOnly) {
            // Main Folder System Grid View with Recent entries
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIBRARY FOLDERS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastGray,
                        letterSpacing = 1.5.sp
                    )
                    // Quick filter: show only starred/favorite entries (across all folders)
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { viewModel.toggleFavoritesOnly() },
                        label = { Text("Favorites", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.testTag("favorites_filter_chip")
                    )
                }
                
                // Taxonomy is defined once in NaatCategories: content types only,
                // never media formats (the old mixed list had an "Audio Only"
                // folder; audio attachments now live inside any category).
                // Folder glyphs: six hand-crafted vector drawables carrying the
                // correct symbolism (Kaaba/Gumbad/masjid-skyline/Zulfiqar/ode
                // scroll/voice waveform); My Kalam & Others keep their approved
                // stock icons. All painters are tinted onBackground by the card.
                val kaabaPainter = painterResource(R.drawable.ic_cat_kaaba)
                val domePainter = painterResource(R.drawable.ic_cat_dome)
                val skylinePainter = painterResource(R.drawable.ic_cat_skyline)
                val zulfiqarPainter = painterResource(R.drawable.ic_cat_zulfiqar)
                val scrollPainter = painterResource(R.drawable.ic_cat_scroll)
                val wavesPainter = painterResource(R.drawable.ic_cat_waves)
                val editPainter = rememberVectorPainter(Icons.Default.Edit)
                val folderStockPainter = rememberVectorPainter(Icons.Default.Folder)
                val folderIcons: Map<String, Painter> = mapOf(
                    NaatCategories.NAAT to domePainter,
                    NaatCategories.HAMD to kaabaPainter,
                    NaatCategories.MANQABAT to zulfiqarPainter,
                    NaatCategories.SALAM to skylinePainter,
                    NaatCategories.QASIDA to scrollPainter,
                    NaatCategories.NASHEED to wavesPainter,
                    NaatCategories.MY_KALAM to editPainter,
                    NaatCategories.OTHERS to folderStockPainter
                )

                // Grid layout (using nested Rows to avoid nested scrolling parent errors)
                val folderRows = NaatCategories.ALL.chunked(2)
                folderRows.forEachIndexed { rowIndex, rowFolders ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowFolders.forEachIndexed { index, folder ->
                            FolderSleekCard(
                                folder = folder,
                                iconPainter = folderIcons[folder] ?: folderStockPainter,
                                count = allNaatsList.count { it.category.equals(folder, ignoreCase = true) },
                                onClick = { viewModel.selectFolder(folder) },
                                modifier = Modifier.weight(1f)
                            )
                            if (index < rowFolders.lastIndex) {
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }
                        // Keep the grid rectangular if the taxonomy ever gains an odd count
                        if (rowFolders.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (rowIndex < folderRows.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Recent entries list representing "Recent Notebooks" in the mockup HTML
                val recentNaats = allNaatsList.take(3)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT NOTEBOOKS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastGray,
                        letterSpacing = 1.5.sp
                    )
                }

                if (recentNaats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No entries in folders yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HighContrastGray
                        )
                    }
                } else {
                    recentNaats.forEach { naat ->
                        NaatRowItem(
                            naat = naat,
                            onItemClick = { viewModel.selectNaat(naat) },
                            onDeleteClick = { deleteCandidate = naat },
                            onFavoriteClick = { viewModel.toggleFavorite(naat) },
                            onEditClick = { viewModel.startEditNaat(naat) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Content spacing buffer
                Spacer(modifier = Modifier.height(80.dp))
            }
            } else {
                // Results list: global search across folders, and/or the favorites filter
                Text(
                    text = when {
                        favoritesOnly && searchQuery.isNotBlank() -> "FAVORITES · \"${searchQuery.trim().uppercase()}\""
                        favoritesOnly -> "FAVORITES"
                        else -> "RESULTS FOR \"${searchQuery.trim().uppercase()}\""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastGray,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
                if (filteredNaats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (favoritesOnly && searchQuery.isBlank()) {
                                "No favorites yet.\nStar any entry with the heart icon to see it here."
                            } else {
                                "No matches found.\nTry a different title, poet, or lyric phrase."
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HighContrastGray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredNaats) { naat ->
                        NaatRowItem(
                            naat = naat,
                            onItemClick = { viewModel.selectNaat(naat) },
                            onDeleteClick = { deleteCandidate = naat },
                            onFavoriteClick = { viewModel.toggleFavorite(naat) },
                            onEditClick = { viewModel.startEditNaat(naat) }
                        )
                        }
                    }
                }
            }
        } else {
            // Inside Folder List-View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.selectFolder(null) },
                    modifier = Modifier.testTag("back_to_folders")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to folders",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedFolder ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            if (filteredNaats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No entries in this folder yet.\nTap (+) below to add your first entry!",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HighContrastGray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredNaats) { naat ->
                        NaatRowItem(
                            naat = naat,
                            onItemClick = { viewModel.selectNaat(naat) },
                            onDeleteClick = { deleteCandidate = naat },
                            onFavoriteClick = { viewModel.toggleFavorite(naat) },
                            onEditClick = { viewModel.startEditNaat(naat) }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation — deletions also remove attached audio files permanently.
    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    "\"${candidate.title}\" will be permanently deleted" +
                        if (candidate.audioType != "none") " along with its attached audio." else "."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNaat(candidate)
                        deleteCandidate = null
                    },
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text("Delete", color = HighContrastRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- Folder Sleek Card ---
@Composable
fun FolderSleekCard(
    folder: String,
    iconPainter: Painter,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val cardBorder = MaterialTheme.colorScheme.outline
    // Icon chips use the inverse surface so the glyph wells read against the card
    val iconBg = MaterialTheme.colorScheme.background

    Card(
        modifier = modifier
            .height(130.dp)
            .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("folder_card_${folder.replace(" ", "_")}"),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = iconPainter,
                    contentDescription = folder,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = folder,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count ENTRIES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastGray,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// --- Row Item representation inside a folder list ---
@Composable
fun NaatRowItem(
    naat: NaatEntity,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val cardBorder = MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onItemClick)
            .testTag("naat_row_item_${naat.id}"),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = naat.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!naat.poet.isNullOrBlank()) {
                    Text(
                        text = "By ${naat.poet}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Audio Attachment indicators
                if (naat.audioType == "recorded") {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Recorded Audio Note Available",
                        tint = HighContrastGray,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(20.dp)
                    )
                } else if (naat.audioType == "local_file") {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Linked MP3 Available",
                        tint = HighContrastGray,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(20.dp)
                    )
                }

                // Edit entry
                IconButton(onClick = onEditClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit entry",
                        tint = HighContrastGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Favorite Quick Heart Tag
                IconButton(onClick = onFavoriteClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (naat.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite toggle",
                        tint = if (naat.isFavorite) HighContrastRed else HighContrastGray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Delete Entry
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete entry",
                        tint = HighContrastGray.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// --- Tab B: Add New Modal Window (Full Screen Overlay) ---
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddNaatModal(
    viewModel: NaatViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    // Edit mode: fields are pre-filled from the entry being edited (null = add new)
    val editingNaat by viewModel.editingNaat.collectAsState()

    var title by remember { mutableStateOf(editingNaat?.title ?: "") }
    var poet by remember { mutableStateOf(editingNaat?.poet ?: "") }
    // Category prefill: normalized defensively so a legacy label (or a blank)
    // can never end up pre-selected outside the current taxonomy.
    var selectedCategory by remember {
        mutableStateOf(
            editingNaat?.category?.let { NaatCategories.normalize(it) } ?: NaatCategories.DEFAULT
        )
    }
    var lyrics by remember { mutableStateOf(editingNaat?.lyrics ?: "") }
    var existingAudioRemoved by remember { mutableStateOf(false) }

    var showCategoryDropdown by remember { mutableStateOf(false) }
    
    val recordingState by viewModel.recordingState.collectAsState()
    val activeRecordingFile by viewModel.activeRecordingFile.collectAsState()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsState()
    
    // Linked local file attachment state
    var linkedFileUriStr by remember { mutableStateOf<String?>(null) }
    var linkedFileName by remember { mutableStateOf<String?>(null) }
    var isAttachingFile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Audio picker launcher for mp3/m4a devices files
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                isAttachingFile = true
                scope.launch {
                    // Copy runs on Dispatchers.IO inside the ViewModel (no UI-thread jank)
                    val copiedFile = viewModel.copyLocalFileToAppStorage(uri)
                    isAttachingFile = false
                    if (copiedFile != null) {
                        linkedFileUriStr = copiedFile.absolutePath
                        linkedFileName = "Attached file: " + copiedFile.name
                        Toast.makeText(context, "Audio file attached successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to copy audio attachment", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.startRecording()
            } else {
                Toast.makeText(context, "Microphone permission is required to record voice notes", Toast.LENGTH_LONG).show()
            }
        }
    )

    // Lyrics dictation (voice typing): recognized speech is APPENDED as a new
    // line below the existing lyrics - dictation never overwrites typed text.
    val lyricsDictationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (spoken.isNotEmpty()) {
                    lyrics = if (lyrics.isBlank()) spoken else lyrics.trimEnd() + "\n" + spoken
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Modal Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (editingNaat != null) "Edit Notebook Entry" else "Add New Notebook Entry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_add_modal")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close modal",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Text Editor Input Fields
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title (Required)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_naat_title"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = poet,
            onValueChange = { poet = it },
            label = { Text("Poet Name (Optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_naat_poet"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category Selector Dropdown
        ExposedDropdownMenuBox(
            expanded = showCategoryDropdown,
            onExpandedChange = { showCategoryDropdown = !showCategoryDropdown }
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Folder Location") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = showCategoryDropdown,
                onDismissRequest = { showCategoryDropdown = false }
            ) {
                NaatCategories.ALL.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = {
                            selectedCategory = cat
                            showCategoryDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = lyrics,
            onValueChange = { lyrics = it },
            label = { Text("Lyrics Text Area (Optional)") },
            // Nastaliq the moment the content turns to Urdu/Arabic script
            textStyle = LocalTextStyle.current.copy(
                fontFamily = if (usesArabicScript(lyrics)) NastaliqFamily else FontFamily.Default,
                lineHeight = if (usesArabicScript(lyrics)) 32.sp else LocalTextStyle.current.lineHeight
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate the kalam...")
                            }
                            lyricsDictationLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Speech-to-Text not supported on this device", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("lyrics_dictate_mic")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Dictate lyrics by voice",
                        tint = HighContrastGray
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("add_naat_lyrics"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Dual-Audio Attachment Box View
        Text(
            text = "Dual-Audio Attachments",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Current attachment (edit mode only): kept as-is unless removed or replaced
                val currentEditing = editingNaat
                if (currentEditing != null && currentEditing.audioType != "none" &&
                    activeRecordingFile == null && linkedFileUriStr == null && !existingAudioRemoved
                ) {
                    currentEditing.audioPath?.let { attachmentPath ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Current attachment",
                                    color = HighContrastGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (currentEditing.audioType == "recorded") Icons.Default.Mic else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = HighContrastGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (currentEditing.audioType == "recorded") "Voice note" else "Linked audio file",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            AudioAttachmentPreview(path = attachmentPath, viewModel = viewModel)
                            IconButton(
                                onClick = { existingAudioRemoved = true },
                                modifier = Modifier.testTag("remove_current_attachment")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove attachment",
                                    tint = HighContrastRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Feature 1: In-App Voice Recorder controls
                Text(
                    text = "Feature 1: In-App Voice Recorder",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                when (recordingState) {
                    // --- Live capture: Pause/Resume + Finish, plus timer & VU meter ---
                    RecordingState.RECORDING, RecordingState.PAUSED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (recordingState == RecordingState.RECORDING) {
                                Button(
                                    onClick = { viewModel.pauseRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = HighContrastGray),
                                    modifier = Modifier.testTag("pause_recording_btn")
                                ) {
                                    Text("Pause")
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.resumeRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground),
                                    modifier = Modifier.testTag("resume_recording_btn")
                                ) {
                                    Text("Resume")
                                }
                            }
                            Button(
                                onClick = { viewModel.stopRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = HighContrastRed),
                                modifier = Modifier.testTag("stop_recording_btn")
                            ) {
                                Text("Finish")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (recordingState == RecordingState.RECORDING) Icons.Default.FiberManualRecord else Icons.Default.Pause,
                                contentDescription = if (recordingState == RecordingState.RECORDING) "Recording in progress" else "Recording paused",
                                tint = if (recordingState == RecordingState.RECORDING) HighContrastRed else HighContrastGray,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatTime(recordingElapsedMs.toInt()),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.testTag("recording_timer")
                            )
                            RecordingVuMeter(amplitude = recordingAmplitude)
                        }
                    }
                    // --- Idle: start a take, or preview/discard/re-record the finished one ---
                    RecordingState.IDLE -> {
                        val finishedTake = activeRecordingFile
                        if (finishedTake == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onBackground
                                    ),
                                    modifier = Modifier.testTag("start_recording_btn")
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Record")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tap to Record")
                                }
                                Text(
                                    text = "No Recording",
                                    color = HighContrastGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Recording ready",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Recording ready: ${finishedTake.name}",
                                    color = HighContrastGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AudioAttachmentPreview(
                                    path = finishedTake.absolutePath,
                                    viewModel = viewModel
                                )
                                TextButton(
                                    onClick = { viewModel.discardRecording() },
                                    modifier = Modifier.testTag("discard_recording_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Discard recording",
                                        tint = HighContrastRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Discard", color = HighContrastRed)
                                }
                                // Icon-only: a text label wrapped badly in this tight row
                                IconButton(
                                    onClick = { viewModel.startRecording() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("rerecord_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Re-record",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))

                // Feature 2: Local File Attachment Linker
                Text(
                    text = "Feature 2: Link External MP3 / M4A File",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    enabled = !isAttachingFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.testTag("link_external_file_btn")
                ) {
                    if (isAttachingFile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.background
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Attaching...")
                    } else {
                        Icon(Icons.Default.MusicNote, contentDescription = "Link File")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse Local Storage")
                    }
                }

                if (!linkedFileName.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = linkedFileName ?: "",
                            color = HighContrastGray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            // Also delete the copied file from app storage (no orphans)
                            linkedFileUriStr?.let { viewModel.deleteOrphanFile(it) }
                            linkedFileUriStr = null
                            linkedFileName = null
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove attached file", tint = HighContrastRed)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Save Notebook Entry Button
        Button(
            onClick = {
                if (title.isBlank()) {
                    Toast.makeText(context, "Please enter a valid notebook title", Toast.LENGTH_SHORT).show()
                } else {
                    // Resolve audio values: a new take or link wins; otherwise the
                    // existing attachment is kept (unless explicitly removed).
                    var audioType = "none"
                    var audioPath: String? = null
                    val editing = editingNaat

                    if (activeRecordingFile != null) {
                        audioType = "recorded"
                        audioPath = activeRecordingFile?.absolutePath
                    } else if (linkedFileUriStr != null) {
                        audioType = "local_file"
                        audioPath = linkedFileUriStr
                    } else if (editing != null && !existingAudioRemoved && editing.audioType != "none") {
                        audioType = editing.audioType
                        audioPath = editing.audioPath
                    }

                    if (editing != null) {
                        viewModel.updateNaat(
                            id = editing.id,
                            title = title,
                            poet = poet,
                            category = selectedCategory,
                            lyrics = lyrics,
                            audioType = audioType,
                            audioPath = audioPath,
                            isFavorite = editing.isFavorite,
                            createdAt = editing.createdAt,
                            previousAudioPath = editing.audioPath
                        )
                        Toast.makeText(context, "Entry Updated!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addNaat(
                            title = title,
                            poet = poet,
                            category = selectedCategory,
                            lyrics = lyrics,
                            audioType = audioType,
                            audioPath = audioPath
                        )
                        Toast.makeText(context, "Notebook Entry Saved!", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_notebook_btn"),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (editingNaat != null) "Save Changes" else "Save Entry",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// --- Tab C: App Settings Screen ---
@Composable
fun SettingsScreen(viewModel: NaatViewModel) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val globalFontSize by viewModel.globalFontSize.collectAsState()

    // Create zip launcher
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.backupNotebook(uri)
            }
        }
    )

    // Open zip backup launcher
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.restoreNotebook(uri)
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "App Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // 1. Theme Configuration
        Text(
            text = "App Theme",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                listOf(
                    "system" to "Match System Settings",
                    "white" to "Total White Mode",
                    "black" to "Total Black Mode"
                ).forEach { (mode, title) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeMode(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = title, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Default Font Sizing adjustment
        Text(
            text = "Typography Adjustment",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Lyrics Default Text Size: ${globalFontSize.toInt()} sp",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = globalFontSize,
                    onValueChange = { viewModel.setGlobalFontSize(it) },
                    valueRange = 12f..36f,
                    steps = 23, // 24 steps total = 12..36 sp in exact 1-sp increments
                    modifier = Modifier.testTag("global_font_slider")
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Sample preview — real salam text in authentic Nastaliq + RTL
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = "اللَّهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ وَعَلَىٰ آلِ مُحَمَّدٍ",
                        fontSize = globalFontSize.sp,
                        fontFamily = NastaliqFamily,
                        lineHeight = (globalFontSize * 2.0f).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Local Backup & Restore Offline
        Text(
            text = "Privacy & Local Database Backups",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "All database records and audio assets are saved strictly locally. Safeguard your work below.",
            style = MaterialTheme.typography.bodySmall,
            color = HighContrastGray
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val timestamp = System.currentTimeMillis()
                    val fileName = "NaatNotebook_backup_$timestamp.zip"
                    exportBackupLauncher.launch(fileName)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("export_backup_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Export")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export ZIP", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("import_backup_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Upload, contentDescription = "Import")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import ZIP", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "My Naat Notebook v1.0.0\n100% Offline | Zero Data Tracking",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = HighContrastGray,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// --- View D: Realtime Lyrics Reader & Distraction-free Performance Screen ---
@Composable
fun LyricsReaderScreen(
    naat: NaatEntity,
    viewModel: NaatViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Keep the screen on while reciting — the reader must never dim mid-performance.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // Auto-Scroll engine configurations
    var autoScrollActive by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1f) } // Speed multiplier mapping
    
    // Sizing controls
    val defaultFontSize by viewModel.globalFontSize.collectAsState()
    var localFontSize by remember { mutableStateOf(defaultFontSize) }
    
    // Player controls
    val audioPlayer = viewModel.audioPlayer
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val currentPos by audioPlayer.currentPosition.collectAsState()
    val audioDuration by audioPlayer.duration.collectAsState()

    // Sync local font size if global changes
    LaunchedEffect(defaultFontSize) {
        localFontSize = defaultFontSize
    }

    // Auto-scroll loop engine coroutine. The step is dp-based, so the pace is
    // consistent across screen densities; it stops on its own at the bottom.
    val density = LocalDensity.current
    LaunchedEffect(autoScrollActive, scrollSpeed) {
        if (autoScrollActive) {
            while (isActive) {
                val stepPx = with(density) { (1.5f * scrollSpeed).dp.toPx() }
                scrollState.scrollBy(stepPx)
                delay(30)
                if (scrollState.value >= scrollState.maxValue) {
                    autoScrollActive = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        audioPlayer.stop()
                        onClose()
                    },
                    modifier = Modifier.testTag("close_reader")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close Reader",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = naat.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (!naat.poet.isNullOrBlank()) "Poet: ${naat.poet}" else "Unknown Poet",
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleFavorite(naat) },
                    modifier = Modifier.testTag("reader_favorite_toggle")
                ) {
                    Icon(
                        imageVector = if (naat.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle favorite",
                        tint = if (naat.isFavorite) HighContrastRed else MaterialTheme.colorScheme.onBackground
                    )
                }

                // Edit this entry right from the reader
                IconButton(
                    onClick = { viewModel.startEditNaat(naat) },
                    modifier = Modifier.testTag("reader_edit_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit entry",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        bottomBar = {
            // Media Control Widget: Sticky mini-player widget fixed at the bottom
            if (naat.audioType != "none" && !naat.audioPath.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (naat.audioType == "recorded") Icons.Default.Mic else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = HighContrastGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (naat.audioType == "recorded") "playing Voice Note" else "playing Linked Audio",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = HighContrastGray
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    audioPlayer.pause()
                                } else if (audioPlayer.hasActiveSession()) {
                                    audioPlayer.resume()
                                } else {
                                    audioPlayer.play(naat.audioPath!!)
                                }
                            },
                            modifier = Modifier.testTag("reader_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Progress bar seeking
                        Slider(
                            value = currentPos.toFloat(),
                            onValueChange = { audioPlayer.seekTo(it.toInt()) },
                            valueRange = 0f..(if (audioDuration > 0) audioDuration.toFloat() else 1000f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_audio_seekbar")
                        )

                        // Duration text indicator
                        val posStr = formatTime(currentPos)
                        val durStr = formatTime(audioDuration)
                        Text(
                            text = "$posStr / $durStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = HighContrastGray
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            // Instant Typography Slider & Auto Scroll Widgets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sizing controller
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Font size icon", modifier = Modifier.size(16.dp), tint = HighContrastGray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Font:", style = MaterialTheme.typography.bodySmall, color = HighContrastGray)
                    Slider(
                        value = localFontSize,
                        onValueChange = { localFontSize = it },
                        valueRange = 12f..36f,
                        steps = 23, // 24 steps total = 12..36 sp in exact 1-sp increments
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Auto Scroll Control
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { autoScrollActive = !autoScrollActive },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (autoScrollActive) MaterialTheme.colorScheme.onBackground else HighContrastGray
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("auto_scroll_toggle")
                    ) {
                        Text(
                            text = if (autoScrollActive) "Scrolling" else "AutoScroll",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (autoScrollActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        // Clickable speed slider to change pace (1x, 1.25x, 1.5x, etc.)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(1f, 1.5f, 2f).forEach { speed ->
                                Text(
                                    text = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "x",
                                    fontSize = 11.sp,
                                    fontWeight = if (scrollSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                    color = if (scrollSpeed == speed) MaterialTheme.colorScheme.onBackground else HighContrastGray,
                                    modifier = Modifier
                                        .clickable { scrollSpeed = speed }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main distraction free lyrics display screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                if (naat.lyrics.isNullOrBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No written lyrics provided.\nReady to recite with attached media!",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HighContrastGray
                        )
                    }
                } else {
                    // Urdu/Arabic-script lyrics get the Nastaliq family, an RTL
                    // paragraph direction and more generous line air; Latin keeps
                    // the classic Serif look. Centered couplets stay centered.
                    val urduLyrics = usesArabicScript(naat.lyrics)
                    CompositionLocalProvider(
                        LocalLayoutDirection provides
                                if (urduLyrics) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = naat.lyrics,
                                fontSize = localFontSize.sp,
                                fontFamily = if (urduLyrics) NastaliqFamily else FontFamily.Serif,
                                lineHeight = (localFontSize * if (urduLyrics) 2.0f else 1.6f).sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reader_lyrics_text")
                            )
                            // Extra paddings at bottom so user can scroll items beyond view lines
                            Spacer(modifier = Modifier.height(180.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- Live VU meter for the recorder: bars light up with the mic amplitude ---
@Composable
fun RecordingVuMeter(
    amplitude: Int,
    modifier: Modifier = Modifier,
    barCount: Int = 14
) {
    val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = normalized, label = "vu_meter")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            val frac = (index + 1f) / barCount
            val isActive = animated >= frac * 0.9f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((6f + 16f * frac).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isActive) HighContrastRed
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
                    )
            )
        }
    }
}

// --- Inline audio preview (play/pause + position) for the add/edit modal ---
@Composable
fun AudioAttachmentPreview(
    path: String,
    viewModel: NaatViewModel
) {
    val player = viewModel.audioPlayer
    val isPlaying by player.isPlaying.collectAsState()
    val currentPos by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()
    var started by remember(path) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                when {
                    isPlaying -> player.pause()
                    started -> player.resume()
                    else -> {
                        player.play(path)
                        started = true
                    }
                }
            },
            modifier = Modifier.testTag("audio_preview_play")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = if (started) "${formatTime(currentPos)} / ${formatTime(duration)}" else "Preview",
            style = MaterialTheme.typography.bodySmall,
            color = HighContrastGray
        )
    }
}

// Formatting seconds duration
private fun formatTime(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

// Arabic-script detection (Urdu/Persian/Punjabi/Arabic): drives the Nastaliq
// font selection and RTL layout direction. Anything Latin stays Serif + LTR.
private val arabicScriptRegex =
    Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")

private fun usesArabicScript(text: String?): Boolean =
    !text.isNullOrEmpty() && arabicScriptRegex.containsMatchIn(text)

package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.RecordingState
import com.example.data.NaatEntity
import com.example.ui.theme.MyApplicationTheme
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

    // In-app back navigation: close overlays / inner screens before leaving the app.
    // Order matches the visual stack: lyrics reader -> add-entry modal -> folder view -> settings tab.
    // Only when everything is closed does back fall through to the system (and exit).
    BackHandler(
        enabled = selectedNaat != null || showAddModal || selectedFolder != null || currentTab != 0
    ) {
        when {
            selectedNaat != null -> viewModel.selectNaat(null)
            showAddModal -> viewModel.setShowAddModal(false)
            selectedFolder != null -> viewModel.selectFolder(null)
            else -> viewModel.selectTab(0)
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

                // 1. Add New Modal Overlay (Full Screen)
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

                // 2. Performance Reader Screen (Full Screen Overlay)
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
    val isDark = isSystemInDarkTheme() || !MaterialTheme.colorScheme.background.equals(Color.White)
    val bg = MaterialTheme.colorScheme.background
    val textAndIconsColor = MaterialTheme.colorScheme.onBackground
    val borderCol = if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5E5)

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
                        contentDescription = "The Big Add",
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
        val isDark = isSystemInDarkTheme() || !MaterialTheme.colorScheme.background.equals(Color.White)
        val searchBg = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF2F2F2)
        val searchBorder = if (isDark) Color(0xFF333333) else Color(0xFFE5E5E5)

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
            if (searchQuery.isBlank()) {
            // Main Folder System Grid View with Recent entries
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "LIBRARY FOLDERS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastGray,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
                
                val foldersList = listOf(
                    "Hamd-o-Naat" to Icons.Default.AutoStories,
                    "Manqabat" to Icons.Default.Book,
                    "Salam & Qasida" to Icons.Default.Favorite,
                    "My Own Poetry" to Icons.Default.Edit,
                    "Audio Only" to Icons.Default.Mic
                )

                // Grid layout (using nested Rows to avoid nested scrolling parent errors)
                // Row 1
                Row(modifier = Modifier.fillMaxWidth()) {
                    FolderSleekCard(
                        folder = foldersList[0].first,
                        icon = foldersList[0].second,
                        count = allNaatsList.count { it.category.equals(foldersList[0].first, ignoreCase = true) },
                        onClick = { viewModel.selectFolder(foldersList[0].first) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FolderSleekCard(
                        folder = foldersList[1].first,
                        icon = foldersList[1].second,
                        count = allNaatsList.count { it.category.equals(foldersList[1].first, ignoreCase = true) },
                        onClick = { viewModel.selectFolder(foldersList[1].first) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Row 2
                Row(modifier = Modifier.fillMaxWidth()) {
                    FolderSleekCard(
                        folder = foldersList[2].first,
                        icon = foldersList[2].second,
                        count = allNaatsList.count { it.category.equals(foldersList[2].first, ignoreCase = true) },
                        onClick = { viewModel.selectFolder(foldersList[2].first) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FolderSleekCard(
                        folder = foldersList[3].first,
                        icon = foldersList[3].second,
                        count = allNaatsList.count { it.category.equals(foldersList[3].first, ignoreCase = true) },
                        onClick = { viewModel.selectFolder(foldersList[3].first) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Row 3 (Audio only occupies half column so it matches perfectly)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FolderSleekCard(
                        folder = foldersList[4].first,
                        icon = foldersList[4].second,
                        count = allNaatsList.count { it.category.equals(foldersList[4].first, ignoreCase = true) },
                        onClick = { viewModel.selectFolder(foldersList[4].first) },
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
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
                            onFavoriteClick = { viewModel.toggleFavorite(naat) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Content spacing buffer
                Spacer(modifier = Modifier.height(80.dp))
            }
            } else {
                // Global search results across every folder
                Text(
                    text = "RESULTS FOR \"${searchQuery.trim().uppercase()}\"",
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
                            text = "No matches found.\nTry a different title, poet, or lyric phrase.",
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
                                onFavoriteClick = { viewModel.toggleFavorite(naat) }
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
                        text = "No entries in this folder yet.\nTap (+) below to add your first Naat!",
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
                            onFavoriteClick = { viewModel.toggleFavorite(naat) }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme() || !MaterialTheme.colorScheme.background.equals(Color.White)
    val cardBg = if (isDark) Color(0xFF121212) else Color(0xFFF8F8F8)
    val cardBorder = if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5E5)
    val iconBg = if (isDark) Color(0xFF1A1A1A) else Color(0xFFEEEEEE)

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
                    imageVector = icon,
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
    onFavoriteClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme() || !MaterialTheme.colorScheme.background.equals(Color.White)
    val cardBg = if (isDark) Color(0xFF121212) else Color(0xFFF8F8F8)
    val cardBorder = if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5E5)

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
    
    var title by remember { mutableStateOf("") }
    var poet by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Hamd-o-Naat") }
    var lyrics by remember { mutableStateOf("") }
    
    var showCategoryDropdown by remember { mutableStateOf(false) }
    
    val recordingState by viewModel.recordingState.collectAsState()
    val activeRecordingFile by viewModel.activeRecordingFile.collectAsState()
    
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
                text = "Add New Notebook Entry",
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
                listOf("Hamd-o-Naat", "Manqabat", "Salam & Qasida", "My Own Poetry", "Audio Only").forEach { cat ->
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
                // Feature 1: In-App Voice Recorder controls
                Text(
                    text = "Feature 1: In-App Voice Recorder",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (recordingState) {
                        RecordingState.IDLE -> {
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
                        }
                        RecordingState.RECORDING -> {
                            Button(
                                onClick = { viewModel.pauseRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = HighContrastGray),
                                modifier = Modifier.testTag("pause_recording_btn")
                            ) {
                                Text("Pause")
                            }
                            Button(
                                onClick = { viewModel.stopRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = HighContrastRed),
                                modifier = Modifier.testTag("stop_recording_btn")
                            ) {
                                Text("Save Voice")
                            }
                        }
                        RecordingState.PAUSED -> {
                            Button(
                                onClick = { viewModel.resumeRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground),
                                modifier = Modifier.testTag("resume_recording_btn")
                            ) {
                                Text("Resume")
                            }
                            Button(
                                onClick = { viewModel.stopRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = HighContrastRed),
                                modifier = Modifier.testTag("stop_recording_btn")
                            ) {
                                Text("Save Voice")
                            }
                        }
                    }

                    // Display recording status text
                    Text(
                        text = when (recordingState) {
                            RecordingState.RECORDING -> "🎙️ Recording..."
                            RecordingState.PAUSED -> "⏸️ Paused"
                            else -> if (activeRecordingFile != null) "✅ Recording Attached" else "No Recording"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (activeRecordingFile != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recorded audio: ${activeRecordingFile?.name}",
                        color = HighContrastGray,
                        style = MaterialTheme.typography.bodySmall
                    )
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
                    // Resolve audio values
                    var audioType = "none"
                    var audioPath: String? = null

                    if (activeRecordingFile != null) {
                        audioType = "recorded"
                        audioPath = activeRecordingFile?.absolutePath
                    } else if (linkedFileUriStr != null) {
                        audioType = "local_file"
                        audioPath = linkedFileUriStr
                    }

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
            Text("Save Entry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            text = "Strict Theme Engine",
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
                    "black" to "Total Black Mode (OLED Friendly)"
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
                    steps = 22,
                    modifier = Modifier.testTag("global_font_slider")
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Sample Display preview
                Text(
                    text = "ہمدریافت نعت پاک نمونہ",
                    fontSize = globalFontSize.sp,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
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
    val scrollState = rememberScrollState()
    
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

    // Auto-scroll loop engine coroutine
    LaunchedEffect(autoScrollActive, scrollSpeed) {
        if (autoScrollActive) {
            while (isActive) {
                // Linear fluid calculations based on scroll speed factor
                val step = (1f * scrollSpeed).toInt().coerceAtLeast(1)
                scrollState.scrollBy(step.toFloat())
                delay(30) // linear frame rate delay
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
                    
                    Text(
                        text = if (naat.audioType == "recorded") "🎙️ playing Voice Note" else "🎵 playing Linked Audio",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = HighContrastGray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    audioPlayer.pause()
                                } else {
                                    if (currentPos > 0) {
                                        audioPlayer.pause() // player resumes internally with pause toggle
                                    } else {
                                        audioPlayer.play(naat.audioPath!!)
                                    }
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
                        steps = 22,
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
                                    text = "${speed.toInt()}x",
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = naat.lyrics,
                            fontSize = localFontSize.sp,
                            fontFamily = FontFamily.Serif,
                            lineHeight = (localFontSize * 1.6f).sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reader_ lyrics_text")
                        )
                        // Extra paddings at bottom so user can scroll items beyond view lines
                        Spacer(modifier = Modifier.height(180.dp))
                    }
                }
            }
        }
    }
}

// Formatting seconds duration
private fun formatTime(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

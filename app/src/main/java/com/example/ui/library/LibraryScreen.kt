package com.example.ui.library

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

@Composable
fun LibraryScreen(
    viewModel: NaatViewModel,
    onOpenReader: (NaatEntity) -> Unit,
    onEdit: (NaatEntity) -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val filteredNaats by viewModel.filteredNaats.collectAsState()
    val allNaatsList by viewModel.allNaats.collectAsState()
    val favoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()

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
                            onItemClick = { onOpenReader(naat) },
                            onDeleteClick = { deleteCandidate = naat },
                            onFavoriteClick = { viewModel.toggleFavorite(naat) },
                            onEditClick = { onEdit(naat) }
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
                            onItemClick = { onOpenReader(naat) },
                            onDeleteClick = { deleteCandidate = naat },
                            onFavoriteClick = { viewModel.toggleFavorite(naat) },
                            onEditClick = { onEdit(naat) }
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
                            onItemClick = { onOpenReader(naat) },
                            onDeleteClick = { deleteCandidate = naat },
                            onFavoriteClick = { viewModel.toggleFavorite(naat) },
                            onEditClick = { onEdit(naat) }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation — deletions also remove attached audio files permanently.
    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!isDeleting) deleteCandidate = null },
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
                        viewModel.deleteNaat(candidate) { deleteCandidate = null }
                    },
                    enabled = !isDeleting,
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deleting…")
                    } else {
                        Text("Delete", color = HighContrastRed)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }, enabled = !isDeleting) {
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

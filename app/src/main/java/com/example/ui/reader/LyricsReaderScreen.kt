package com.example.ui.reader

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

import com.example.ui.components.formatTime
import com.example.ui.components.usesArabicScript

@Composable
fun LyricsReaderScreen(
    naat: NaatEntity,
    viewModel: NaatViewModel,
    onClose: () -> Unit,
    onEdit: (NaatEntity) -> Unit
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

    // Player controls are scoped to this entry; a modal preview or another
    // entry can never drive this reader's transport UI.
    val playbackController = viewModel.playbackController
    val nowPlaying by playbackController.nowPlaying.collectAsState()
    val hasActiveSession by playbackController.hasActiveSession.collectAsState()
    val playerIsPlaying by playbackController.isPlaying.collectAsState()
    val playerPosition by playbackController.currentPosition.collectAsState()
    val playerDuration by playbackController.duration.collectAsState()
    val attachments = listOfNotNull(
        naat.audioPath?.let { naat.audioType to it },
        naat.secondaryAudioPath?.let { naat.secondaryAudioType to it }
    ).filter { it.first != "none" }
    var selectedAudioPath by remember(naat.id) {
        mutableStateOf(attachments.firstOrNull()?.second)
    }
    LaunchedEffect(attachments, nowPlaying?.audioPath) {
        if (nowPlaying?.naatId == naat.id) selectedAudioPath = nowPlaying?.audioPath
        else if (attachments.none { it.second == selectedAudioPath }) {
            selectedAudioPath = attachments.firstOrNull()?.second
        }
    }
    val ownsEntry = nowPlaying?.naatId == naat.id && hasActiveSession
    val ownsSelectedAttachment = ownsEntry && nowPlaying?.audioPath == selectedAudioPath
    val isPlaying = ownsSelectedAttachment && playerIsPlaying
    val currentPos = if (ownsSelectedAttachment) playerPosition else 0
    val audioDuration = if (ownsSelectedAttachment) playerDuration else 0

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
                    // Closing the reader keeps playback running (MediaSession owns
                    // the session); the global mini-player takes over control.
                    onClick = onClose,
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
                    onClick = { onEdit(naat) },
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
            if (attachments.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    if (attachments.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            attachments.forEach { (type, path) ->
                                TextButton(
                                    onClick = {
                                        selectedAudioPath = path
                                        viewModel.startEntryPlayback(naat, path)
                                    },
                                    modifier = Modifier.testTag("reader_select_$type")
                                ) {
                                    Icon(
                                        if (type == "recorded") Icons.Default.Mic else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (type == "recorded") "Voice Note" else "Linked Audio",
                                        fontWeight = if (selectedAudioPath == path) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    val selectedType = attachments.firstOrNull { it.second == selectedAudioPath }?.first
                        ?: attachments.first().first
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (selectedType == "recorded") Icons.Default.Mic else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = HighContrastGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (selectedType == "recorded") "Voice Note" else "Linked Audio",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = HighContrastGray
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val path = selectedAudioPath ?: return@IconButton
                                if (isPlaying) playbackController.pause()
                                else if (ownsSelectedAttachment) playbackController.resume()
                                else viewModel.startEntryPlayback(naat, path)
                            },
                            modifier = Modifier.testTag("reader_play_pause")
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Slider(
                            value = currentPos.toFloat(),
                            onValueChange = { playbackController.seekTo(it.toInt()) },
                            valueRange = 0f..(if (audioDuration > 0) audioDuration.toFloat() else 1000f),
                            modifier = Modifier.weight(1f).testTag("reader_audio_seekbar")
                        )
                        Text(
                            "${formatTime(currentPos)} / ${formatTime(audioDuration)}",
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

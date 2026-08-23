package com.example.ui.components

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

// --- Global Mini-Player: floating transport strip above the bottom nav ---
@Composable
fun GlobalMiniPlayer(
    viewModel: NaatViewModel,
    onOpen: () -> Unit
) {
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val player = viewModel.audioPlayer
    val hasSession by player.hasActiveSessionFlow.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    val current = nowPlaying
    if (current == null || !hasSession) return

    val fg = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mini_player")
    ) {
        // Hairline matching the nav bar's top divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("mini_player_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cat_dome),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("mini_player_title")
                )
                if (!current.poet.isNullOrBlank()) {
                    Text(
                        text = current.poet,
                        style = MaterialTheme.typography.bodySmall,
                        color = HighContrastGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { player.togglePlayPause() },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("mini_player_play_pause")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = fg,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// --- Tab A: My Library Screen ---

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
fun formatTime(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

// Arabic-script detection (Urdu/Persian/Punjabi/Arabic): drives the Nastaliq
// font selection and RTL layout direction. Anything Latin stays Serif + LTR.
private val arabicScriptRegex =
    Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")

fun usesArabicScript(text: String?): Boolean =
    !text.isNullOrEmpty() && arabicScriptRegex.containsMatchIn(text)

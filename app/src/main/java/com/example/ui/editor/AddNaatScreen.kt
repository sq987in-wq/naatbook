package com.example.ui.editor

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

import com.example.ui.components.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddNaatModal(
    viewModel: NaatViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // All user-authored values live in SavedStateHandle-backed ViewModel state.
    val draft by viewModel.editorDraft.collectAsState()
    val title = draft.title
    val poet = draft.poet
    val selectedCategory = draft.category
    val lyrics = draft.lyrics
    val existingAudioRemoved = draft.existingAudioRemoved
    val isEditing = draft.editingId != null
    val editingNaat = draft.editingId?.let { id ->
        NaatEntity(
            id = id,
            title = draft.title,
            poet = draft.poet.takeIf { it.isNotBlank() },
            category = draft.category,
            lyrics = draft.lyrics.takeIf { it.isNotBlank() },
            audioType = draft.existingAudioType,
            audioPath = draft.existingAudioPath,
            isFavorite = draft.existingFavorite,
            createdAt = draft.existingCreatedAt
        )
    }

    var showCategoryDropdown by remember { mutableStateOf(false) }

    val recordingState by viewModel.recordingState.collectAsState()
    val activeRecordingFile by viewModel.activeRecordingFile.collectAsState()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    // Copy progress is transient; copied-file ownership is part of the durable draft.
    val linkedFileUriStr = draft.newAttachmentPath
    val linkedFileName = draft.newAttachmentName
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
                        viewModel.adoptLinkedFile(copiedFile)
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
                    val updated = if (lyrics.isBlank()) spoken else lyrics.trimEnd() + "\n" + spoken
                    viewModel.updateDraft { it.copy(lyrics = updated) }
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
                text = if (isEditing) "Edit Notebook Entry" else "Add New Notebook Entry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onClose,
                enabled = !isSaving,
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
            onValueChange = { value -> viewModel.updateDraft { it.copy(title = value) } },
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
            onValueChange = { value -> viewModel.updateDraft { it.copy(poet = value) } },
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
                            viewModel.updateDraft { it.copy(category = cat) }
                            showCategoryDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = lyrics,
            onValueChange = { value -> viewModel.updateDraft { it.copy(lyrics = value) } },
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
                                onClick = { viewModel.updateDraft { it.copy(existingAudioRemoved = true) } },
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
                            viewModel.updateDraft {
                                it.copy(newAttachmentPath = null, newAttachmentName = null)
                            }
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
                    viewModel.saveDraft()
                }
            },
            enabled = !isSaving && !isAttachingFile,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_notebook_btn"),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving…", fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = if (isEditing) "Save Changes" else "Save Entry",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// --- Tab C: App Settings Screen ---

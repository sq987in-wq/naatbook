package com.example.ui.editor

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.RecordingState
import com.example.data.NaatCategories
import com.example.ui.components.AudioAttachmentPreview
import com.example.ui.components.RecordingVuMeter
import com.example.ui.components.formatTime
import com.example.ui.components.usesArabicScript
import com.example.ui.theme.HighContrastGray
import com.example.ui.theme.HighContrastRed
import com.example.ui.theme.NastaliqFamily
import com.example.viewmodel.EditorAttachmentDraft
import com.example.viewmodel.NaatViewModel

/** Keyed lazy editor: only visible sections are composed and high-frequency media state is local. */
@Composable
fun AddNaatModal(viewModel: NaatViewModel, onClose: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") { EditorHeader(viewModel, onClose) }
        item(key = "metadata") { EditorMetadataSection(viewModel) }
        item(key = "audio") { EditorAudioSection(viewModel) }
        item(key = "save") { EditorSaveSection(viewModel) }
        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun EditorHeader(viewModel: NaatViewModel, onClose: () -> Unit) {
    val editingId by viewModel.editorEntryId.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isAttaching by viewModel.isAttachingFile.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            if (editingId != null) "Edit Notebook Entry" else "Add New Notebook Entry",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(
            onClick = onClose,
            enabled = !isSaving && !isAttaching,
            modifier = Modifier.testTag("close_add_modal")
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close modal")
        }
    }
}

@Composable
private fun EditorMetadataSection(viewModel: NaatViewModel) {
    val context = LocalContext.current
    val metadata by viewModel.editorMetadata.collectAsStateWithLifecycle()
    var showCategoryDropdown by remember { mutableStateOf(false) }
    val lyricsDictationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                viewModel.updateDraft {
                    it.copy(lyrics = if (it.lyrics.isBlank()) spoken else it.lyrics.trimEnd() + "\n" + spoken)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = metadata.title,
            onValueChange = { value -> viewModel.updateDraft { it.copy(title = value) } },
            label = { Text("Title (Required)") },
            modifier = Modifier.fillMaxWidth().testTag("add_naat_title"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true
        )
        OutlinedTextField(
            value = metadata.poet,
            onValueChange = { value -> viewModel.updateDraft { it.copy(poet = value) } },
            label = { Text("Poet Name (Optional)") },
            modifier = Modifier.fillMaxWidth().testTag("add_naat_poet"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            singleLine = true
        )
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = metadata.category,
                onValueChange = {},
                readOnly = true,
                label = { Text("Folder Location") },
                trailingIcon = {
                    IconButton(onClick = { showCategoryDropdown = true }) {
                        Icon(Icons.Default.MusicNote, contentDescription = "Choose folder")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = showCategoryDropdown,
                onDismissRequest = { showCategoryDropdown = false }
            ) {
                NaatCategories.ALL.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            viewModel.updateDraft { it.copy(category = category) }
                            showCategoryDropdown = false
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = metadata.lyrics,
            onValueChange = { value -> viewModel.updateDraft { it.copy(lyrics = value) } },
            label = { Text("Lyrics Text Area (Optional)") },
            textStyle = LocalTextStyle.current.copy(
                fontFamily = if (usesArabicScript(metadata.lyrics)) NastaliqFamily else FontFamily.Default,
                lineHeight = if (usesArabicScript(metadata.lyrics)) 32.sp else LocalTextStyle.current.lineHeight
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        try {
                            lyricsDictationLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate the kalam...")
                            })
                        } catch (_: Exception) {
                            Toast.makeText(context, "Speech-to-Text not supported", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("lyrics_dictate_mic")
                ) { Icon(Icons.Default.Mic, contentDescription = "Dictate lyrics", tint = HighContrastGray) }
            },
            modifier = Modifier.fillMaxWidth().height(180.dp).testTag("add_naat_lyrics"),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
    }
}

@Composable
private fun EditorAudioSection(viewModel: NaatViewModel) {
    val context = LocalContext.current
    val attachments by viewModel.editorAttachments.collectAsStateWithLifecycle()
    val activeRecordingFile by viewModel.activeRecordingFile.collectAsStateWithLifecycle()
    val isAttaching by viewModel.isAttachingFile.collectAsStateWithLifecycle()

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !isAttaching) {
            viewModel.attachLocalFile(uri) { success ->
                Toast.makeText(
                    context,
                    if (success) "Audio file attached successfully!" else "Failed to copy audio attachment",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dual-Audio Attachments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExistingAttachments(attachments, activeRecordingFile != null, viewModel)
                Text("In-App Voice Recorder", fontWeight = FontWeight.SemiBold)
                RecorderControls(viewModel)
                HorizontalDivider()
                Text("Link External MP3 / M4A File", fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { audioPicker.launch("audio/*") },
                    enabled = !isAttaching,
                    modifier = Modifier.testTag("link_external_file_btn")
                ) {
                    if (isAttaching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Attaching...")
                    } else {
                        Icon(Icons.Default.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Browse Local Storage")
                    }
                }
                attachments.newAttachmentPath?.let { path ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            attachments.newAttachmentName ?: path.substringAfterLast('/'),
                            color = HighContrastGray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        AudioAttachmentPreview(path, viewModel)
                        IconButton(onClick = {
                            viewModel.deleteOrphanFile(path)
                            viewModel.updateDraft {
                                it.copy(newAttachmentPath = null, newAttachmentName = null)
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove linked file", tint = HighContrastRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExistingAttachments(
    draft: EditorAttachmentDraft,
    recordingReplaced: Boolean,
    viewModel: NaatViewModel
) {
    listOf(
        Triple(draft.existingAudioType, draft.existingAudioPath, false),
        Triple(draft.existingSecondaryAudioType, draft.existingSecondaryAudioPath, true)
    ).forEach { (type, path, secondary) ->
        val removed = if (secondary) draft.existingSecondaryAudioRemoved else draft.existingAudioRemoved
        val replaced = (type == "recorded" && recordingReplaced) ||
            (type == "local_file" && draft.newAttachmentPath != null)
        if (type != "none" && path != null && !removed && !replaced) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (type == "recorded") Icons.Default.Mic else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = HighContrastGray
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (type == "recorded") "Current voice note" else "Current linked audio",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                AudioAttachmentPreview(path, viewModel)
                IconButton(onClick = {
                    viewModel.updateDraft {
                        if (secondary) it.copy(existingSecondaryAudioRemoved = true)
                        else it.copy(existingAudioRemoved = true)
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove attachment", tint = HighContrastRed)
                }
            }
        }
    }
}

@Composable
private fun RecorderControls(viewModel: NaatViewModel) {
    val context = LocalContext.current
    val state by viewModel.recordingState.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeRecordingFile.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording()
        else Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_LONG).show()
    }

    when (state) {
        RecordingState.RECORDING, RecordingState.PAUSED -> {
            val elapsed by viewModel.recordingElapsedMs.collectAsStateWithLifecycle()
            val amplitude by viewModel.recordingAmplitude.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (state == RecordingState.RECORDING) viewModel.pauseRecording()
                        else viewModel.resumeRecording()
                    },
                    modifier = Modifier.testTag(
                        if (state == RecordingState.RECORDING) "pause_recording_btn" else "resume_recording_btn"
                    )
                ) { Text(if (state == RecordingState.RECORDING) "Pause" else "Resume") }
                Button(
                    onClick = viewModel::stopRecording,
                    colors = ButtonDefaults.buttonColors(containerColor = HighContrastRed),
                    modifier = Modifier.testTag("stop_recording_btn")
                ) { Text("Finish") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (state == RecordingState.RECORDING) Icons.Default.FiberManualRecord else Icons.Default.Pause,
                    contentDescription = null,
                    tint = if (state == RecordingState.RECORDING) HighContrastRed else HighContrastGray,
                    modifier = Modifier.size(14.dp)
                )
                Text(formatTime(elapsed.toInt()), fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("recording_timer"))
                RecordingVuMeter(amplitude)
            }
        }
        RecordingState.IDLE -> {
            val finishedFile = activeFile
            if (finishedFile == null) {
                Button(
                    onClick = { permission.launch(android.Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.testTag("start_recording_btn")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Tap to Record")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Recording ready: ${finishedFile.name}",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AudioAttachmentPreview(finishedFile.absolutePath, viewModel)
                    TextButton(
                        onClick = viewModel::discardRecording,
                        modifier = Modifier.testTag("discard_recording_btn")
                    ) { Text("Discard", color = HighContrastRed) }
                    IconButton(
                        onClick = viewModel::startRecording,
                        modifier = Modifier.testTag("rerecord_btn")
                    ) { Icon(Icons.Default.Refresh, contentDescription = "Re-record") }
                }
            }
        }
    }
}

@Composable
private fun EditorSaveSection(viewModel: NaatViewModel) {
    val metadata by viewModel.editorMetadata.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isAttaching by viewModel.isAttachingFile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Button(
        onClick = {
            if (metadata.title.isBlank()) {
                Toast.makeText(context, "Please enter a valid notebook title", Toast.LENGTH_SHORT).show()
            } else viewModel.saveDraft()
        },
        enabled = !isSaving && !isAttaching,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_notebook_btn")
    ) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Saving…", fontWeight = FontWeight.Bold)
        } else {
            Text(
                if (metadata.editingId != null) "Save Changes" else "Save Entry",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

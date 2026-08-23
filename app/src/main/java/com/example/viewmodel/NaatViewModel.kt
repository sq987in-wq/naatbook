package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.*
import com.example.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class NaatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NaatRepository,
    private val backupManager: BackupManager,
    private val audioRecorder: AudioRecorder,
    val playbackController: PlaybackController,
    private val settingsStore: SettingsStore
) : ViewModel() {

    init {
        // Housekeeping: delete audio files that lost their database entries
        // (cancelled attachments, crashes mid-save, failed deletes, ...).
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val referenced = repository.allNaats.first().mapNotNull { it.audioPath }.toSet()
                listOf(getRecordingsDirectory(), getLinkedDirectory()).forEach { dir ->
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && file.absolutePath !in referenced && file.delete()) {
                            Log.d("NaatViewModel", "Cleaned orphaned audio file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NaatViewModel", "Orphaned audio cleanup failed", e)
            }
        }
    }

    // Preferences & Settings are DataStore-backed; legacy values migrate in once.

    // UI Navigation Screen State
    private val _currentTab = MutableStateFlow(0) // 0: Library, 1: Add (via modal), 2: Settings
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _showAddModal = MutableStateFlow(false)
    val showAddModal: StateFlow<Boolean> = _showAddModal.asStateFlow()

    private val _selectedNaat = MutableStateFlow<NaatEntity?>(null)
    val selectedNaat: StateFlow<NaatEntity?> = _selectedNaat.asStateFlow()

    // Filter and Search States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null) // null means "All" / Folder Grid View
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    fun toggleFavoritesOnly() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _showFavoritesOnly.value = enabled
    }

    /** Returns the Library tab to its root view: no folder, no filter, no search text. */
    fun resetLibraryToHome() {
        _selectedFolder.value = null
        _showFavoritesOnly.value = false
        _searchQuery.value = ""
    }

    // App Preferences (defaults render instantly; stored values land on first emit)
    private val _themeMode = MutableStateFlow(NaatViewModelDefaults.DEFAULT_THEME_MODE)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _globalFontSize = MutableStateFlow(NaatViewModelDefaults.DEFAULT_FONT_SIZE)
    val globalFontSize: StateFlow<Float> = _globalFontSize.asStateFlow()

    init {
        // DataStore emits current settings once, then on every change
        viewModelScope.launch { settingsStore.themeMode.collect { _themeMode.value = it } }
        viewModelScope.launch { settingsStore.fontSize.collect { _globalFontSize.value = it } }
    }

    // List of cataloged naats
    val allNaats: StateFlow<List<NaatEntity>> = repository.allNaats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered list based on Search, Folders & the Favorites toggle
    val filteredNaats: StateFlow<List<NaatEntity>> = combine(
        allNaats,
        _searchQuery,
        _selectedFolder,
        _showFavoritesOnly
    ) { naats, query, folder, favoritesOnly ->
        naats.filter { naat ->
            val matchesFolder = if (folder != null) {
                naat.category.equals(folder, ignoreCase = true)
            } else {
                true
            }
            val matchesSearch = if (query.isNotEmpty()) {
                naat.title.contains(query, ignoreCase = true) ||
                (naat.poet?.contains(query, ignoreCase = true) == true) ||
                (naat.lyrics?.contains(query, ignoreCase = true) == true)
            } else {
                true
            }
            val matchesFavorite = !favoritesOnly || naat.isFavorite
            matchesFolder && matchesSearch && matchesFavorite
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active recording file state (for adding a new Naat)
    private val _activeRecordingFile = MutableStateFlow<File?>(null)
    val activeRecordingFile: StateFlow<File?> = _activeRecordingFile.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    // Live recording meter (elapsed time + amplitude for the VU meter)
    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0)
    val recordingAmplitude: StateFlow<Int> = _recordingAmplitude.asStateFlow()

    private var recordingMeterJob: Job? = null

    // Entry currently being edited in the modal (null = "add new" mode)
    private val _editingNaat = MutableStateFlow<NaatEntity?>(null)
    val editingNaat: StateFlow<NaatEntity?> = _editingNaat.asStateFlow()

    private fun startRecordingMeter() {
        recordingMeterJob?.cancel()
        _recordingElapsedMs.value = 0L
        recordingMeterJob = viewModelScope.launch {
            while (isActive) {
                _recordingElapsedMs.value = audioRecorder.getElapsedMs()
                _recordingAmplitude.value = audioRecorder.getMaxAmplitude()
                delay(120)
            }
        }
    }

    private fun stopRecordingMeter() {
        recordingMeterJob?.cancel()
        recordingMeterJob = null
        _recordingElapsedMs.value = 0L
        _recordingAmplitude.value = 0
    }

    // Backup & Restore status notifications
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun selectTab(index: Int) {
        if (index == 1) {
            _showAddModal.value = true
        } else {
            _currentTab.value = index
        }
    }

    fun setShowAddModal(show: Boolean) {
        _showAddModal.value = show
        if (!show) {
            // If cancelled, clean up any active temporary recording
            _activeRecordingFile.value?.let { file ->
                if (file.exists() && _recordingState.value == RecordingState.IDLE) {
                    file.delete()
                }
            }
            _activeRecordingFile.value = null
            _recordingState.value = RecordingState.IDLE
            _editingNaat.value = null
            audioRecorder.stop()
            // Stop only the UI-owned preview; service-owned entry playback survives.
            playbackController.stopPreview()
            stopRecordingMeter()
        }
    }

    fun selectNaat(naat: NaatEntity?) {
        // Closing the reader (null) leaves playback running — the MediaSession
        // owns that session in the background now. Only switching directly to a
        // DIFFERENT entry (or deleting it) stops the old audio: anti-bleed rule.
        if (naat != null &&
            naat.id != _selectedNaat.value?.id &&
            naat.id != playbackController.nowPlaying.value?.naatId
        ) {
            playbackController.stop()
        }
        _selectedNaat.value = naat
    }

    /** Starts a media-session-owned listening session for an entry (from the reader). */
    fun startEntryPlayback(naat: NaatEntity) {
        playbackController.playEntry(naat)
    }

    /** Mini-player tap: jump straight back into the playing entry's reader. */
    fun openNowPlayingEntry() {
        val current = playbackController.nowPlaying.value ?: return
        val entry = allNaats.value.firstOrNull { it.id == current.naatId }
        if (entry != null) {
            _selectedNaat.value = entry // direct set: reopening the SAME entry must not stop its audio
        } else {
            // Entry was deleted out from under the session
            playbackController.stop()
        }
    }

    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Audio Recording Controls (For Add Naat Modal) ---
    private fun getRecordingsDirectory(): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun startRecording() {
        // A previously abandoned recording (never attached to an entry) is replaced — delete it
        _activeRecordingFile.value?.let { old ->
            if (old.exists()) viewModelScope.launch(Dispatchers.IO) { old.delete() }
        }
        playbackController.stop() // prevent playback feedback while capturing a fresh take
        val timestamp = System.currentTimeMillis()
        val file = File(getRecordingsDirectory(), "record_$timestamp.m4a")
        _activeRecordingFile.value = file
        audioRecorder.start(file)
        _recordingState.value = audioRecorder.getRecordingState()
        if (_recordingState.value == RecordingState.RECORDING) {
            startRecordingMeter()
        }
    }

    fun pauseRecording() {
        audioRecorder.pause()
        _recordingState.value = audioRecorder.getRecordingState()
        _recordingAmplitude.value = 0 // VU meter flattens while paused
    }

    fun resumeRecording() {
        audioRecorder.resume()
        _recordingState.value = audioRecorder.getRecordingState()
    }

    fun stopRecording() {
        audioRecorder.stop()
        _recordingState.value = RecordingState.IDLE
        stopRecordingMeter()
    }

    /** Discard the finished take: stop playback, delete the file, reset state. */
    fun discardRecording() {
        audioRecorder.stop()
        playbackController.stop()
        _recordingState.value = RecordingState.IDLE
        stopRecordingMeter()
        _activeRecordingFile.value?.let { file ->
            if (file.exists()) viewModelScope.launch(Dispatchers.IO) { file.delete() }
        }
        _activeRecordingFile.value = null
    }

    /** Open the add/edit modal pre-filled with an existing entry. */
    fun startEditNaat(naat: NaatEntity) {
        // Editing the entry invalidates its playing session (attachment may change)
        playbackController.stop()
        _editingNaat.value = naat
        _showAddModal.value = true
    }

    // --- Local Device File Attachment ---
    private fun getLinkedDirectory(): File {
        val dir = File(context.filesDir, "linked")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // Runs on Dispatchers.IO: audio files can be tens of MB, and copying them
    // on the main thread risks jank/ANRs.
    suspend fun copyLocalFileToAppStorage(uri: Uri): File? = withContext(Dispatchers.IO) {
        val fileName = "linked_${System.currentTimeMillis()}_file.mp3"
        val destFile = File(getLinkedDirectory(), fileName)
        try {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                null
            } else {
                input.use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                destFile
            }
        } catch (e: Exception) {
            Log.e("NaatViewModel", "Failed to copy local file", e)
            destFile.delete() // remove any partially copied file
            null
        }
    }

    /** Deletes a file inside the app's private storage (e.g. a removed attachment). */
    fun deleteOrphanFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(path)
            if (file.absolutePath.startsWith(context.filesDir.absolutePath) && file.exists()) {
                file.delete()
            }
        }
    }

    // --- CRUD DB Operations ---
    fun addNaat(
        title: String,
        poet: String?,
        category: String,
        lyrics: String?,
        audioType: String,
        audioPath: String?
    ) {
        // Always stop the recorder first — previously, saving mid-recording left the
        // MediaRecorder running with no way to stop it (hot mic + half-written file).
        audioRecorder.stop()
        _recordingState.value = RecordingState.IDLE

        // An instantly-stopped recording can be an empty/invalid file; don't attach it.
        val finalAudioPath = if (audioType == "recorded" && audioPath != null) {
            val f = File(audioPath)
            if (f.exists() && f.length() > 0L) audioPath else {
                f.delete()
                null
            }
        } else {
            audioPath
        }
        val finalAudioType = if (audioType == "recorded" && finalAudioPath == null) "none" else audioType

        viewModelScope.launch {
            val naat = NaatEntity(
                title = title,
                poet = if (poet.isNullOrBlank()) null else poet,
                category = category,
                lyrics = if (lyrics.isNullOrBlank()) null else lyrics,
                audioType = finalAudioType,
                audioPath = finalAudioPath,
                isFavorite = false
            )
            repository.insert(naat)
            // Clear the recording reference BEFORE closing, so the modal cleanup in
            // setShowAddModal(false) doesn't delete the file we just attached.
            _activeRecordingFile.value = null
            setShowAddModal(false)
        }
    }

    /**
     * Full edit of an existing entry. Attachments are preserved unless the user
     * recorded a new take, linked a new file, or removed the attachment — in which
     * case the previous app-owned audio file is deleted.
     */
    fun updateNaat(
        id: Int,
        title: String,
        poet: String?,
        category: String,
        lyrics: String?,
        audioType: String,
        audioPath: String?,
        isFavorite: Boolean,
        createdAt: Long,
        previousAudioPath: String?
    ) {
        audioRecorder.stop()
        playbackController.stop()
        _recordingState.value = RecordingState.IDLE
        stopRecordingMeter()

        val finalAudioPath = if (audioType == "recorded" && audioPath != null) {
            val f = File(audioPath)
            if (f.exists() && f.length() > 0L) audioPath else {
                f.delete()
                null
            }
        } else {
            audioPath
        }
        val finalAudioType = if (audioType == "recorded" && finalAudioPath == null) "none" else audioType

        viewModelScope.launch {
            val updated = NaatEntity(
                id = id,
                title = title,
                poet = if (poet.isNullOrBlank()) null else poet,
                category = category,
                lyrics = if (lyrics.isNullOrBlank()) null else lyrics,
                audioType = finalAudioType,
                audioPath = finalAudioPath,
                isFavorite = isFavorite,
                createdAt = createdAt
            )
            repository.update(updated)
            // Delete the replaced/removed audio file (only if app-owned and actually changed)
            if (!previousAudioPath.isNullOrEmpty() && previousAudioPath != finalAudioPath) {
                val old = File(previousAudioPath)
                if (old.exists() && old.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    old.delete()
                }
            }
            if (_selectedNaat.value?.id == id) {
                _selectedNaat.value = updated // keep an open reader in sync
            }
            _activeRecordingFile.value = null
            setShowAddModal(false)
        }
    }

    fun toggleFavorite(naat: NaatEntity) {
        viewModelScope.launch {
            val updated = naat.copy(isFavorite = !naat.isFavorite)
            repository.update(updated)
            if (_selectedNaat.value?.id == naat.id) {
                _selectedNaat.value = updated
            }
        }
    }

    fun deleteNaat(naat: NaatEntity) {
        viewModelScope.launch {
            // 1. Delete associated audio file physically if recorded/linked to prevent ghost leakages
            if (!naat.audioPath.isNullOrEmpty()) {
                val file = File(naat.audioPath)
                if (file.exists() && file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    val deleted = file.delete()
                    Log.d("NaatViewModel", "Deleted associated file ${file.name}: $deleted")
                }
            }
            // 2. Delete entry from Database
            repository.delete(naat)
            if (_selectedNaat.value?.id == naat.id) {
                _selectedNaat.value = null
            }
            if (playbackController.nowPlaying.value?.naatId == naat.id) {
                playbackController.stop()
            }
        }
    }

    // --- Preferences configuration (suspend writes -> exactly one DataStore
    // transaction per committed user action, never per slider tick) ---
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setGlobalFontSize(size: Float) {
        _globalFontSize.value = size
        viewModelScope.launch { settingsStore.setFontSize(size) }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // --- Backup & Restore ---
    fun backupNotebook(uri: Uri) {
        viewModelScope.launch {
            _statusMessage.value = "Exporting backup, please wait..."
            val result = backupManager.exportBackup(uri)
            result.onSuccess {
                _statusMessage.value = "Library Backup Exported Successfully!"
            }.onFailure {
                _statusMessage.value = "Export Failed: ${it.localizedMessage}"
            }
        }
    }

    fun restoreNotebook(uri: Uri) {
        viewModelScope.launch {
            _statusMessage.value = "Importing backup, please wait..."
            val result = backupManager.importBackup(uri)
            result.onSuccess { count ->
                _statusMessage.value = "Library Restored Successfully! Loaded $count entries."
            }.onFailure {
                _statusMessage.value = "Import Failed: ${it.localizedMessage}"
            }
        }
    }

    /**
     * Called when the app goes to the background: stop playback and finalize any
     * in-progress take so the recording stays complete and previewable on return.
     */
    fun onAppBackgrounded() {
        // Entry sessions survive; UI-owned previews do not.
        playbackController.stopPreview()
        if (_recordingState.value != RecordingState.IDLE) {
            audioRecorder.stop()
            _recordingState.value = RecordingState.IDLE
            stopRecordingMeter()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // PlaybackController is process-scoped; ViewModel teardown must not end
        // a service-owned listening session.
        audioRecorder.stop()
        stopRecordingMeter()
    }
}

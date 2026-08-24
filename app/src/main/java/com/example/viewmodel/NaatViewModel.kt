package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
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

/** Serializable editor values only; audio payloads remain in app-owned files. */
data class EditorDraft(
    val active: Boolean = false,
    val editingId: Int? = null,
    val title: String = "",
    val poet: String = "",
    val category: String = NaatCategories.DEFAULT,
    val lyrics: String = "",
    val existingAudioRemoved: Boolean = false,
    val existingAudioType: String = "none",
    val existingAudioPath: String? = null,
    val existingSecondaryAudioRemoved: Boolean = false,
    val existingSecondaryAudioType: String = "none",
    val existingSecondaryAudioPath: String? = null,
    val existingFavorite: Boolean = false,
    val existingCreatedAt: Long = 0L,
    val newAttachmentPath: String? = null,
    val newAttachmentName: String? = null,
    val finishedRecordingPath: String? = null
)

@HiltViewModel
class NaatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val repository: NaatRepository,
    private val backupManager: BackupManager,
    private val audioRecorder: AudioRecorder,
    val playbackController: PlaybackController,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val draftStore = EditorDraftStore(savedStateHandle)
    private val initialDraft = draftStore.restore()
    private val _editorDraft = MutableStateFlow(initialDraft)
    val editorDraft: StateFlow<EditorDraft> = _editorDraft.asStateFlow()

    private val saveGate = OperationGate()
    private val deleteGate = OperationGate()
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()
    private val _isOpeningNowPlaying = MutableStateFlow(false)
    val isOpeningNowPlaying: StateFlow<Boolean> = _isOpeningNowPlaying.asStateFlow()

    init {
        // Housekeeping: delete audio files that lost their database entries
        // (cancelled attachments, crashes mid-save, failed deletes, ...).
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val referenced = buildSet {
                    repository.allNaats.first().forEach { naat ->
                        naat.audioPath?.let(::add)
                        naat.secondaryAudioPath?.let(::add)
                    }
                    initialDraft.newAttachmentPath?.let(::add)
                    initialDraft.finishedRecordingPath?.let(::add)
                }
                listOf(
                    getRecordingsDirectory(),
                    getLinkedDirectory(),
                    getImportedAudioDirectory()
                ).forEach { dir ->
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

    private val _showAddModal = MutableStateFlow(initialDraft.active)
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
    private val _activeRecordingFile = MutableStateFlow(
        initialDraft.finishedRecordingPath?.let(::File)?.takeIf { it.isFile }
    )
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

    private fun persistDraft(draft: EditorDraft) {
        _editorDraft.value = draft
        // Exactly one lyrics value is retained; audio bytes are never placed in saved state.
        draftStore.save(draft)
    }

    fun updateDraft(transform: (EditorDraft) -> EditorDraft) {
        persistDraft(transform(_editorDraft.value))
    }

    private fun clearDraft() = persistDraft(EditorDraft())

    fun startAddDraft(forceFresh: Boolean = false) {
        if (_editorDraft.value.active && !forceFresh) return
        if (forceFresh) {
            // '+' is an explicit new-entry action, never a request to reuse an old edit draft.
            audioRecorder.stop()
            _recordingState.value = RecordingState.IDLE
            stopRecordingMeter()
            playbackController.stopPreview()
            val old = _editorDraft.value
            DraftFileCleanup.discard(
                old.existingAudioPath,
                listOf(_activeRecordingFile.value?.absolutePath,
                    old.finishedRecordingPath, old.newAttachmentPath)
            )
            _activeRecordingFile.value = null
        }
        persistDraft(EditorDraft(active = true))
        _editingNaat.value = null
        _showAddModal.value = true
    }

    fun selectTab(index: Int) {
        if (index == 1) startAddDraft(forceFresh = true) else _currentTab.value = index
    }

    /** Explicit cancellation/discard. Recorder finalization always precedes file cleanup. */
    fun setShowAddModal(show: Boolean) {
        if (show) {
            if (!_editorDraft.value.active) startAddDraft() else _showAddModal.value = true
            return
        }
        if (_isSaving.value) return
        audioRecorder.stop()
        _recordingState.value = RecordingState.IDLE
        stopRecordingMeter()
        // Stop only the UI-owned preview; service-owned entry playback survives.
        playbackController.stopPreview()

        val draft = _editorDraft.value
        DraftFileCleanup.discard(
            existingSavedPath = draft.existingAudioPath,
            temporaryPaths = listOf(
                _activeRecordingFile.value?.absolutePath,
                draft.finishedRecordingPath,
                draft.newAttachmentPath
            )
        )
        _activeRecordingFile.value = null
        _editingNaat.value = null
        clearDraft()
        _showAddModal.value = false
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
        naat.audioPath?.let { playbackController.playEntry(naat, it) }
    }

    fun startEntryPlayback(naat: NaatEntity, path: String) {
        playbackController.playEntry(naat, path)
    }

    /** Mini-player tap: resolve through Room; the list flow may still hold its initial empty value. */
    fun openNowPlayingEntry(onResolved: (Boolean) -> Unit = {}) {
        if (_isOpeningNowPlaying.value) return
        val requested = playbackController.nowPlaying.value ?: return
        _isOpeningNowPlaying.value = true
        viewModelScope.launch {
            try {
                val entry = repository.getNaatById(requested.naatId)
                // Ignore a result for a session replaced while Room was queried.
                if (playbackController.nowPlaying.value?.naatId != requested.naatId) return@launch
                if (entry != null) {
                    _selectedNaat.value = entry
                    onResolved(true)
                } else {
                    playbackController.stop() // database confirmed deletion
                    onResolved(false)
                }
            } catch (e: Exception) {
                Log.e("NaatViewModel", "Unable to open now-playing entry", e)
                _statusMessage.value = "Unable to open the playing entry: ${e.localizedMessage ?: "database error"}"
                onResolved(false) // preserve playback when lookup itself failed
            } finally {
                _isOpeningNowPlaying.value = false
            }
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
        val draft = _editorDraft.value
        setOfNotNull(_activeRecordingFile.value?.absolutePath, draft.finishedRecordingPath)
            .filter { it != draft.existingAudioPath && it != draft.existingSecondaryAudioPath }
            .forEach { try { File(it).delete() } catch (_: Exception) {} }
        playbackController.stop() // prevent playback feedback while capturing a fresh take
        val file = File(getRecordingsDirectory(), "record_${System.currentTimeMillis()}.m4a")
        _activeRecordingFile.value = file
        persistDraft(draft.copy(finishedRecordingPath = file.absolutePath))
        audioRecorder.start(file)
        _recordingState.value = audioRecorder.getRecordingState()
        if (_recordingState.value == RecordingState.RECORDING) {
            startRecordingMeter()
        } else {
            // Startup failed: AudioRecorder removes partial output; forget every reference.
            try { file.delete() } catch (_: Exception) {}
            _activeRecordingFile.value = null
            persistDraft(_editorDraft.value.copy(finishedRecordingPath = null))
            stopRecordingMeter()
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
        val file = _activeRecordingFile.value
        audioRecorder.stop()
        _recordingState.value = RecordingState.IDLE
        stopRecordingMeter()
        if (file == null || !file.isFile || file.length() <= 0L) {
            try { file?.delete() } catch (_: Exception) {}
            _activeRecordingFile.value = null
            persistDraft(_editorDraft.value.copy(finishedRecordingPath = null))
        } else {
            persistDraft(_editorDraft.value.copy(finishedRecordingPath = file.absolutePath))
        }
    }

    /** Discard the finished take: stop playback, delete the file, reset state. */
    fun discardRecording() {
        audioRecorder.stop()
        playbackController.stopPreview()
        _recordingState.value = RecordingState.IDLE
        stopRecordingMeter()
        _activeRecordingFile.value?.let { file ->
            if (file.exists()) viewModelScope.launch(Dispatchers.IO) { file.delete() }
        }
        _activeRecordingFile.value = null
        persistDraft(_editorDraft.value.copy(finishedRecordingPath = null))
    }

    /** Open the add/edit modal pre-filled with an existing entry. */
    fun startEditNaat(naat: NaatEntity) {
        // Editing the entry invalidates its playing session (attachment may change).
        playbackController.stop()
        val current = _editorDraft.value
        if (!current.active || current.editingId != naat.id) {
            if (current.active) {
                audioRecorder.stop()
                stopRecordingMeter()
                DraftFileCleanup.discard(
                    current.existingAudioPath,
                    listOf(_activeRecordingFile.value?.absolutePath,
                        current.finishedRecordingPath, current.newAttachmentPath)
                )
                _activeRecordingFile.value = null
                _recordingState.value = RecordingState.IDLE
            }
            persistDraft(EditorDraft(
                active = true,
                editingId = naat.id,
                title = naat.title,
                poet = naat.poet.orEmpty(),
                category = NaatCategories.normalize(naat.category),
                lyrics = naat.lyrics.orEmpty(),
                existingAudioType = naat.audioType,
                existingAudioPath = naat.audioPath,
                existingSecondaryAudioType = naat.secondaryAudioType,
                existingSecondaryAudioPath = naat.secondaryAudioPath,
                existingFavorite = naat.isFavorite,
                existingCreatedAt = naat.createdAt
            ))
        }
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

    private fun getImportedAudioDirectory(): File {
        val dir = File(context.filesDir, "audio")
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

    /** Transfers a copied file into draft ownership and removes any superseded temporary file. */
    fun adoptLinkedFile(file: File) {
        if (_recordingState.value != RecordingState.IDLE) stopRecording()
        playbackController.stopPreview()
        val draft = _editorDraft.value
        draft.newAttachmentPath?.takeIf {
            it != draft.existingAudioPath && it != draft.existingSecondaryAudioPath &&
                it != file.absolutePath
        }?.let { try { File(it).delete() } catch (_: Exception) {} }
        persistDraft(draft.copy(
            newAttachmentPath = file.absolutePath,
            newAttachmentName = "Attached file: ${file.name}"
        ))
    }

    /** Deletes an app-owned file only when no database entry still references it. */
    fun deleteOrphanFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) { deleteAudioIfUnreferenced(path) }
    }

    private suspend fun deleteAudioIfUnreferenced(path: String) = withContext(Dispatchers.IO) {
        if (repository.countByAudioPath(path) > 0) return@withContext
        val file = File(path).canonicalFile
        val basePath = context.filesDir.canonicalPath + File.separator
        if (file.path.startsWith(basePath) && file.isFile) {
            file.delete()
        }
    }

    // --- CRUD DB Operations ---
    fun saveDraft() {
        if (!saveGate.tryStart()) return
        _isSaving.value = true

        // Finalize first. stopRecording also rejects/deletes an invalid partial take.
        if (_recordingState.value != RecordingState.IDLE) stopRecording() else {
            audioRecorder.stop()
            stopRecordingMeter()
        }
        val draft = _editorDraft.value
        val finishedRecording = draft.finishedRecordingPath?.takeIf {
            File(it).let { file -> file.isFile && file.length() > 0L }
        }
        if (draft.finishedRecordingPath != null && finishedRecording == null) {
            try { File(draft.finishedRecordingPath).delete() } catch (_: Exception) {}
        }

        fun existingPath(type: String): String? = when {
            !draft.existingAudioRemoved && draft.existingAudioType == type -> draft.existingAudioPath
            !draft.existingSecondaryAudioRemoved && draft.existingSecondaryAudioType == type ->
                draft.existingSecondaryAudioPath
            else -> null
        }
        val recordingPath = finishedRecording ?: existingPath("recorded")
        val linkedPath = draft.newAttachmentPath ?: existingPath("local_file")
        val audioType = when {
            recordingPath != null -> "recorded"
            linkedPath != null -> "local_file"
            else -> "none"
        }
        val audioPath = recordingPath ?: linkedPath
        val secondaryAudioType = if (recordingPath != null && linkedPath != null) "local_file" else "none"
        val secondaryAudioPath = if (secondaryAudioType == "local_file") linkedPath else null

        viewModelScope.launch {
            try {
                val saved = if (draft.editingId == null) {
                    val entity = NaatEntity(
                        title = draft.title,
                        poet = draft.poet.takeIf { it.isNotBlank() },
                        category = draft.category,
                        lyrics = draft.lyrics.takeIf { it.isNotBlank() },
                        audioType = audioType,
                        audioPath = audioPath,
                        isFavorite = false,
                        secondaryAudioType = secondaryAudioType,
                        secondaryAudioPath = secondaryAudioPath
                    )
                    val id = repository.insert(entity).toInt()
                    entity.copy(id = id)
                } else {
                    val entity = NaatEntity(
                        id = draft.editingId,
                        title = draft.title,
                        poet = draft.poet.takeIf { it.isNotBlank() },
                        category = draft.category,
                        lyrics = draft.lyrics.takeIf { it.isNotBlank() },
                        audioType = audioType,
                        audioPath = audioPath,
                        isFavorite = draft.existingFavorite,
                        createdAt = draft.existingCreatedAt,
                        secondaryAudioType = secondaryAudioType,
                        secondaryAudioPath = secondaryAudioPath
                    )
                    repository.update(entity)
                    entity
                }

                // Room owns the selected attachment now: clear temporary ownership before closing.
                _activeRecordingFile.value = null
                clearDraft()
                _editingNaat.value = null
                _showAddModal.value = false
                _recordingState.value = RecordingState.IDLE
                val retainedPaths = setOfNotNull(audioPath, secondaryAudioPath)
                setOfNotNull(draft.existingAudioPath, draft.existingSecondaryAudioPath)
                    .filter { it !in retainedPaths }
                    .forEach { oldPath ->
                        try { deleteAudioIfUnreferenced(oldPath) }
                        catch (e: Exception) {
                            Log.w("NaatViewModel", "Saved, but old audio cleanup failed", e)
                        }
                    }
                if (_selectedNaat.value?.id == saved.id) _selectedNaat.value = saved
                _statusMessage.value = if (draft.editingId == null) {
                    "Notebook Entry Saved!"
                } else {
                    "Entry Updated!"
                }
            } catch (e: Exception) {
                Log.e("NaatViewModel", "Save failed", e)
                _statusMessage.value = "Save failed: ${e.localizedMessage ?: "database error"}"
                // Keep the modal and draft files intact so the user can retry.
            } finally {
                _isSaving.value = false
                saveGate.finish()
            }
        }
    }

    fun toggleFavorite(naat: NaatEntity) {
        viewModelScope.launch {
            try {
                val updated = repository.toggleFavorite(naat.id) ?: return@launch
                if (_selectedNaat.value?.id == naat.id) _selectedNaat.value = updated
                if (_editorDraft.value.editingId == naat.id) {
                    persistDraft(_editorDraft.value.copy(existingFavorite = updated.isFavorite))
                }
            } catch (e: Exception) {
                Log.e("NaatViewModel", "Favorite toggle failed", e)
                _statusMessage.value = "Could not update favorite"
            }
        }
    }

    fun deleteNaat(naat: NaatEntity, onSuccess: () -> Unit = {}) {
        if (!deleteGate.tryStart()) return
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                repository.delete(naat)
                if (playbackController.nowPlaying.value?.naatId == naat.id) playbackController.stop()
                try {
                    setOfNotNull(naat.audioPath, naat.secondaryAudioPath)
                        .forEach { deleteAudioIfUnreferenced(it) }
                } catch (e: Exception) {
                    Log.w("NaatViewModel", "Deleted row, but audio cleanup failed", e)
                }
                if (_selectedNaat.value?.id == naat.id) _selectedNaat.value = null
                _statusMessage.value = "Entry deleted"
                onSuccess()
            } catch (e: Exception) {
                Log.e("NaatViewModel", "Delete failed", e)
                _statusMessage.value = "Delete failed: ${e.localizedMessage ?: "database error"}"
            } finally {
                _isDeleting.value = false
                deleteGate.finish()
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
        // PlaybackController is process-scoped; ViewModel teardown must not end
        // a service-owned listening session. A permanently abandoned draft does
        // not retain ownership of temporary files.
        audioRecorder.stop()
        playbackController.stopPreview()
        stopRecordingMeter()
        val draft = _editorDraft.value
        if (draft.active) {
            DraftFileCleanup.discard(
                draft.existingAudioPath,
                listOf(_activeRecordingFile.value?.absolutePath,
                    draft.finishedRecordingPath, draft.newAttachmentPath)
            )
        }
        super.onCleared()
    }
}

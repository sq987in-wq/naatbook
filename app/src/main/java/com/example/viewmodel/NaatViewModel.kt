package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.*
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class NaatViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = NaatDatabase.getDatabase(context)
    private val repository = NaatRepository(database.naatDao())
    private val backupManager = BackupManager(context, repository)

    // Audio players & recorders
    private val audioRecorder = AudioRecorder(context)
    val audioPlayer = AudioPlayer(context)

    // Preferences & Settings Keys
    private val prefs = context.getSharedPreferences("naat_notebook_prefs", Context.MODE_PRIVATE)

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

    // App Preferences
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _globalFontSize = MutableStateFlow(prefs.getFloat("font_size", 18f))
    val globalFontSize: StateFlow<Float> = _globalFontSize.asStateFlow()

    // List of cataloged naats
    val allNaats: StateFlow<List<NaatEntity>> = repository.allNaats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered list based on Search & Folders
    val filteredNaats: StateFlow<List<NaatEntity>> = combine(
        allNaats,
        _searchQuery,
        _selectedFolder
    ) { naats, query, folder ->
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
            matchesFolder && matchesSearch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active recording file state (for adding a new Naat)
    private val _activeRecordingFile = MutableStateFlow<File?>(null)
    val activeRecordingFile: StateFlow<File?> = _activeRecordingFile.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

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
            audioRecorder.stop()
        }
    }

    fun selectNaat(naat: NaatEntity?) {
        _selectedNaat.value = naat
        if (naat == null) {
            audioPlayer.stop()
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
        val timestamp = System.currentTimeMillis()
        val file = File(getRecordingsDirectory(), "record_$timestamp.m4a")
        _activeRecordingFile.value = file
        audioRecorder.start(file)
        _recordingState.value = audioRecorder.getRecordingState()
    }

    fun pauseRecording() {
        audioRecorder.pause()
        _recordingState.value = audioRecorder.getRecordingState()
    }

    fun resumeRecording() {
        audioRecorder.resume()
        _recordingState.value = audioRecorder.getRecordingState()
    }

    fun stopRecording() {
        audioRecorder.stop()
        _recordingState.value = RecordingState.IDLE
    }

    // --- Local Device File Attachment ---
    private fun getLinkedDirectory(): File {
        val dir = File(context.filesDir, "linked")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun copyLocalFileToAppStorage(uri: Uri): File? {
        try {
            val fileName = "linked_${System.currentTimeMillis()}_file.mp3"
            val destFile = File(getLinkedDirectory(), fileName)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val outputStream = FileOutputStream(destFile)
            val buffer = ByteArray(1024 * 4)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()
            outputStream.close()
            return destFile
        } catch (e: Exception) {
            Log.e("NaatViewModel", "Failed to copy local file", e)
            return null
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
        viewModelScope.launch {
            val naat = NaatEntity(
                title = title,
                poet = if (poet.isNullOrBlank()) null else poet,
                category = category,
                lyrics = if (lyrics.isNullOrBlank()) null else lyrics,
                audioType = audioType,
                audioPath = audioPath,
                isFavorite = false
            )
            repository.insert(naat)
            _activeRecordingFile.value = null // reset
            _showAddModal.value = false
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
                audioPlayer.stop()
            }
        }
    }

    // --- Preferences configuration ---
    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun setGlobalFontSize(size: Float) {
        prefs.edit().putFloat("font_size", size).apply()
        _globalFontSize.value = size
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

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        audioRecorder.stop()
    }
}

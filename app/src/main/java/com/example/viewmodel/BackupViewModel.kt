package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * User-controlled backup export / import.
 *
 * Extracted from NaatViewModel: backup is independent of library/editor state.
 * Progress and result messages go through the shared [StatusReporter] so the UI
 * keeps collecting a single status flow.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val statusReporter: StatusReporter
) : ViewModel() {

    fun backupNotebook(uri: Uri) {
        viewModelScope.launch {
            statusReporter.show("Exporting backup, please wait...")
            val result = backupManager.exportBackup(uri)
            result.onSuccess {
                statusReporter.show("Library Backup Exported Successfully!")
            }.onFailure {
                statusReporter.show("Export Failed: ${it.localizedMessage}")
            }
        }
    }

    fun restoreNotebook(uri: Uri) {
        viewModelScope.launch {
            statusReporter.show("Importing backup, please wait...")
            val result = backupManager.importBackup(uri)
            result.onSuccess { count ->
                statusReporter.show("Library Restored Successfully! Loaded $count entries.")
            }.onFailure {
                statusReporter.show("Import Failed: ${it.localizedMessage}")
            }
        }
    }
}

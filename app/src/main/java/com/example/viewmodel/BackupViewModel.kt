package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
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
            statusReporter.show(R.string.status_exporting)
            val result = backupManager.exportBackup(uri)
            result.onSuccess {
                statusReporter.show(R.string.status_export_success)
            }.onFailure {
                statusReporter.show(R.string.status_export_failed, it.localizedMessage ?: StatusMessage.Res(R.string.status_error_database))
            }
        }
    }

    fun restoreNotebook(uri: Uri) {
        viewModelScope.launch {
            statusReporter.show(R.string.status_importing)
            val result = backupManager.importBackup(uri)
            result.onSuccess { count ->
                statusReporter.show(R.string.status_import_success, count)
            }.onFailure {
                statusReporter.show(R.string.status_import_failed, it.localizedMessage ?: StatusMessage.Res(R.string.status_error_database))
            }
        }
    }
}

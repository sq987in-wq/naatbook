package com.example.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide status/toast message bus.
 *
 * Every feature ViewModel reports user-facing status through this single channel,
 * so the UI collects exactly one flow no matter which ViewModel produced the message.
 * NaatViewModel keeps its `statusMessage`/`clearStatusMessage()` API as a thin
 * delegate for backward compatibility with existing screens.
 */
@Singleton
class StatusReporter @Inject constructor() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun show(message: String?) {
        _message.value = message
    }

    fun clear() {
        _message.value = null
    }
}

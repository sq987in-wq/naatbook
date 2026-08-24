package com.example.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide ownership boundary for app-managed audio files.
 *
 * A Room row and its audio file cannot be committed atomically by the filesystem,
 * so every operation that can create, adopt, delete, restore, or orphan-scan audio
 * shares this mutex. Keep operation bodies short but complete: observe ownership,
 * mutate Room/files, then release only after the transition is coherent.
 */
@Singleton
class AudioFileLifecycleCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}

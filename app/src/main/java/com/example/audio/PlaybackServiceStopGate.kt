package com.example.audio

import java.util.concurrent.atomic.AtomicLong

/**
 * Invalidates a delayed service shutdown as soon as a newer entry playback request arrives.
 * Kept separate so the stop/start race is directly testable without Android services.
 */
internal class PlaybackServiceStopGate {
    private val generation = AtomicLong(0L)

    fun schedule(): Long = generation.incrementAndGet()

    fun cancelPending(): Long = generation.incrementAndGet()

    fun isCurrent(token: Long): Boolean = generation.get() == token
}

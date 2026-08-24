package com.example.viewmodel

import java.util.concurrent.atomic.AtomicBoolean

/** Coalesces repeated UI submissions until the active operation reaches finally. */
internal class OperationGate {
    private val running = AtomicBoolean(false)
    fun tryStart(): Boolean = running.compareAndSet(false, true)
    fun finish() { running.set(false) }
}

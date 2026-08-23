package com.example.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CorrectnessGuardsTest {
    @Test
    fun `repeated operation submissions are coalesced until completion`() {
        val gate = OperationGate()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
    }

    @Test
    fun `cancelled recording and linked files are deleted`() {
        val recording = createTempFile(prefix = "recording", suffix = ".m4a")
        val linked = createTempFile(prefix = "linked", suffix = ".mp3")

        DraftFileCleanup.discard(null, listOf(recording.path, linked.path))

        assertFalse(recording.exists())
        assertFalse(linked.exists())
    }

    @Test
    fun `saved existing attachment is never deleted by draft cleanup`() {
        val saved = createTempFile(prefix = "saved", suffix = ".m4a")

        DraftFileCleanup.discard(saved.path, listOf(saved.path, saved.path))

        assertTrue(saved.exists())
        saved.delete()
    }
}

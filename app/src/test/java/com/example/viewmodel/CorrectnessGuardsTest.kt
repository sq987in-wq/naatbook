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

        DraftFileCleanup.discard(emptyList(), listOf(recording.path, linked.path))

        assertFalse(recording.exists())
        assertFalse(linked.exists())
    }

    @Test
    fun `saved primary and secondary attachments are never deleted by draft cleanup`() {
        val primary = createTempFile(prefix = "saved-primary", suffix = ".m4a")
        val secondary = createTempFile(prefix = "saved-secondary", suffix = ".mp3")

        DraftFileCleanup.discard(
            listOf(primary.path, secondary.path),
            listOf(primary.path, secondary.path, primary.path)
        )

        assertTrue(primary.exists())
        assertTrue(secondary.exists())
        primary.delete()
        secondary.delete()
    }
}

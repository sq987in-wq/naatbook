package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupPathGuardTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `accepts entries contained by the extraction directory`() {
        val baseDir = temporaryFolder.newFolder("files")

        val destination = safeBackupDestination(baseDir, "recordings/session..final.mp3")

        assertEquals(
            File(baseDir, "recordings/session..final.mp3").canonicalFile,
            destination?.canonicalFile
        )
    }

    @Test
    fun `rejects zip slip and invalid entry names`() {
        val baseDir = temporaryFolder.newFolder("files")
        val unsafeNames = listOf(
            "",
            "   ",
            ".",
            "../outside.mp3",
            "recordings/../../outside.mp3",
            "recordings/../outside.mp3",
            "/absolute/path.mp3",
            "recordings\\..\\outside.mp3"
        )

        unsafeNames.forEach { entryName ->
            assertNull("Expected rejection for '$entryName'", safeBackupDestination(baseDir, entryName))
        }
    }
}

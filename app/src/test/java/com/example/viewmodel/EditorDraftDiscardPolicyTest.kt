package com.example.viewmodel

import com.example.data.NaatCategories
import com.example.data.NaatEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDraftDiscardPolicyTest {

    private val original = NaatEntity(
        id = 7,
        title = "Saved entry",
        poet = "Saved poet",
        category = NaatCategories.HAMD,
        lyrics = "Saved lyrics",
        audioType = "recorded",
        audioPath = "/files/recordings/saved.m4a",
        isFavorite = false,
        secondaryAudioType = "local_file",
        secondaryAudioPath = "/files/linked/saved.mp3"
    )

    @Test
    fun `fresh untouched add draft can close without discard warning`() {
        assertFalse(EditorDraft(active = true).hasUnsavedChanges(original = null))
    }

    @Test
    fun `new text category or temporary audio requires discard confirmation`() {
        assertTrue(EditorDraft(active = true, title = "Draft").hasUnsavedChanges(original = null))
        assertTrue(
            EditorDraft(active = true, category = NaatCategories.SALAM)
                .hasUnsavedChanges(original = null)
        )
        assertTrue(
            EditorDraft(active = true, finishedRecordingPath = "/files/recordings/take.m4a")
                .hasUnsavedChanges(original = null)
        )
    }

    @Test
    fun `unchanged edit does not warn but modifications do`() {
        val unchanged = EditorDraft(
            active = true,
            editingId = original.id,
            title = original.title,
            poet = original.poet.orEmpty(),
            category = original.category,
            lyrics = original.lyrics.orEmpty(),
            existingAudioType = original.audioType,
            existingAudioPath = original.audioPath,
            existingSecondaryAudioType = original.secondaryAudioType,
            existingSecondaryAudioPath = original.secondaryAudioPath
        )

        assertFalse(unchanged.hasUnsavedChanges(original))
        assertTrue(unchanged.copy(lyrics = "Changed lyrics").hasUnsavedChanges(original))
        assertTrue(unchanged.copy(existingSecondaryAudioRemoved = true).hasUnsavedChanges(original))
        assertTrue(unchanged.copy(newAttachmentPath = "/files/linked/new.mp3").hasUnsavedChanges(original))
    }

    @Test
    fun `unresolved restored edit is treated as dirty to protect user data`() {
        assertTrue(
            EditorDraft(active = true, editingId = original.id, title = original.title)
                .hasUnsavedChanges(original = null)
        )
    }
}

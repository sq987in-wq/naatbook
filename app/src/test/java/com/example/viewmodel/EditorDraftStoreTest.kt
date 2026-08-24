package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.data.NaatCategories
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorDraftStoreTest {
    @Test
    fun `all edit and attachment fields survive handle recreation`() {
        val handle = SavedStateHandle()
        val expected = EditorDraft(
            active = true,
            editingId = 42,
            title = "Draft title",
            poet = "Draft poet",
            category = NaatCategories.MANQABAT,
            lyrics = "A complete draft lyric",
            existingAudioRemoved = true,
            existingAudioType = "recorded",
            existingAudioPath = "/files/audio/existing.m4a",
            existingSecondaryAudioRemoved = true,
            existingSecondaryAudioType = "local_file",
            existingSecondaryAudioPath = "/files/audio/existing.mp3",
            existingFavorite = true,
            existingCreatedAt = 1234L,
            newAttachmentPath = "/files/linked/new.mp3",
            newAttachmentName = "new.mp3",
            finishedRecordingPath = "/files/recordings/take.m4a"
        )

        EditorDraftStore(handle).save(expected)

        // A newly constructed store models the ViewModel being recreated around retained state.
        assertEquals(expected, EditorDraftStore(handle).restore())
    }

    @Test
    fun `empty handle restores a fresh inactive add draft`() {
        assertEquals(EditorDraft(), EditorDraftStore(SavedStateHandle()).restore())
    }
}

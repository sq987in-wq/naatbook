package com.example.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EditorDraftDiskStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `background snapshot round trips and deletes`() {
        val store = EditorDraftDiskStore(context)
        store.delete()
        val expected = EditorDraft(
            active = true,
            editingId = 7,
            title = "Debounced",
            lyrics = "large draft text",
            newAttachmentPath = "/tmp/audio.mp3"
        )

        store.write(expected)
        assertEquals(expected, store.read())
        store.delete()
        assertNull(store.read())
    }
}

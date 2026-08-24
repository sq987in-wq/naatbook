package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.data.NaatCategories

/** Single serialization boundary for the editor's recreation-safe state. */
internal class EditorDraftStore(private val handle: SavedStateHandle) {
    fun restore(): EditorDraft = EditorDraft(
        active = handle[ACTIVE] ?: false,
        editingId = handle[EDITING_ID],
        title = handle[TITLE] ?: "",
        poet = handle[POET] ?: "",
        category = handle[CATEGORY] ?: NaatCategories.DEFAULT,
        lyrics = handle[LYRICS] ?: "",
        existingAudioRemoved = handle[EXISTING_AUDIO_REMOVED] ?: false,
        existingAudioType = handle[EXISTING_AUDIO_TYPE] ?: "none",
        existingAudioPath = handle[EXISTING_AUDIO_PATH],
        existingSecondaryAudioRemoved = handle[EXISTING_SECONDARY_AUDIO_REMOVED] ?: false,
        existingSecondaryAudioType = handle[EXISTING_SECONDARY_AUDIO_TYPE] ?: "none",
        existingSecondaryAudioPath = handle[EXISTING_SECONDARY_AUDIO_PATH],
        existingFavorite = handle[EXISTING_FAVORITE] ?: false,
        existingCreatedAt = handle[EXISTING_CREATED_AT] ?: 0L,
        newAttachmentPath = handle[NEW_ATTACHMENT_PATH],
        newAttachmentName = handle[NEW_ATTACHMENT_NAME],
        finishedRecordingPath = handle[FINISHED_RECORDING_PATH]
    )

    fun save(draft: EditorDraft) {
        handle[ACTIVE] = draft.active
        handle[EDITING_ID] = draft.editingId
        handle[TITLE] = draft.title
        handle[POET] = draft.poet
        handle[CATEGORY] = draft.category
        handle[LYRICS] = draft.lyrics
        handle[EXISTING_AUDIO_REMOVED] = draft.existingAudioRemoved
        handle[EXISTING_AUDIO_TYPE] = draft.existingAudioType
        handle[EXISTING_AUDIO_PATH] = draft.existingAudioPath
        handle[EXISTING_SECONDARY_AUDIO_REMOVED] = draft.existingSecondaryAudioRemoved
        handle[EXISTING_SECONDARY_AUDIO_TYPE] = draft.existingSecondaryAudioType
        handle[EXISTING_SECONDARY_AUDIO_PATH] = draft.existingSecondaryAudioPath
        handle[EXISTING_FAVORITE] = draft.existingFavorite
        handle[EXISTING_CREATED_AT] = draft.existingCreatedAt
        handle[NEW_ATTACHMENT_PATH] = draft.newAttachmentPath
        handle[NEW_ATTACHMENT_NAME] = draft.newAttachmentName
        handle[FINISHED_RECORDING_PATH] = draft.finishedRecordingPath
    }

    private companion object {
        const val ACTIVE = "draft.active"
        const val EDITING_ID = "draft.editingId"
        const val TITLE = "draft.title"
        const val POET = "draft.poet"
        const val CATEGORY = "draft.category"
        const val LYRICS = "draft.lyrics"
        const val EXISTING_AUDIO_REMOVED = "draft.existingAudioRemoved"
        const val EXISTING_AUDIO_TYPE = "draft.existingAudioType"
        const val EXISTING_AUDIO_PATH = "draft.existingAudioPath"
        const val EXISTING_SECONDARY_AUDIO_REMOVED = "draft.existingSecondaryAudioRemoved"
        const val EXISTING_SECONDARY_AUDIO_TYPE = "draft.existingSecondaryAudioType"
        const val EXISTING_SECONDARY_AUDIO_PATH = "draft.existingSecondaryAudioPath"
        const val EXISTING_FAVORITE = "draft.existingFavorite"
        const val EXISTING_CREATED_AT = "draft.existingCreatedAt"
        const val NEW_ATTACHMENT_PATH = "draft.newAttachmentPath"
        const val NEW_ATTACHMENT_NAME = "draft.newAttachmentName"
        const val FINISHED_RECORDING_PATH = "draft.finishedRecordingPath"
    }
}

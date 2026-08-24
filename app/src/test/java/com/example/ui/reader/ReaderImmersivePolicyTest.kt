package com.example.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImmersivePolicyTest {

    @Test
    fun hidingChromeOffsetsScrollByTheRemovedTopPadding() {
        assertEquals(
            -184f,
            ReaderImmersivePolicy.scrollCompensationPx(
                fromTopChromePx = 184,
                toTopChromePx = 0
            ),
            0f
        )
    }

    @Test
    fun restoringChromeOffsetsScrollByTheAddedTopPadding() {
        assertEquals(
            184f,
            ReaderImmersivePolicy.scrollCompensationPx(
                fromTopChromePx = 0,
                toTopChromePx = 184
            ),
            0f
        )
    }

    @Test
    fun onlyCleanShortSingleFingerTapTogglesChrome() {
        assertTrue(
            ReaderImmersivePolicy.isIntentionalSingleTap(
                travelPx = 3f,
                touchSlopPx = 12f,
                pointerCount = 1,
                durationMs = 120L
            )
        )
        assertFalse(
            ReaderImmersivePolicy.isIntentionalSingleTap(
                travelPx = 13f,
                touchSlopPx = 12f,
                pointerCount = 1,
                durationMs = 120L
            )
        )
        assertFalse(
            ReaderImmersivePolicy.isIntentionalSingleTap(
                travelPx = 0f,
                touchSlopPx = 12f,
                pointerCount = 2,
                durationMs = 120L
            )
        )
        assertFalse(
            ReaderImmersivePolicy.isIntentionalSingleTap(
                travelPx = 0f,
                touchSlopPx = 12f,
                pointerCount = 1,
                durationMs = ReaderImmersivePolicy.MAX_SINGLE_TAP_DURATION_MS + 1L
            )
        )
    }
}

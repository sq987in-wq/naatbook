package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeResourceSafetyTest {
    @Test
    fun `release runs when native stop throws`() {
        var released = false

        runCatching {
            NativeResourceSafety.stopAndRelease(
                stop = { error("broken native stop") },
                release = { released = true }
            )
        }

        assertTrue(released)
    }

    @Test
    fun `normal stop releases exactly once`() {
        var releases = 0
        NativeResourceSafety.stopAndRelease(stop = {}, release = { releases++ })
        assertEquals(1, releases)
    }
}

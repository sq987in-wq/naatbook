package com.example.audio

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3TerminalPolicyTest {
    @Test
    fun `natural completion is terminal`() {
        assertTrue(Media3TerminalPolicy.shouldStop(Player.STATE_ENDED, 1, false))
    }

    @Test
    fun `external stop with a retained item is terminal`() {
        assertTrue(Media3TerminalPolicy.shouldStop(Player.STATE_IDLE, 1, false))
    }

    @Test
    fun `internal clear does not recursively stop and ready or buffering remain active`() {
        assertFalse(Media3TerminalPolicy.shouldStop(Player.STATE_IDLE, 1, true))
        assertFalse(Media3TerminalPolicy.shouldStop(Player.STATE_IDLE, 0, false))
        assertFalse(Media3TerminalPolicy.shouldStop(Player.STATE_READY, 1, false))
        assertFalse(Media3TerminalPolicy.shouldStop(Player.STATE_BUFFERING, 1, false))
    }
}

package com.example.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceStopGateTest {
    @Test
    fun `new playback invalidates a previously scheduled service shutdown`() {
        val gate = PlaybackServiceStopGate()
        val oldStop = gate.schedule()

        gate.cancelPending()

        assertFalse(gate.isCurrent(oldStop))
    }

    @Test
    fun `latest scheduled shutdown remains valid until cancelled`() {
        val gate = PlaybackServiceStopGate()
        val latestStop = gate.schedule()

        assertTrue(gate.isCurrent(latestStop))
    }
}

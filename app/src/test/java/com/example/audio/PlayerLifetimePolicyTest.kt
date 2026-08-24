package com.example.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLifetimePolicyTest {
    @Test
    fun `unexpected active entry service teardown idles singleton player`() {
        assertTrue(
            PlayerLifetimePolicy.shouldEnsureIdleOnServiceDestroy(
                engineIdle = false,
                previewActive = false
            )
        )
    }

    @Test
    fun `notification free editor preview survives service teardown`() {
        assertFalse(
            PlayerLifetimePolicy.shouldEnsureIdleOnServiceDestroy(
                engineIdle = false,
                previewActive = true
            )
        )
    }

    @Test
    fun `already idle engine needs no teardown work`() {
        assertFalse(
            PlayerLifetimePolicy.shouldEnsureIdleOnServiceDestroy(
                engineIdle = true,
                previewActive = false
            )
        )
    }
}

package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRequestRegistryTest {
    @Test
    fun `request token is private and consumable only once`() {
        val registry = PlaybackRequestRegistry()
        val request = PlaybackRequest(
            path = "/data/user/0/example/files/recordings/take.m4a",
            naatId = 42,
            title = "Protected request",
            artist = "Poet"
        )

        val token = registry.register(request)

        assertEquals(request, registry.consume(token))
        assertNull(registry.consume(token))
        assertNull(registry.consume("not-a-valid-token"))
    }
}

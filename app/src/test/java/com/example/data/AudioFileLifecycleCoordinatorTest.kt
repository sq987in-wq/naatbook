package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AudioFileLifecycleCoordinatorTest {
    @Test
    fun `exclusive operations never overlap`() = runBlocking {
        val coordinator = AudioFileLifecycleCoordinator()
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)

        List(12) {
            launch(Dispatchers.Default) {
                coordinator.exclusive {
                    val now = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, now) }
                    delay(5)
                    active.decrementAndGet()
                }
            }
        }.forEach { it.join() }

        assertEquals(1, peak.get())
        assertEquals(0, active.get())
    }
}

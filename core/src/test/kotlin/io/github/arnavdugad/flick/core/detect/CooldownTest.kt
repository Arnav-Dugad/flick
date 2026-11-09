package io.github.arnavdugad.flick.core.detect

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooldownTest {

    @Test
    fun `first fire is always allowed`() {
        assertTrue(Cooldown(500).tryFire(nowMs = 0))
    }

    @Test
    fun `blocks until duration has elapsed`() {
        val cooldown = Cooldown(500)
        assertTrue(cooldown.tryFire(1_000))
        assertFalse(cooldown.tryFire(1_200))
        assertFalse(cooldown.tryFire(1_499))
        assertTrue(cooldown.tryFire(1_500))
    }

    @Test
    fun `blocked attempts do not extend the cooldown`() {
        val cooldown = Cooldown(500)
        cooldown.tryFire(0)
        cooldown.tryFire(400) // blocked
        assertTrue(cooldown.tryFire(500))
    }

    @Test
    fun `reset allows immediate fire`() {
        val cooldown = Cooldown(10_000)
        cooldown.tryFire(0)
        cooldown.reset()
        assertTrue(cooldown.tryFire(1))
    }

    @Test
    fun `duration can be changed at runtime`() {
        val cooldown = Cooldown(10_000)
        cooldown.tryFire(0)
        cooldown.durationMs = 100
        assertTrue(cooldown.isReady(100))
    }
}

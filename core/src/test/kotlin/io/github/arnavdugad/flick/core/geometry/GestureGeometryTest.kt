package io.github.arnavdugad.flick.core.geometry

import io.github.arnavdugad.flick.core.model.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GestureGeometryTest {

    private val w = 1080
    private val h = 2400

    @Test
    fun `swipe up moves finger from lower to upper half`() {
        val path = GestureGeometry.swipe(Direction.UP, 0.5f, w, h)
        assertEquals(540f, path.start.x)
        assertEquals(540f, path.end.x)
        assertEquals(1800f, path.start.y)
        assertEquals(600f, path.end.y)
    }

    @Test
    fun `swipe down is the mirror of swipe up`() {
        val up = GestureGeometry.swipe(Direction.UP, 0.5f, w, h)
        val down = GestureGeometry.swipe(Direction.DOWN, 0.5f, w, h)
        assertEquals(up.start, down.end)
        assertEquals(up.end, down.start)
    }

    @Test
    fun `horizontal swipes use width`() {
        val left = GestureGeometry.swipe(Direction.LEFT, 0.5f, w, h)
        assertEquals(810f, left.start.x)
        assertEquals(270f, left.end.x)
        assertEquals(1200f, left.start.y)
        val right = GestureGeometry.swipe(Direction.RIGHT, 0.5f, w, h)
        assertEquals(270f, right.start.x)
        assertEquals(810f, right.end.x)
    }

    @Test
    fun `swipe never enters the edge margin`() {
        for (direction in Direction.entries) {
            val path = GestureGeometry.swipe(direction, 5f, w, h)
            for (p in listOf(path.start, path.end)) {
                assertTrue(p.x >= w * GestureGeometry.EDGE_MARGIN - 0.01f, "x=${p.x} for $direction")
                assertTrue(p.x <= w * (1 - GestureGeometry.EDGE_MARGIN) + 0.01f, "x=${p.x} for $direction")
                assertTrue(p.y >= h * GestureGeometry.EDGE_MARGIN - 0.01f, "y=${p.y} for $direction")
                assertTrue(p.y <= h * (1 - GestureGeometry.EDGE_MARGIN) + 0.01f, "y=${p.y} for $direction")
            }
        }
    }

    @Test
    fun `tiny or negative distance is clamped to a usable swipe`() {
        val path = GestureGeometry.swipe(Direction.UP, -1f, w, h)
        assertTrue(path.start.y - path.end.y > 0f)
    }

    @Test
    fun `point is clamped inside the screen`() {
        assertEquals(ScreenPoint(1f, 1f), GestureGeometry.point(-0.5f, 0f, w, h))
        assertEquals(ScreenPoint(w - 1f, h - 1f), GestureGeometry.point(2f, 1f, w, h))
        assertEquals(ScreenPoint(540f, 1200f), GestureGeometry.point(0.5f, 0.5f, w, h))
    }

    @Test
    fun `invalid screen size is rejected`() {
        assertFailsWith<IllegalArgumentException> { GestureGeometry.swipe(Direction.UP, 0.5f, 0, h) }
        assertFailsWith<IllegalArgumentException> { GestureGeometry.point(0.5f, 0.5f, w, -1) }
    }
}

package io.github.arnavdugad.flick.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val samples: List<Action> = listOf(
        Action.Swipe(Direction.UP),
        Action.Swipe(Direction.LEFT, distance = 0.3f, durationMs = 200),
        Action.Scroll(Direction.DOWN),
        Action.Tap(0.9f, 0.5f),
        Action.DoubleTap(),
        Action.LongPress(durationMs = 900),
        Action.Global(GlobalAction.SCREENSHOT),
        Action.Media(MediaCommand.NEXT),
    )

    @Test
    fun `every action round-trips through JSON`() {
        for (action in samples) {
            val encoded = json.encodeToString(Action.serializer(), action)
            assertEquals(action, json.decodeFromString(Action.serializer(), encoded), encoded)
        }
    }

    @Test
    fun `json uses stable type names`() {
        val encoded = json.encodeToString(Action.serializer(), Action.Swipe(Direction.UP))
        assertTrue(encoded.contains("\"type\":\"swipe\""), encoded)
    }

    @Test
    fun `direction opposite is symmetric`() {
        for (d in Direction.entries) assertEquals(d, d.opposite.opposite)
    }
}

package io.github.arnavdugad.flick.core.profiles

import io.github.arnavdugad.flick.core.model.Actions
import io.github.arnavdugad.flick.core.model.TriggerRequirement
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileResolverTest {

    private val defaults = ProfileTemplates.defaults()

    @Test
    fun `instagram uses the reels profile`() {
        assertEquals("reels", ProfileResolver.resolve("com.instagram.android", defaults)?.id)
    }

    @Test
    fun `unknown app falls back to everywhere`() {
        assertEquals(ProfileTemplates.EVERYWHERE_ID, ProfileResolver.resolve("com.example.notes", defaults)?.id)
    }

    @Test
    fun `disabled profiles are skipped`() {
        // YouTube's profile is off by default.
        assertEquals(ProfileTemplates.EVERYWHERE_ID, ProfileResolver.resolve("com.google.android.youtube", defaults)?.id)
    }

    @Test
    fun `suppressed packages get no profile at all`() {
        assertNull(ProfileResolver.resolve("io.github.arnavdugad.flick", defaults, suppressed = setOf("io.github.arnavdugad.flick")))
    }

    @Test
    fun `no everywhere profile means nothing outside listed apps`() {
        val onlyApps = defaults.filter { it.kind == ProfileKind.APPS }
        assertNull(ProfileResolver.resolve("com.example.notes", onlyApps))
    }

    @Test
    fun `everywhere profile never uses camera or microphone`() {
        val everywhere = Profile(
            id = "e",
            name = "E",
            kind = ProfileKind.EVERYWHERE,
            bindings = mapOf(
                TriggerType.FACE_SMILE to Actions.Like,
                TriggerType.VOICE_NEXT to Actions.Next,
                TriggerType.BACK_DOUBLE_KNOCK to Actions.Back,
            ),
        )
        assertEquals(setOf(TriggerType.BACK_DOUBLE_KNOCK), everywhere.effectiveBindings.keys)
        assertFalse(everywhere.allows(TriggerType.HAND_FIST))
    }

    @Test
    fun `practice set is the union of enabled profiles`() {
        val all = ProfileResolver.allTriggers(defaults)
        assertTrue(TriggerType.FACE_SMILE in all) // reels
        assertTrue(TriggerType.PHONE_TILT_RIGHT in all) // books
        assertFalse(TriggerType.FACE_MOUTH_OPEN in all) // youtube only, disabled
    }
}

class ForegroundTrackerTest {

    @Test
    fun `transient overlays keep the app underneath`() {
        val t = ForegroundTracker(setOf("com.android.systemui", "com.google.android.inputmethod.latin"))
        assertTrue(t.onWindow("com.instagram.android"))
        assertFalse(t.onWindow("com.android.systemui"))
        assertFalse(t.onWindow("com.google.android.inputmethod.latin"))
        assertEquals("com.instagram.android", t.current)
        assertTrue(t.onWindow("com.android.chrome"))
        assertFalse(t.onWindow("com.android.chrome"))
    }

    @Test
    fun `null and empty packages are ignored`() {
        val t = ForegroundTracker(emptySet())
        assertFalse(t.onWindow(null))
        assertFalse(t.onWindow(""))
        assertNull(t.current)
    }
}

class ProfileTemplatesTest {

    @Test
    fun `defaults have unique ids and exactly one everywhere profile`() {
        val d = ProfileTemplates.defaults()
        assertEquals(d.size, d.map { it.id }.toSet().size)
        assertEquals(1, d.count { it.kind == ProfileKind.EVERYWHERE })
    }

    @Test
    fun `no package belongs to two default profiles`() {
        val all = ProfileTemplates.defaults().flatMap { it.packages }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `everywhere default contains no private sensors`() {
        val e = ProfileTemplates.defaults().single { it.kind == ProfileKind.EVERYWHERE }
        assertTrue(e.bindings.keys.none { it.requirement in setOf(TriggerRequirement.CAMERA, TriggerRequirement.MICROPHONE) })
    }

    @Test
    fun `profiles round-trip through JSON`() {
        val json = Json { ignoreUnknownKeys = true }
        val s = ListSerializer(Profile.serializer())
        val d = ProfileTemplates.defaults()
        assertEquals(d, json.decodeFromString(s, json.encodeToString(s, d)))
    }
}

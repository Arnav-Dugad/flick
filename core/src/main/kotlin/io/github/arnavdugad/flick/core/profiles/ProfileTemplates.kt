package io.github.arnavdugad.flick.core.profiles

import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.Actions
import io.github.arnavdugad.flick.core.model.TriggerType

/** A starting point for a profile. [defaultEnabled] decides whether it is on out of the box. */
data class ProfileTemplate(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val bindings: Map<TriggerType, Action>,
    val defaultEnabled: Boolean,
) {
    fun toProfile(id: String = this.id, kind: ProfileKind = ProfileKind.APPS) = Profile(
        id = id,
        name = name,
        kind = kind,
        packages = packages,
        enabled = defaultEnabled,
        bindings = bindings,
    )
}

/**
 * Smart defaults for popular apps. Swipe/tap geometry is relative to the screen, so these keep
 * working when apps redesign; users can edit everything.
 */
object ProfileTemplates {

    const val EVERYWHERE_ID = "everywhere"

    private val pageForward = Action.Tap(x = 0.9f, y = 0.5f)
    private val pageBack = Action.Tap(x = 0.1f, y = 0.5f)

    val REELS = ProfileTemplate(
        id = "reels",
        name = "Reels & Shorts",
        packages = setOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.facebook.katana",
            "com.snapchat.android",
        ),
        bindings = mapOf(
            TriggerType.HEAD_TILT_DOWN to Actions.Next,
            TriggerType.HEAD_TILT_UP to Actions.Previous,
            TriggerType.FACE_SMILE to Actions.Like,
            TriggerType.FACE_LONG_BLINK to Actions.TapCenter,
            TriggerType.HAND_OPEN_PALM to Actions.Next,
        ),
        defaultEnabled = true,
    )

    val YOUTUBE = ProfileTemplate(
        id = "youtube",
        name = "YouTube",
        packages = setOf("com.google.android.youtube"),
        bindings = mapOf(
            TriggerType.HEAD_TILT_DOWN to Actions.Next,
            TriggerType.HEAD_TILT_UP to Actions.Previous,
            TriggerType.FACE_MOUTH_OPEN to Actions.TapCenter,
            TriggerType.FACE_EYEBROW_RAISE to Actions.Back,
            TriggerType.HAND_OPEN_PALM to Actions.Next,
        ),
        defaultEnabled = false,
    )

    val READING = ProfileTemplate(
        id = "reading",
        name = "Browsing & reading",
        packages = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
        ),
        bindings = mapOf(
            TriggerType.HEAD_TILT_DOWN to Actions.ScrollDown,
            TriggerType.HEAD_TILT_UP to Actions.ScrollUp,
            TriggerType.FACE_EYEBROW_RAISE to Actions.Back,
        ),
        defaultEnabled = false,
    )

    val BOOKS = ProfileTemplate(
        id = "books",
        name = "Books & PDFs",
        packages = setOf(
            "com.amazon.kindle",
            "com.google.android.apps.books",
            "com.adobe.reader",
            "com.flyersoft.moonreader",
            "org.readera",
            "com.foobnix.pdf.reader",
        ),
        bindings = mapOf(
            TriggerType.PHONE_TILT_RIGHT to pageForward,
            TriggerType.PHONE_TILT_LEFT to pageBack,
            TriggerType.HEAD_TURN_RIGHT to pageForward,
            TriggerType.HEAD_TURN_LEFT to pageBack,
        ),
        defaultEnabled = true,
    )

    /** Hands may be messy while cooking: palm and fist work from a distance, no touching. */
    val COOKING = ProfileTemplate(
        id = "cooking",
        name = "Cooking",
        packages = emptySet(),
        bindings = mapOf(
            TriggerType.HAND_OPEN_PALM to Actions.ScrollDown,
            TriggerType.HAND_FIST to Actions.ScrollUp,
            TriggerType.HAND_THUMBS_UP to Actions.Next,
        ),
        defaultEnabled = true,
    )

    val BLANK = ProfileTemplate(
        id = "blank",
        name = "New profile",
        packages = emptySet(),
        bindings = emptyMap(),
        defaultEnabled = true,
    )

    val EVERYWHERE = ProfileTemplate(
        id = EVERYWHERE_ID,
        name = "Everywhere else",
        packages = emptySet(),
        bindings = emptyMap(),
        defaultEnabled = true,
    )

    /** Templates offered when creating a profile. */
    val creatable = listOf(BLANK, REELS, YOUTUBE, READING, BOOKS, COOKING)

    /** The profiles a fresh install starts with. */
    fun defaults(): List<Profile> = listOf(
        REELS.toProfile(),
        YOUTUBE.toProfile(),
        READING.toProfile(),
        BOOKS.toProfile(),
        EVERYWHERE.toProfile(kind = ProfileKind.EVERYWHERE),
    )
}

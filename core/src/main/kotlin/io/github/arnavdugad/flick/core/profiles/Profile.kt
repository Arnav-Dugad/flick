package io.github.arnavdugad.flick.core.profiles

import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.TriggerRequirement
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlinx.serialization.Serializable

@Serializable
enum class ProfileKind {
    /** Applies while one of [Profile.packages] is in the foreground. */
    APPS,

    /** Applies in any app without its own profile. Never uses the camera or microphone. */
    EVERYWHERE,
}

/** A set of gesture → action bindings that applies in specific apps. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val kind: ProfileKind = ProfileKind.APPS,
    val packages: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val bindings: Map<TriggerType, Action> = emptyMap(),
) {
    /**
     * Whether this profile may use [trigger]. The camera and microphone only ever run inside apps
     * the user picked, so the catch-all profile can't use them.
     */
    fun allows(trigger: TriggerType): Boolean =
        kind == ProfileKind.APPS || trigger.requirement !in PRIVATE_SENSORS

    /** Bindings this profile can actually use. */
    val effectiveBindings: Map<TriggerType, Action>
        get() = if (kind == ProfileKind.APPS) bindings else bindings.filterKeys(::allows)

    companion object {
        val PRIVATE_SENSORS = setOf(TriggerRequirement.CAMERA, TriggerRequirement.MICROPHONE)
    }
}

/** Picks the profile for the app in the foreground. */
object ProfileResolver {

    /**
     * @param foregroundPackage the app in front, or null if unknown.
     * @param suppressed packages where Flick must do nothing at all (e.g. Flick itself).
     */
    fun resolve(
        foregroundPackage: String?,
        profiles: List<Profile>,
        suppressed: Set<String> = emptySet(),
    ): Profile? {
        if (foregroundPackage != null && foregroundPackage in suppressed) return null
        if (foregroundPackage != null) {
            profiles.firstOrNull { it.enabled && it.kind == ProfileKind.APPS && foregroundPackage in it.packages }
                ?.let { return it }
        }
        return profiles.firstOrNull { it.enabled && it.kind == ProfileKind.EVERYWHERE }
    }

    /** Every trigger used by any enabled profile (for practice mode). */
    fun allTriggers(profiles: List<Profile>): Set<TriggerType> =
        profiles.filter { it.enabled }.flatMapTo(HashSet()) { it.effectiveBindings.keys }
}

/**
 * Tracks which app is really in the foreground. System overlays (notification shade, keyboards,
 * system dialogs) don't count as switching apps: the app underneath stays current.
 */
class ForegroundTracker(private val transient: Set<String>) {

    var current: String? = null
        private set

    /** Returns true when the foreground app changed. */
    fun onWindow(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty() || packageName in transient) return false
        if (packageName == current) return false
        current = packageName
        return true
    }
}

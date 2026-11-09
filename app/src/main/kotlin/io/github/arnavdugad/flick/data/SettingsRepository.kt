package io.github.arnavdugad.flick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.arnavdugad.flick.core.autoscroll.AutoScrollSettings
import io.github.arnavdugad.flick.core.macro.Macro
import io.github.arnavdugad.flick.core.mindful.MindfulSettings
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.TriggerSpecs
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.core.profiles.Profile
import io.github.arnavdugad.flick.core.profiles.ProfileKind
import io.github.arnavdugad.flick.core.profiles.ProfileTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class FlickSettings(
    val masterEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val haptics: Boolean = true,
    /** Per-app profiles, including the catch-all "Everywhere else" profile. */
    val profiles: List<Profile> = ProfileTemplates.defaults(),
    /** Only triggers the user has adjusted are stored; the rest use [TriggerSpecs] defaults. */
    val tuning: Map<TriggerType, TriggerTuning> = emptyMap(),
    /** Holding a volume key that is bound as a gesture still changes the volume. */
    val volumeHoldAdjusts: Boolean = true,
    /** A soft glow around the screen edge when a gesture fires. */
    val edgeGlow: Boolean = true,
    /** A small badge while the camera is in use; tapping it pauses Flick. */
    val privacyBadge: Boolean = true,
    /** Show tracking dots in the privacy badge. */
    val trackingPreview: Boolean = false,
    /** Floating mini-controller over other apps. */
    val bubble: Boolean = false,
    /** Ongoing notification with pause / auto-scroll controls. */
    val statusNotification: Boolean = false,
    val autoScroll: AutoScrollSettings = AutoScrollSettings(),
    val mindful: MindfulSettings = MindfulSettings(),
    val macros: List<Macro> = emptyList(),
) {
    fun tuningFor(type: TriggerType): TriggerTuning = TriggerSpecs.tuningFor(type, tuning)

    fun profile(id: String): Profile? = profiles.firstOrNull { it.id == id }

    fun macro(id: String): Macro? = macros.firstOrNull { it.id == id }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.settingsStore
    private val json = Json { ignoreUnknownKeys = true }
    private val bindingsSerializer = MapSerializer(TriggerType.serializer(), Action.serializer())
    private val profilesSerializer = ListSerializer(Profile.serializer())
    private val macrosSerializer = ListSerializer(Macro.serializer())
    private val tuningSerializer = MapSerializer(TriggerType.serializer(), TriggerTuning.serializer())

    private object Keys {
        val masterEnabled = booleanPreferencesKey("master_enabled")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val amoledBlack = booleanPreferencesKey("amoled_black")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val haptics = booleanPreferencesKey("haptics")
        val profiles = stringPreferencesKey("profiles")

        /** M2 global bindings; migrated into the Reels & Shorts profile on first read. */
        val legacyBindings = stringPreferencesKey("bindings")
        val tuning = stringPreferencesKey("tuning")
        val volumeHoldAdjusts = booleanPreferencesKey("volume_hold_adjusts")
        val edgeGlow = booleanPreferencesKey("edge_glow")
        val privacyBadge = booleanPreferencesKey("privacy_badge")
        val trackingPreview = booleanPreferencesKey("tracking_preview")
        val bubble = booleanPreferencesKey("bubble")
        val statusNotification = booleanPreferencesKey("status_notification")
        val autoScroll = stringPreferencesKey("auto_scroll")
        val mindful = stringPreferencesKey("mindful")
        val macros = stringPreferencesKey("macros")

        /** M1 single binding; migrated into [bindings] on first read. */
        val legacyWaveAction = stringPreferencesKey("wave_action")
    }

    val settings: Flow<FlickSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = FlickSettings()
            FlickSettings(
                masterEnabled = p[Keys.masterEnabled] ?: defaults.masterEnabled,
                onboardingComplete = p[Keys.onboardingComplete] ?: defaults.onboardingComplete,
                themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: defaults.themeMode,
                amoledBlack = p[Keys.amoledBlack] ?: defaults.amoledBlack,
                dynamicColor = p[Keys.dynamicColor] ?: defaults.dynamicColor,
                haptics = p[Keys.haptics] ?: defaults.haptics,
                profiles = readProfiles(p),
                tuning = p[Keys.tuning]?.let { decode(it) { s -> json.decodeFromString(tuningSerializer, s) } }
                    ?: defaults.tuning,
                volumeHoldAdjusts = p[Keys.volumeHoldAdjusts] ?: defaults.volumeHoldAdjusts,
                edgeGlow = p[Keys.edgeGlow] ?: defaults.edgeGlow,
                privacyBadge = p[Keys.privacyBadge] ?: defaults.privacyBadge,
                trackingPreview = p[Keys.trackingPreview] ?: defaults.trackingPreview,
                bubble = p[Keys.bubble] ?: defaults.bubble,
                statusNotification = p[Keys.statusNotification] ?: defaults.statusNotification,
                autoScroll = p[Keys.autoScroll]?.let { decode(it) { s -> json.decodeFromString(AutoScrollSettings.serializer(), s) } }
                    ?: defaults.autoScroll,
                mindful = p[Keys.mindful]?.let { decode(it) { s -> json.decodeFromString(MindfulSettings.serializer(), s) } }
                    ?: defaults.mindful,
                macros = p[Keys.macros]?.let { decode(it) { s -> json.decodeFromString(macrosSerializer, s) } }
                    ?: defaults.macros,
            )
        }

    private fun readProfiles(p: Preferences): List<Profile> {
        p[Keys.profiles]?.let { raw ->
            decode(raw) { json.decodeFromString(profilesSerializer, it) }?.let { return it }
        }
        // First run on M3: start from the defaults and keep any gestures set up in M1/M2.
        val legacy = p[Keys.legacyBindings]?.let { raw -> decode(raw) { json.decodeFromString(bindingsSerializer, it) } }
            ?: p[Keys.legacyWaveAction]?.let { raw ->
                decode(raw) { json.decodeFromString(Action.serializer(), it) }
            }?.let { mapOf(TriggerType.PROXIMITY_WAVE to it) }
            ?: emptyMap()
        return ProfileTemplates.defaults().map {
            if (it.id == ProfileTemplates.REELS.id) it.copy(bindings = it.bindings + legacy) else it
        }
    }

    private suspend fun editProfiles(transform: (List<Profile>) -> List<Profile>) = store.edit { p ->
        val updated = transform(readProfiles(p))
        p[Keys.profiles] = json.encodeToString(profilesSerializer, updated)
        p.remove(Keys.legacyBindings)
        p.remove(Keys.legacyWaveAction)
    }

    suspend fun setMasterEnabled(value: Boolean) = store.edit { it[Keys.masterEnabled] = value }
    suspend fun setOnboardingComplete(value: Boolean) = store.edit { it[Keys.onboardingComplete] = value }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.themeMode] = value.name }
    suspend fun setAmoledBlack(value: Boolean) = store.edit { it[Keys.amoledBlack] = value }
    suspend fun setDynamicColor(value: Boolean) = store.edit { it[Keys.dynamicColor] = value }
    suspend fun setHaptics(value: Boolean) = store.edit { it[Keys.haptics] = value }
    suspend fun setVolumeHoldAdjusts(value: Boolean) = store.edit { it[Keys.volumeHoldAdjusts] = value }
    suspend fun setEdgeGlow(value: Boolean) = store.edit { it[Keys.edgeGlow] = value }
    suspend fun setPrivacyBadge(value: Boolean) = store.edit { it[Keys.privacyBadge] = value }
    suspend fun setTrackingPreview(value: Boolean) = store.edit { it[Keys.trackingPreview] = value }
    suspend fun setBubble(value: Boolean) = store.edit { it[Keys.bubble] = value }
    suspend fun setStatusNotification(value: Boolean) = store.edit { it[Keys.statusNotification] = value }
    suspend fun setAutoScroll(value: AutoScrollSettings) =
        store.edit { it[Keys.autoScroll] = json.encodeToString(AutoScrollSettings.serializer(), value.sanitized()) }
    suspend fun setMindful(value: MindfulSettings) =
        store.edit { it[Keys.mindful] = json.encodeToString(MindfulSettings.serializer(), value) }

    private fun readMacros(p: Preferences): List<Macro> =
        p[Keys.macros]?.let { decode(it) { s -> json.decodeFromString(macrosSerializer, s) } }.orEmpty()

    /** Inserts or replaces a macro (matched by id). */
    suspend fun upsertMacro(macro: Macro) = store.edit { p ->
        val list = readMacros(p)
        val updated = if (list.any { it.id == macro.id }) list.map { if (it.id == macro.id) macro else it } else list + macro
        p[Keys.macros] = json.encodeToString(macrosSerializer, updated)
    }

    /** Deletes a macro and removes it from every profile that used it. */
    suspend fun deleteMacro(id: String) {
        store.edit { p -> p[Keys.macros] = json.encodeToString(macrosSerializer, readMacros(p).filterNot { it.id == id }) }
        editProfiles { list ->
            list.map { prof ->
                prof.copy(bindings = prof.bindings.filterValues { !(it is Action.RunMacro && it.macroId == id) })
            }
        }
    }

    /** Inserts or replaces a profile (matched by id). */
    suspend fun upsertProfile(profile: Profile) = editProfiles { list ->
        if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it } else list + profile
    }

    suspend fun deleteProfile(id: String) = editProfiles { list ->
        list.filterNot { it.id == id && it.kind == ProfileKind.APPS }
    }

    suspend fun setProfileEnabled(id: String, enabled: Boolean) = editProfiles { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    /** Binds [trigger] to [action] in profile [id], or removes the binding when [action] is null. */
    suspend fun setBinding(id: String, trigger: TriggerType, action: Action?) = editProfiles { list ->
        list.map { prof ->
            if (prof.id != id) return@map prof
            prof.copy(bindings = if (action == null) prof.bindings - trigger else prof.bindings + (trigger to action))
        }
    }

    /**
     * Sets the apps of profile [id], removing them from any other app profile first so an app never
     * belongs to two profiles.
     */
    suspend fun setProfilePackages(id: String, packages: Set<String>) = editProfiles { list ->
        list.map { prof ->
            when {
                prof.id == id -> prof.copy(packages = packages)
                prof.kind == ProfileKind.APPS -> prof.copy(packages = prof.packages - packages)
                else -> prof
            }
        }
    }

    /** Stores tuning for [trigger]; null restores the defaults. */
    suspend fun setTuning(trigger: TriggerType, tuning: TriggerTuning?) = store.edit { p ->
        val current = p[Keys.tuning]?.let { decode(it) { s -> json.decodeFromString(tuningSerializer, s) } }
            ?: emptyMap()
        val updated = if (tuning == null) current - trigger else current + (trigger to tuning)
        p[Keys.tuning] = json.encodeToString(tuningSerializer, updated)
    }

    private inline fun <T> decode(raw: String, block: (String) -> T): T? = runCatching { block(raw) }.getOrNull()
}

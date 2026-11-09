package io.github.arnavdugad.flick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.arnavdugad.flick.core.model.Action
import io.github.arnavdugad.flick.core.model.TriggerSpecs
import io.github.arnavdugad.flick.core.model.TriggerTuning
import io.github.arnavdugad.flick.core.model.TriggerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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
    /** Global trigger → action bindings. Profiles (M3) layer per-app bindings on top. */
    val bindings: Map<TriggerType, Action> = DEFAULT_BINDINGS,
    /** Only triggers the user has adjusted are stored; the rest use [TriggerSpecs] defaults. */
    val tuning: Map<TriggerType, TriggerTuning> = emptyMap(),
    /** Holding a volume key that is bound as a gesture still changes the volume. */
    val volumeHoldAdjusts: Boolean = true,
) {
    fun tuningFor(type: TriggerType): TriggerTuning = TriggerSpecs.tuningFor(type, tuning)

    companion object {
        /** Nothing is bound until the user picks a first gesture in onboarding. */
        val DEFAULT_BINDINGS: Map<TriggerType, Action> = emptyMap()
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.settingsStore
    private val json = Json { ignoreUnknownKeys = true }
    private val bindingsSerializer = MapSerializer(TriggerType.serializer(), Action.serializer())
    private val tuningSerializer = MapSerializer(TriggerType.serializer(), TriggerTuning.serializer())

    private object Keys {
        val masterEnabled = booleanPreferencesKey("master_enabled")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val amoledBlack = booleanPreferencesKey("amoled_black")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val haptics = booleanPreferencesKey("haptics")
        val bindings = stringPreferencesKey("bindings")
        val tuning = stringPreferencesKey("tuning")
        val volumeHoldAdjusts = booleanPreferencesKey("volume_hold_adjusts")

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
                bindings = readBindings(p) ?: defaults.bindings,
                tuning = p[Keys.tuning]?.let { decode(it) { s -> json.decodeFromString(tuningSerializer, s) } }
                    ?: defaults.tuning,
                volumeHoldAdjusts = p[Keys.volumeHoldAdjusts] ?: defaults.volumeHoldAdjusts,
            )
        }

    private fun readBindings(p: Preferences): Map<TriggerType, Action>? {
        p[Keys.bindings]?.let { raw ->
            return decode(raw) { json.decodeFromString(bindingsSerializer, it) }
        }
        val legacy = p[Keys.legacyWaveAction]?.let { raw ->
            decode(raw) { json.decodeFromString(Action.serializer(), it) }
        } ?: return null
        return mapOf(TriggerType.PROXIMITY_WAVE to legacy)
    }

    suspend fun setMasterEnabled(value: Boolean) = store.edit { it[Keys.masterEnabled] = value }
    suspend fun setOnboardingComplete(value: Boolean) = store.edit { it[Keys.onboardingComplete] = value }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.themeMode] = value.name }
    suspend fun setAmoledBlack(value: Boolean) = store.edit { it[Keys.amoledBlack] = value }
    suspend fun setDynamicColor(value: Boolean) = store.edit { it[Keys.dynamicColor] = value }
    suspend fun setHaptics(value: Boolean) = store.edit { it[Keys.haptics] = value }
    suspend fun setVolumeHoldAdjusts(value: Boolean) = store.edit { it[Keys.volumeHoldAdjusts] = value }

    /** Binds [trigger] to [action], or removes the binding when [action] is null. */
    suspend fun setBinding(trigger: TriggerType, action: Action?) = store.edit { p ->
        val current = readBindings(p) ?: FlickSettings.DEFAULT_BINDINGS
        val updated = if (action == null) current - trigger else current + (trigger to action)
        p[Keys.bindings] = json.encodeToString(bindingsSerializer, updated)
        p.remove(Keys.legacyWaveAction)
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

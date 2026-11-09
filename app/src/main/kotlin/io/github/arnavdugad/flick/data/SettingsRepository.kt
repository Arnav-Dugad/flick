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
import io.github.arnavdugad.flick.core.model.Actions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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
    /** M1: the single global binding for the proximity wave. Replaced by profiles in M3. */
    val waveAction: Action = Actions.Next,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.settingsStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val masterEnabled = booleanPreferencesKey("master_enabled")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val amoledBlack = booleanPreferencesKey("amoled_black")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val haptics = booleanPreferencesKey("haptics")
        val waveAction = stringPreferencesKey("wave_action")
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
                waveAction = p[Keys.waveAction]?.let(::decodeAction) ?: defaults.waveAction,
            )
        }

    suspend fun setMasterEnabled(value: Boolean) = store.edit { it[Keys.masterEnabled] = value }
    suspend fun setOnboardingComplete(value: Boolean) = store.edit { it[Keys.onboardingComplete] = value }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.themeMode] = value.name }
    suspend fun setAmoledBlack(value: Boolean) = store.edit { it[Keys.amoledBlack] = value }
    suspend fun setDynamicColor(value: Boolean) = store.edit { it[Keys.dynamicColor] = value }
    suspend fun setHaptics(value: Boolean) = store.edit { it[Keys.haptics] = value }
    suspend fun setWaveAction(value: Action) =
        store.edit { it[Keys.waveAction] = json.encodeToString(Action.serializer(), value) }

    private fun decodeAction(raw: String): Action? =
        runCatching { json.decodeFromString(Action.serializer(), raw) }.getOrNull()
}

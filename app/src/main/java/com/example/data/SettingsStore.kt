package com.example.data

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * App settings backed by Preferences DataStore.
 *
 * Replaces the legacy "naat_notebook_prefs" SharedPreferences file; existing
 * installs keep their values through a one-time SharedPreferencesMigration
 * (the old file is then deleted). Reads are cold Flows, so the ViewModel no
 * longer blocks the main thread on disk during startup.
 */

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_FONT_SIZE = floatPreferencesKey("font_size")

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "naat_notebook_prefs"))
    }
)

class SettingsStore(private val context: Context) {

    val themeMode: Flow<String> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_THEME_MODE] ?: "system" }

    val fontSize: Flow<Float> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_FONT_SIZE] ?: NaatViewModelDefaults.DEFAULT_FONT_SIZE }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setFontSize(size: Float) {
        context.settingsDataStore.edit { it[KEY_FONT_SIZE] = size }
    }
}

/** Shared preference defaults (kept here so ViewModel and store can never drift). */
object NaatViewModelDefaults {
    const val DEFAULT_THEME_MODE = "system"
    const val DEFAULT_FONT_SIZE = 18f
}

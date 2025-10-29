package com.rapo.haloai.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object PreferencesKeys {
        val THEME = stringPreferencesKey("theme")
        val HARDWARE_ACCELERATION = booleanPreferencesKey("hardware_acceleration")
        val MEMORY_MODE = stringPreferencesKey("memory_mode")
    }

    val theme: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.THEME] ?: "system"
        }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit {
            it[PreferencesKeys.THEME] = theme
        }
    }

    val hardwareAcceleration: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HARDWARE_ACCELERATION] ?: true
        }

    suspend fun setHardwareAcceleration(enabled: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.HARDWARE_ACCELERATION] = enabled
        }
    }

    val memoryMode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MEMORY_MODE] ?: "normal"
        }

    suspend fun setMemoryMode(mode: String) {
        context.dataStore.edit {
            it[PreferencesKeys.MEMORY_MODE] = mode
        }
    }
}
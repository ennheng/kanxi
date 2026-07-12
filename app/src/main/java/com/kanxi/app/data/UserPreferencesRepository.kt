package com.kanxi.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.kanxiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kanxi_preferences",
)

enum class TextSizeLevel(val storageValue: String, val scale: Float) {
    STANDARD("standard", 1.0f),
    LARGE("large", 1.15f),
    EXTRA_LARGE("extra_large", 1.3f),
    ;

    companion object {
        fun fromStorage(value: String?): TextSizeLevel =
            entries.firstOrNull { it.storageValue == value } ?: LARGE
    }
}

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.kanxiPreferencesDataStore)

    private val safePreferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val textSizeLevel: Flow<TextSizeLevel> = safePreferences.map { preferences ->
        TextSizeLevel.fromStorage(preferences[Keys.TEXT_SIZE_LEVEL])
    }

    val hasShownMobileDataWarning: Flow<Boolean> = safePreferences.map { preferences ->
        preferences[Keys.HAS_SHOWN_MOBILE_DATA_WARNING] ?: false
    }

    suspend fun setTextSizeLevel(level: TextSizeLevel) {
        dataStore.edit { it[Keys.TEXT_SIZE_LEVEL] = level.storageValue }
    }

    suspend fun markMobileDataWarningShown() {
        dataStore.edit { it[Keys.HAS_SHOWN_MOBILE_DATA_WARNING] = true }
    }

    suspend fun resetMobileDataWarning() {
        dataStore.edit { it[Keys.HAS_SHOWN_MOBILE_DATA_WARNING] = false }
    }

    private object Keys {
        val TEXT_SIZE_LEVEL = stringPreferencesKey("text_size_level")
        val HAS_SHOWN_MOBILE_DATA_WARNING = booleanPreferencesKey(
            "has_shown_mobile_data_warning",
        )
    }
}

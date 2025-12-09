package com.mosu.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    
    companion object {
        private val CLIENT_ID_KEY = stringPreferencesKey("osu_client_id")
        private val CLIENT_SECRET_KEY = stringPreferencesKey("osu_client_secret")
    }

    val clientId: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CLIENT_ID_KEY] ?: ""
        }
    
    val clientSecret: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CLIENT_SECRET_KEY] ?: ""
        }

    suspend fun saveCredentials(clientId: String, clientSecret: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[CLIENT_ID_KEY] = clientId
            preferences[CLIENT_SECRET_KEY] = clientSecret
        }
    }

    suspend fun clearCredentials() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(CLIENT_ID_KEY)
            preferences.remove(CLIENT_SECRET_KEY)
        }
    }
}


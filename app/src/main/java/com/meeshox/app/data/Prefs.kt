package com.meeshox.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("meeshox_prefs")

data class UserPrefs(
    val userId: Long = 0,
    val personalization: Boolean = true,
    val darkTheme: Boolean = false,
    val language: String = "English",
    val orderNotifications: Boolean = true,
    val dealNotifications: Boolean = true,
    val favouriteBrands: String = "",
    val preferredSize: String = "",
    val budget: String = "",
    val gridLayout: Boolean = true,
    val consentGiven: Boolean = false
)

class Prefs(private val context: Context) {
    private object K {
        val userId = longPreferencesKey("user_id")
        val personalization = booleanPreferencesKey("personalization")
        val dark = booleanPreferencesKey("dark")
        val language = stringPreferencesKey("language")
        val orderNotif = booleanPreferencesKey("order_notif")
        val dealNotif = booleanPreferencesKey("deal_notif")
        val brands = stringPreferencesKey("fav_brands")
        val size = stringPreferencesKey("pref_size")
        val budget = stringPreferencesKey("budget")
        val grid = booleanPreferencesKey("grid")
        val consent = booleanPreferencesKey("consent")
    }

    val flow: Flow<UserPrefs> = context.dataStore.data.map { p: Preferences ->
        UserPrefs(
            userId = p[K.userId] ?: 0,
            personalization = p[K.personalization] ?: true,
            darkTheme = p[K.dark] ?: false,
            language = p[K.language] ?: "English",
            orderNotifications = p[K.orderNotif] ?: true,
            dealNotifications = p[K.dealNotif] ?: true,
            favouriteBrands = p[K.brands] ?: "",
            preferredSize = p[K.size] ?: "",
            budget = p[K.budget] ?: "",
            gridLayout = p[K.grid] ?: true,
            consentGiven = p[K.consent] ?: false
        )
    }

    suspend fun current(): UserPrefs = flow.first()

    suspend fun setUser(id: Long) = context.dataStore.edit { it[K.userId] = id }
    suspend fun setPersonalization(v: Boolean) = context.dataStore.edit { it[K.personalization] = v }
    suspend fun setDark(v: Boolean) = context.dataStore.edit { it[K.dark] = v }
    suspend fun setLanguage(v: String) = context.dataStore.edit { it[K.language] = v }
    suspend fun setOrderNotif(v: Boolean) = context.dataStore.edit { it[K.orderNotif] = v }
    suspend fun setDealNotif(v: Boolean) = context.dataStore.edit { it[K.dealNotif] = v }
    suspend fun setGrid(v: Boolean) = context.dataStore.edit { it[K.grid] = v }
    suspend fun setConsent(v: Boolean) = context.dataStore.edit { it[K.consent] = v }
    suspend fun setMemory(brands: String, size: String, budget: String) = context.dataStore.edit {
        it[K.brands] = brands; it[K.size] = size; it[K.budget] = budget
    }
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}

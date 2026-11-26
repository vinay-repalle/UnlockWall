package io.github.vinayrepalle.unlockwall.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.vinayrepalle.unlockwall.core.Slot
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * @property enabled the main switch.
 * @property showing the slot last set successfully; null when unknown.
 */
data class Settings(val enabled: Boolean = false, val showing: Slot? = null)

/** Which pictures exist is not stored here: the files in the wallpaper cache are the truth. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data
        // An unreadable file means "off", the harmless state.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            Settings(
                enabled = prefs[ENABLED] ?: false,
                showing = Slot.entries.find { it.name == prefs[SHOWING] },
            )
        }

    suspend fun current(): Settings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }

    /** Set to null before changing the wallpaper, so a crash midway leaves "unknown". */
    suspend fun setShowing(slot: Slot?) {
        store.edit { if (slot == null) it.remove(SHOWING) else it[SHOWING] = slot.name }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val SHOWING = stringPreferencesKey("showing")
    }
}

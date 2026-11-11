package ai.pleos.app.safetywindow.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ProductSettingsRepository.kt
 * Pleos Safety Window - Work Order #41
 * Dedicated product preferences repository backed by SharedPreferences.
 * Provides atomic persistence across application lifecycles and reboots.
 */
class ProductSettingsRepository(
    private val prefs: SharedPreferences
) {
    constructor(context: Context, prefsName: String = PREFS_NAME) : this(
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    )

    companion object {
        const val PREFS_NAME = "pleos_product_settings"
        private const val KEY_USER_ENABLED = "user_enabled"
        private const val KEY_VOICE_MUTED = "voice_muted"
        private const val KEY_SETTINGS_VERSION = "settings_version"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_SOURCE = "source"
        private const val CURRENT_VERSION = 1
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<ProductSettings> = _settingsFlow.asStateFlow()

    private fun getCurrentKstTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.KOREA)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return sdf.format(Date())
    }

    private fun loadSettings(): ProductSettings {
        val enabled = prefs.getBoolean(KEY_USER_ENABLED, true)
        val muted = prefs.getBoolean(KEY_VOICE_MUTED, false)
        val version = prefs.getInt(KEY_SETTINGS_VERSION, CURRENT_VERSION)
        val updated = prefs.getString(KEY_UPDATED_AT, getCurrentKstTimestamp()) ?: getCurrentKstTimestamp()
        val sourceStr = prefs.getString(KEY_SOURCE, SettingsSource.DEFAULT.name) ?: SettingsSource.DEFAULT.name
        val source = try {
            SettingsSource.valueOf(sourceStr)
        } catch (e: Exception) {
            SettingsSource.DEFAULT
        }
        return ProductSettings(
            userEnabled = enabled,
            voiceMuted = muted,
            settingsVersion = version,
            updatedAt = updated,
            source = source
        )
    }

    fun getSettings(): ProductSettings {
        return _settingsFlow.value
    }

    fun setUserEnabled(enabled: Boolean, source: SettingsSource = SettingsSource.USER): ProductSettings {
        val timestamp = getCurrentKstTimestamp()
        val updated = _settingsFlow.value.copy(
            userEnabled = enabled,
            updatedAt = timestamp,
            source = source
        )
        prefs.edit()
            .putBoolean(KEY_USER_ENABLED, enabled)
            .putInt(KEY_SETTINGS_VERSION, CURRENT_VERSION)
            .putString(KEY_UPDATED_AT, timestamp)
            .putString(KEY_SOURCE, source.name)
            .commit()
        _settingsFlow.value = updated
        return updated
    }

    fun setVoiceMuted(muted: Boolean, source: SettingsSource = SettingsSource.USER): ProductSettings {
        val timestamp = getCurrentKstTimestamp()
        val updated = _settingsFlow.value.copy(
            voiceMuted = muted,
            updatedAt = timestamp,
            source = source
        )
        prefs.edit()
            .putBoolean(KEY_VOICE_MUTED, muted)
            .putInt(KEY_SETTINGS_VERSION, CURRENT_VERSION)
            .putString(KEY_UPDATED_AT, timestamp)
            .putString(KEY_SOURCE, source.name)
            .commit()
        _settingsFlow.value = updated
        return updated
    }

    fun resetToDefault(): ProductSettings {
        val timestamp = getCurrentKstTimestamp()
        val defaultSettings = ProductSettings(
            userEnabled = true,
            voiceMuted = false,
            settingsVersion = CURRENT_VERSION,
            updatedAt = timestamp,
            source = SettingsSource.DEFAULT
        )
        prefs.edit()
            .putBoolean(KEY_USER_ENABLED, true)
            .putBoolean(KEY_VOICE_MUTED, false)
            .putInt(KEY_SETTINGS_VERSION, CURRENT_VERSION)
            .putString(KEY_UPDATED_AT, timestamp)
            .putString(KEY_SOURCE, SettingsSource.DEFAULT.name)
            .commit()
        _settingsFlow.value = defaultSettings
        return defaultSettings
    }
}

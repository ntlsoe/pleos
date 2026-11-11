package ai.pleos.app.safetywindow.core.settings

/**
 * ProductSettingsModel.kt
 * Pleos Safety Window - Work Order #41
 * Immutable DTO representing user-configurable product settings.
 * Isolated from Debug Test Console state objects.
 */

enum class SettingsSource {
    DEFAULT,
    USER,
    MIGRATION
}

data class ProductSettings(
    val userEnabled: Boolean = true,
    val voiceMuted: Boolean = false,
    val settingsVersion: Int = 1,
    val updatedAt: String = "2026-11-10T07:36:00+09:00",
    val source: SettingsSource = SettingsSource.DEFAULT
) {
    companion object {
        val DEFAULT = ProductSettings()
    }
}

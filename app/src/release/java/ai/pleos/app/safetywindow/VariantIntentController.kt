package ai.pleos.app.safetywindow

import android.content.Intent

internal class VariantIntentController(
    activity: MainActivity,
    viewModel: ai.pleos.app.safetywindow.ui.hud.SafetyViewModel,
    sensorRepository: ai.pleos.app.safetywindow.data.sensor.VehicleSensorRepository,
    onDemoModeChanged: (Boolean) -> Unit
) {
    fun register() = Unit
    fun unregister() = Unit
    fun handleExternalIntent(intent: Intent?) = Unit
}

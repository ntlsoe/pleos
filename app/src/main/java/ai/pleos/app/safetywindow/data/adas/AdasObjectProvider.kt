package ai.pleos.app.safetywindow.data.adas

import ai.pleos.playground.adas.TrackedObject
import kotlinx.coroutines.flow.StateFlow

/**
 * AdasObjectProvider
 *
 * ADAS SDK TrackedObject(PEDESTRIAN, TWO_WHEELER) 규격을 제공하는 추상화 인터페이스.
 */
interface AdasObjectProvider {
    val detectedObjects: StateFlow<List<TrackedObject>>
    fun start()
    fun stop()
}

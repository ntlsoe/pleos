package ai.pleos.app.safetywindow.demo.policy

import ai.pleos.app.safetywindow.demo.model.DemoDirection
import ai.pleos.app.safetywindow.demo.model.DemoExecutionMode
import ai.pleos.app.safetywindow.demo.model.DemoLevel

data class DemoFeatureFlags(
    val enableDemoL1: Boolean = true,
    val enableDemoL2: Boolean = true,
    val enableDemoL3: Boolean = true,
    val enableDemoTtsL2: Boolean = true,
    val enableDemoTtsL3: Boolean = true,
    val enableRealInputOutput: Boolean = false,
    val enableReleaseExperimentalOutput: Boolean = false
)

data class StabilityPolicy(
    val l1EnterCount: Int = 2,
    val l2EnterCount: Int = 3,
    val l3EnterCount: Int = 3,
    val exitCount: Int = 2,
    val minimumVisibleMillis: Long = 900L,
    val cooldownMillis: Long = 1600L,
    val parameterClass: String = "EXPERIMENTAL_DEMO_ONLY"
)

object DemoVisualSpec {
    const val GREEN: Long = 0xFF22C55EL
    const val YELLOW: Long = 0xFFFACC15L
    const val ORANGE: Long = 0xFFF97316L
    const val RED: Long = 0xFFDC2626L
    const val NEUTRAL: Long = 0xFF64748BL

    fun color(level: DemoLevel): Long = when (level) {
        DemoLevel.L1 -> YELLOW
        DemoLevel.L2 -> ORANGE
        DemoLevel.L3 -> RED
        DemoLevel.L0, DemoLevel.NONE_DATA_INVALID -> NEUTRAL
    }

    fun message(level: DemoLevel, direction: DemoDirection): String = when (level) {
        DemoLevel.L0 -> "시야 안내 대기"
        DemoLevel.L1 -> when (direction) {
            DemoDirection.LEFT -> "좌측 시야 확인"
            DemoDirection.RIGHT -> "우측 시야 확인"
            DemoDirection.BOTH -> "좌우 시야 확인"
            else -> "주변 시야 확인"
        }
        DemoLevel.L2 -> when (direction) {
            DemoDirection.LEFT -> "좌측 가림 구간 주의"
            DemoDirection.RIGHT -> "우측 가림 구간 주의"
            else -> "교차 구간 시야 확인"
        }
        DemoLevel.L3 -> when (direction) {
            DemoDirection.LEFT -> "좌측 가림 구간 임박\n즉시 좌측 시야 확인"
            DemoDirection.RIGHT -> "우측 가림 구간 임박\n즉시 우측 시야 확인"
            else -> "교차 구간 임박\n즉시 주변 시야 확인"
        }
        DemoLevel.NONE_DATA_INVALID -> "공간정보 확인 필요"
    }

    fun tts(level: DemoLevel, direction: DemoDirection): String? = when (level) {
        DemoLevel.L2 -> when (direction) {
            DemoDirection.LEFT -> "좌측 시야를 확인하세요."
            DemoDirection.RIGHT -> "우측 시야를 확인하세요."
            else -> "좌우 시야를 확인하세요."
        }
        DemoLevel.L3 -> when (direction) {
            DemoDirection.LEFT -> "좌측 가림 구간입니다. 좌측 시야를 확인하세요."
            DemoDirection.RIGHT -> "우측 가림 구간입니다. 우측 시야를 확인하세요."
            else -> "가림 구간입니다. 주변 시야를 확인하세요."
        }
        else -> null
    }
}

object DemoOutputGate {
    fun mayRender(mode: DemoExecutionMode, isRelease: Boolean, flags: DemoFeatureFlags = DemoFeatureFlags()): Boolean =
        mode == DemoExecutionMode.DEMO_INTEGRATION && !isRelease && !flags.enableRealInputOutput

    fun maySpeak(mode: DemoExecutionMode, isRelease: Boolean, level: DemoLevel, flags: DemoFeatureFlags = DemoFeatureFlags()): Boolean =
        mayRender(mode, isRelease, flags) && when (level) {
            DemoLevel.L2 -> flags.enableDemoTtsL2
            DemoLevel.L3 -> flags.enableDemoTtsL3
            else -> false
        }
}

object DemoL3MeaningGuard {
    private val FORBIDDEN_WORDS = listOf(
        "보행자 감지",
        "어린이 감지",
        "충돌 임박",
        "자동 제동",
        "긴급 제동",
        "AEB",
        "출발 가능"
    )

    fun validateMessageMeaning(message: String): Boolean {
        for (word in FORBIDDEN_WORDS) {
            if (message.contains(word)) {
                return false
            }
        }
        return true
    }
}

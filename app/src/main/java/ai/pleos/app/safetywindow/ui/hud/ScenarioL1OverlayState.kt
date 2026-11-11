package ai.pleos.app.safetywindow.ui.hud

enum class AdvisorySide {
    LEFT,
    RIGHT,
    BOTH,
    UNCERTAIN
}

/**
 * Isolated UI overlay state for Scenario-only L1 visual advisory (Section 6.3 & Section 7).
 * Never mutates the existing live L0-L3 state machine.
 */
sealed interface ScenarioL1OverlayState {
    data object Hidden : ScenarioL1OverlayState

    data class Visible(
        val message: String,
        val side: AdvisorySide,
        val reasonCode: String
    ) : ScenarioL1OverlayState {
        init {
            for (forbidden in FORBIDDEN_TERMS) {
                require(!message.contains(forbidden)) {
                    "Forbidden term '$forbidden' is not permitted in ScenarioL1OverlayState.Visible message: $message"
                }
            }
            require(message in ALLOWED_MESSAGES) {
                "ScenarioL1OverlayState.Visible message must be one of $ALLOWED_MESSAGES, got: $message"
            }
        }
    }

    companion object {
        const val MSG_RIGHT_CHECK = "우측 시야 확인"
        const val MSG_LEFT_CHECK = "좌측 시야 확인"
        const val MSG_BOTH_CHECK = "좌우 시야 확인"
        const val MSG_SURROUND_CHECK = "주변 시야 확인"

        val ALLOWED_MESSAGES = setOf(
            MSG_RIGHT_CHECK,
            MSG_LEFT_CHECK,
            MSG_BOTH_CHECK,
            MSG_SURROUND_CHECK
        )

        val FORBIDDEN_TERMS = listOf(
            "위험 감지",
            "보행자 감지",
            "안전",
            "출발 가능",
            "보행자",
            "어린이",
            "킥보드",
            "위험 객체",
            "감지됨",
            "준수",
            "일시정지 완료",
            "충돌 위험 확정"
        )

        val FORBIDDEN_PHRASES = FORBIDDEN_TERMS
    }
}

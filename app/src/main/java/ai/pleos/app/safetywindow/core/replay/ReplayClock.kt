package ai.pleos.app.safetywindow.core.replay

/**
 * ReplayClock
 * [작업명령 #8] 섹션 7: 시스템 벽시계(wall clock)와 완전 격리된 가상 시계
 *
 * 지원 배속: 0.5x, 1.0x, 2.0x
 * - 배속 변경 시에도 이벤트의 논리적 순서 불변
 * - minimumVisibleMillis, cooldownMillis 판정 시 이 가상 시계의 경과 시간을 사용
 * - 일시정지 및 재개 시 시간 점프(time jump) 없음
 */
class ReplayClock(
    var speedMultiplier: Double = 1.0
) {
    var currentReplayTimeMillis: Long = 0L
        private set

    var isPaused: Boolean = false
        private set

    var totalPausedDurationMillis: Long = 0L
        private set

    init {
        require(speedMultiplier in listOf(0.5, 1.0, 2.0)) {
            "지원 배속은 0.5x, 1.0x, 2.0x만 허용됩니다: $speedMultiplier"
        }
    }

    fun setSpeed(multiplier: Double) {
        require(multiplier in listOf(0.5, 1.0, 2.0)) {
            "지원 배속은 0.5x, 1.0x, 2.0x만 허용됩니다: $multiplier"
        }
        this.speedMultiplier = multiplier
    }

    fun advanceTo(targetRelativeMillis: Long) {
        if (isPaused) return
        require(targetRelativeMillis >= currentReplayTimeMillis) {
            "가상 시계는 역행할 수 없습니다. 현재: $currentReplayTimeMillis, 목표: $targetRelativeMillis"
        }
        currentReplayTimeMillis = targetRelativeMillis
    }

    fun advanceDelta(deltaMillis: Long) {
        if (isPaused) return
        require(deltaMillis >= 0) { "경과 시간은 음수일 수 없습니다: $deltaMillis" }
        // 가상 시간은 배속에 비례하여 진행
        currentReplayTimeMillis += (deltaMillis * speedMultiplier).toLong()
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun reset() {
        currentReplayTimeMillis = 0L
        isPaused = false
        totalPausedDurationMillis = 0L
    }

    /**
     * 특정 지속시간을 현재 배속에 맞추어 가상 경과 시간으로 변환
     */
    fun scaledDuration(nominalDurationMillis: Long): Long {
        return (nominalDurationMillis / speedMultiplier).toLong()
    }
}

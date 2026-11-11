package ai.pleos.app.safetywindow.ui.hud

import ai.pleos.app.safetywindow.core.model.ExecutionMode
import ai.pleos.app.safetywindow.data.sensor.TurnSignalState
import ai.pleos.playground.adas.ObjectType
import ai.pleos.playground.adas.TrackedObject
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * SafetyWindowVectorHud
 *
 * [주행 UX 기준선 준수 벡터 HUD]
 * 1. AAOS UX_RESTRICTIONS_NO_VIDEO 표준 준수 (점멸, 페이드, 자동 이동 애니메이션 전면 배제)
 * 2. 모든 상태 전이는 정적 요소의 즉시 교체(Static Instant Replacement)로만 수행
 * 3. 명암비 4.5:1 이상 고대비 텍스트 (핵심 텍스트 28sp 이상, 보조 텍스트 12sp 이상)
 * 4. 시나리오 재생 시 작고 정적인 [시나리오 재생] 배지 상시 표출
 * 5. 금지 문구(준수/완료/출발가능/감지) 전면 배제 및 중립적 안전 확인 문구 표출
 */
@Composable
fun SafetyWindowVectorHud(
    uiState: SafetyHudUiState,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(uiState.advisoryDecision?.evaluatedAt, uiState.isGeometryFixtureActive) {
        if (uiState.isGeometryFixtureActive) {
            val decision = uiState.advisoryDecision
            Log.i(
                "Work48HudFrame",
                "timestamp=${decision?.evaluatedAt} level=${uiState.alertLevel} visualRequested=${decision?.visualAllowed} composed=true dialogOpen=${uiState.isSettingsPanelOpen} ttsRequested=${decision?.ttsAllowed} ttsActuallySpoken=false"
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D14)) // 차량용 초저반사 딥블랙 배경
    ) {
        // 1. 좌측 16dp 디지털 필러 엣지 (경고 시 정적 단색 표출, 점멸 없음)
        if (uiState.leftPillarAlertActive) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(16.dp)
                    .align(Alignment.CenterStart)
                    .background(Color(0xFFD50000))
            )
        }

        // 2. 우측 16dp 디지털 필러 엣지 (경고 시 정적 단색 표출, 점멸 없음)
        if (uiState.rightPillarAlertActive) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(16.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color(0xFFD50000))
            )
        }

        // 3. 중앙 및 좌/우 A필러 부채꼴 벡터 캔버스 (정적 렌더링)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // 좌측 A필러 부채꼴 사각지대 (정적 렌더링)
            drawStaticBlindSpotSector(
                origin = Offset(0f, canvasH * 0.75f),
                startAngle = -65f,
                sweepAngle = 45f,
                radius = canvasW * 0.28f,
                isAlert = uiState.leftPillarAlertActive
            )

            // 우측 A필러 부채꼴 사각지대 (정적 렌더링)
            drawStaticBlindSpotSector(
                origin = Offset(canvasW, canvasH * 0.75f),
                startAngle = 200f,
                sweepAngle = 45f,
                radius = canvasW * 0.28f,
                isAlert = uiState.rightPillarAlertActive
            )

            // 가상 미래 확장 객체 마커는 PARKED_VALIDATION 모드에서만 정적으로 표시
            if (uiState.alertLevel == SafetyAlertLevel.PARKED_VALIDATION) {
                uiState.demoVirtualObstacles.forEach { obj ->
                    val isRight = obj.azimuthDegrees > 0
                    drawDemoObjectMarker(
                        origin = if (isRight) Offset(canvasW, canvasH * 0.75f) else Offset(0f, canvasH * 0.75f),
                        obj = obj,
                        baseAngle = if (isRight) 222f else -42f,
                        radius = canvasW * 0.20f
                    )
                }
            }
        }

        // 4. 상단 정적 대시보드
        TopStaticDashboard(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        )

        // 4.2. 시나리오 전용 L1 정적 오버레이 배너 (Section 8: SCENARIO_INPUT에서만 제한적 표시, 애니메이션 없음)
        val l1State = uiState.scenarioL1OverlayState
        if (uiState.executionMode == ExecutionMode.SCENARIO_INPUT && l1State is ScenarioL1OverlayState.Visible) {
            ScenarioL1StaticBanner(
                visibleState = l1State,
                isGeometryFixtureActive = uiState.isGeometryFixtureActive,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 74.dp)
            )
        }

        // 4.5. 정차 중 검증 / 시나리오 L1 통합 진단 오버레이
        if (uiState.occlusionValidation != null) {
            ParkedValidationOverlay(
                metrics = uiState.occlusionValidation,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 48.dp, start = 48.dp, end = 48.dp)
            )
        }

        // 5. 하단 정적 배지 및 안내 문구 패널
        BottomStaticPanel(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        )

        // 6. 시나리오 재생 표시 배지 (정적 텍스트, 점멸 없음)
        if (uiState.isScenarioActive || uiState.executionMode != ExecutionMode.REAL_INPUT) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp)
                    .background(Color(0xFFE65100), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (uiState.isGeometryFixtureActive) "합성 입력 · 제품 아님 · 동작 검증 전용" else "시나리오 재생",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScenarioL1StaticBanner(
    visibleState: ScenarioL1OverlayState.Visible,
    isGeometryFixtureActive: Boolean,
    modifier: Modifier = Modifier
) {
    val staticIcon = when (visibleState.side) {
        AdvisorySide.RIGHT -> "▶"
        AdvisorySide.LEFT -> "◀"
        AdvisorySide.BOTH -> "◀ ▶"
        AdvisorySide.UNCERTAIN -> "◆"
    }
    Row(
        modifier = modifier
            .background(Color(0xFF1A2332), RoundedCornerShape(8.dp))
            .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = staticIcon,
            color = Color(0xFFFFD54F),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = visibleState.message,
            color = Color(0xFFFFF8E1), // 명암비 > 11:1 (>= 4.5:1 기준 충족)
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .background(Color(0xFFE65100), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isGeometryFixtureActive) "합성 입력 · 제품 아님 · 동작 검증 전용" else "시나리오 재생",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStaticBlindSpotSector(
    origin: Offset,
    startAngle: Float,
    sweepAngle: Float,
    radius: Float,
    isAlert: Boolean
) {
    val sectorFillColor = if (isAlert) Color(0x33D50000) else Color(0x1A00E5FF)
    val strokeColor = if (isAlert) Color(0xFFFF1744) else Color(0xFF00E5FF)

    val topLeft = Offset(origin.x - radius, origin.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    drawArc(
        color = sectorFillColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = true,
        topLeft = topLeft,
        size = arcSize
    )

    drawArc(
        color = strokeColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = true,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = if (isAlert) 3.5f else 1.8f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDemoObjectMarker(
    origin: Offset,
    obj: TrackedObject,
    baseAngle: Float,
    radius: Float
) {
    val angleRad = Math.toRadians(baseAngle.toDouble())
    val markerX = origin.x + (radius * cos(angleRad)).toFloat()
    val markerY = origin.y + (radius * sin(angleRad)).toFloat()

    val markerColor = when (obj.type) {
        ObjectType.TWO_WHEELER -> Color(0xFFFF9100)
        ObjectType.PEDESTRIAN -> Color(0xFFFF1744)
        else -> Color(0xFF00E5FF)
    }

    drawCircle(
        color = markerColor,
        radius = 12f,
        center = Offset(markerX, markerY)
    )
}

@Composable
private fun TopStaticDashboard(
    uiState: SafetyHudUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFF131924), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF232D3F), RoundedCornerShape(10.dp))
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 차속 표시
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${uiState.speedKmh.toInt()}",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "km/h",
                color = Color(0xFF8A99AD),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 조향각 및 방향 표시
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (uiState.turnSignal) {
                    TurnSignalState.LEFT -> "◀ 좌회전 방향"
                    TurnSignalState.RIGHT -> "우회전 방향 ▶"
                    TurnSignalState.HAZARD -> "◀ 비상등 ▶"
                    TurnSignalState.OFF -> "조향 ${uiState.steeringAngleDeg.toInt()}°"
                },
                color = if (uiState.turnSignal != TurnSignalState.OFF) Color(0xFFFF9100) else Color(0xFF00E5FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (uiState.alertLevel) {
                    SafetyAlertLevel.L0_IDLE -> "L0: 시야 보조 작동 중"
                    SafetyAlertLevel.L1_ADVISORY -> "L1: 정적 방향 안내"
                    SafetyAlertLevel.L2_WARNING -> "L2: 시야 확인 경고"
                    SafetyAlertLevel.L3_ACTIVE -> "L3: 적극 경고 모드"
                    SafetyAlertLevel.RESTRICTED_MODE -> "제한 모드 (센서/권한 제한)"
                    SafetyAlertLevel.PARKED_VALIDATION -> "정차 중 검증 모드"
                },
                color = Color(0xFF8A99AD),
                fontSize = 10.sp
            )
        }

        // Zone Context 표시
        if (uiState.isSchoolZoneTimeWeighted) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF311B92), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "스쿨존 시간대 컨텍스트 활성 (14~18시)",
                    color = Color(0xFFD1C4E9),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // [작업명령 #8] Replay 모드 정적 배지 표시
        if (uiState.executionMode == ai.pleos.app.safetywindow.core.model.ExecutionMode.REPLAY_INPUT) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFE65100), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "기록 입력 재생",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BottomStaticPanel(
    uiState: SafetyHudUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 정차 상태 감지 정적 배지 (일시정지 준수 대체)
        if (uiState.isVehicleStationary) {
            Row(
                modifier = Modifier
                    .background(Color(0xFF1B5E20), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "차량 정지 상태 감지 (0 km/h) | 주변을 직접 확인하세요",
                    color = Color(0xFFE8F5E9),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 방향별 중립적 시야 확인 안내 텍스트 (Release 및 Scenario 시연에서는 Legacy 시각 출력 차단)
        when (uiState.alertLevel) {
            SafetyAlertLevel.L2_WARNING, SafetyAlertLevel.L3_ACTIVE -> {
                if (uiState.advisoryOutputFlags.enableLegacyVisualL2) {
                    Text(
                        text = if (uiState.rightPillarAlertActive) "우측 시야를 확인하세요 (가림영역 주의)" else "좌측 시야를 확인하세요 (가림영역 주의)",
                        color = Color(0xFFFF5252),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            SafetyAlertLevel.L1_ADVISORY -> {
                if (uiState.advisoryOutputFlags.enableLegacyVisualL1) {
                    Text(
                        text = if (uiState.rightPillarAlertActive) "우측 시야를 확인하세요" else "좌측 시야를 확인하세요",
                        color = Color(0xFFFFB74D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            SafetyAlertLevel.RESTRICTED_MODE -> {
                Text(
                    text = "센서 신뢰도 부족 - 주변 시야를 직접 확인하세요",
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            SafetyAlertLevel.PARKED_VALIDATION -> {
                Text(
                    text = "[Simulated future integration] 정차 중 미래 가상 객체 데모 검증",
                    color = Color(0xFFFFB74D),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            SafetyAlertLevel.L0_IDLE -> {
                // L0 침묵 유지: 추가 경고 텍스트 없음
            }
        }
    }
}

@Composable
private fun ParkedValidationOverlay(
    metrics: OcclusionValidationMetrics,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xF0101726), RoundedCornerShape(12.dp))
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "양안 A필러 가림 예측 & Scenario L1 통합 검증 패널",
            color = Color(0xFF00E5FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        // Row 1: Scenario L1 Integration & Execution Mode
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scenario L1 Integration: ${metrics.scenarioL1IntegrationStatus}",
                color = Color(0xFFFFD54F),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Execution Mode: ${metrics.executionModeStr}",
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Row 2: Input Provenance
        Text(
            text = "Input Provenance: ${metrics.inputProvenanceStr}",
            color = Color(0xFFB0BEC5),
            fontSize = 9.5.sp
        )

        // Row 3: Raw Candidate & Stabilized Overlay
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Raw Candidate: ${metrics.rawCandidateStr}",
                color = Color(0xFF80CBC4),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .background(
                        if (metrics.stabilizedOverlayStr.startsWith("VISIBLE")) Color(0xFF1B5E20) else Color(0xFF37474F),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Stabilized Overlay: ${metrics.stabilizedOverlayStr}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Row 4: Policy Restriction & E2E Latency
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Policy Restriction: ${metrics.policyRestrictionStr}",
                color = if (metrics.policyRestrictionStr.startsWith("ACTIVE")) Color(0xFFFF8A80) else Color(0xFFA5D6A7),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "E2E Latency: ${metrics.e2eLatencyStr}",
                color = Color(0xFFFFCC80),
                fontSize = 10.sp
            )
        }

        // Row 5: Existing State Machine Unchanged & Feature Quality
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Existing State Machine Unchanged: ${metrics.existingStateMachineUnchangedStr}",
                color = Color(0xFF81C784),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Feature: ${metrics.featureId} (${metrics.spatialQuality})",
                color = Color.White,
                fontSize = 10.sp
            )
        }

        // Row 6: Legacy Heuristic Candidate & Visual Suppression
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Legacy Candidate: ${metrics.legacyCandidateStr}",
                color = Color(0xFFFFB74D),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Legacy Visual: ${metrics.legacyVisualSuppressedStr}",
                color = Color(0xFFB0BEC5),
                fontSize = 9.5.sp
            )
        }

        // Row 7: Legacy TTS Suppressed & Active Pipeline & Release Defaults
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TTS: ${metrics.legacyTtsSuppressedStr}",
                color = Color(0xFFFF8A80),
                fontSize = 9.5.sp
            )
            Text(
                text = "Pipeline: ${metrics.activePipelineStr}",
                color = Color(0xFF80CBC4),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Defaults: ${metrics.releaseSafeDefaultsStr}",
                color = Color(0xFFE0E0E0),
                fontSize = 9.5.sp
            )
        }

        // Section 16 Mandatory Notice: '진단 정보 / 실제 주행 경고에 미적용'
        Box(
            modifier = Modifier
                .background(Color(0xFFE65100), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = metrics.isolationNoticeStr,
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

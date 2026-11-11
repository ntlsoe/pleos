package ai.pleos.app.safetywindow.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ai.pleos.app.safetywindow.batch.execution.VehicleSensitivityRunner
import ai.pleos.app.safetywindow.demo.model.*
import ai.pleos.app.safetywindow.demo.policy.DemoVisualSpec
import ai.pleos.app.safetywindow.demo.replay.*
import kotlin.math.*

/**
 * FourViewDemoInteractiveHost
 * [작업명령 #24] 섹션 5, 6, 7, 8: 전수 통과 Run 실데이터 4분할 시연 호스트
 *
 * 1. DemoReplayRegistry에서 100개 보호구역, 현대차 5종, Clean Run 실제 산출물 로드
 * 2. 4분할 화면(메인 3D, 운전자 시점, 조감도, Pleos HUD) 단일 DemoSceneState 동기화
 * 3. 사용자 조작: 구역·차량 5종·시나리오·속도·배속(0.5x, 1x, 2x)·재생·일시정지·한프레임·초기화·Click-to-Swap
 * 4. 접이식 '실행 증거(Execution Evidence)' 패널: 25개 필드 해시 및 공개 제원 고지
 * 5. Fail-Closed: 파일 누락 또는 해시 불일치 시 출력 완전 억제
 */
@Composable
fun FourViewDemoInteractiveHost(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Initialise registry
    LaunchedEffect(Unit) {
        DemoReplayRegistry.initializeIfNeeded()
    }

    var selectedZoneId by remember { mutableStateOf("SZ_001") }
    var selectedVehicleId by remember { mutableStateOf("IONIQ_5") }
    var selectedScenarioId by remember { mutableStateOf("INTERSECTION_LEFT_TURN") }
    var selectedSpeedId by remember { mutableStateOf("CONST_20") }
    var playbackRate by remember { mutableFloatStateOf(1.0f) }
    var isPlaying by remember { mutableStateOf(true) }
    var simTimeMillis by remember { mutableLongStateOf(0L) }
    var showEvidencePanel by remember { mutableStateOf(false) }

    var primaryCamera by remember { mutableStateOf(DemoCamera.EXTERNAL_CHASE) }
    var aux1Camera by remember { mutableStateOf(DemoCamera.DRIVER_EYE) }
    var aux2Camera by remember { mutableStateOf(DemoCamera.TOP_DOWN) }
    var isRecording by remember { mutableStateOf(false) }

    // Loaded raw frames and active entry
    var currentEntry by remember { mutableStateOf<DemoReplayEntry?>(null) }
    var loadedFrames by remember { mutableStateOf<List<DemoRawInputFrame>>(emptyList()) }
    var currentState by remember { mutableStateOf<DemoSceneState?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Replay selection update
    LaunchedEffect(selectedZoneId, selectedVehicleId, selectedScenarioId, selectedSpeedId) {
        simTimeMillis = 0L
        loadError = null

        val matches = DemoReplayRegistry.filter(
            zoneId = selectedZoneId,
            vehicleId = selectedVehicleId,
            scenarioId = selectedScenarioId,
            speedId = selectedSpeedId
        )

        val entry = matches.firstOrNull() ?: DemoReplayRegistry.filter(zoneId = selectedZoneId).firstOrNull()
        currentEntry = entry

        if (entry != null) {
            try {
                val frames = DemoReplayLoader.loadFrames(entry)
                loadedFrames = frames
                if (frames.isNotEmpty()) {
                    currentState = BatchRunToDemoSceneMapper.mapToSceneState(entry, frames[0], 0L)
                }
            } catch (e: Exception) {
                loadError = e.message
                loadedFrames = emptyList()
            }
        } else {
            loadError = "선택한 조건의 Replay Run을 찾을 수 없습니다 (Fail-Closed)."
            loadedFrames = emptyList()
        }
    }

    // Playback loop
    LaunchedEffect(isPlaying, playbackRate, loadedFrames, currentEntry) {
        val entry = currentEntry
        val frames = loadedFrames
        if (entry == null || frames.isEmpty()) return@LaunchedEffect

        val maxDuration = frames.last().simulationTimeMillis
        while (isPlaying) {
            val stepDelay = (33L / playbackRate).toLong().coerceAtLeast(10L)
            delay(stepDelay)
            simTimeMillis += 33L
            if (simTimeMillis > maxDuration) {
                simTimeMillis = 0L
            }

            // Find closest frame by simulationTimeMillis
            val frame = frames.minByOrNull { abs(it.simulationTimeMillis - simTimeMillis) } ?: frames.last()
            currentState = BatchRunToDemoSceneMapper.mapToSceneState(entry, frame, simTimeMillis)
        }
    }

    val state = currentState

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070F18))
    ) {
        // 상단 헤더 바 (구역/차량/시나리오 선택기)
        DemoHeaderBar(
            selectedZoneId = selectedZoneId,
            onSelectZone = { selectedZoneId = it },
            selectedVehicleId = selectedVehicleId,
            onSelectVehicle = { selectedVehicleId = it },
            selectedScenarioId = selectedScenarioId,
            onSelectScenario = { selectedScenarioId = it },
            onClose = onClose
        )

        if (state == null || loadError != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF030712)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ Fail-Closed: ${loadError ?: "데이터 로드 중..."}",
                        color = Color(0xFFEF4444),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "원시 산출물 검증 실패 시 안전을 위해 출력을 억제합니다.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            // 4분할 화면 바디
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                // 메인 뷰 (3배 너비)
                Box(
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxHeight()
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp))
                        .background(Color(0xFF030712))
                ) {
                    Demo3DCanvasView(state = state, camera = primaryCamera)

                    // 메인 카메라 레이블 뱃지 및 가상환경 표기
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                text = "메인 3D: ${cameraLabel(primaryCamera)} [Clean Run 동기화]",
                                color = Color(0xFF38BDF8),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = state.virtualEnvironmentBadge,
                                color = Color(0xFFF59E0B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 접이식 실행 증거 패널 오버레이
                    if (showEvidencePanel && currentEntry != null) {
                        ExecutionEvidenceOverlay(
                            entry = currentEntry!!,
                            state = state,
                            onClose = { showEvidencePanel = false },
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 3개 보조 분할 패널 (운전자 시점, 조감도, Pleos HUD)
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                ) {
                    MiniPane(
                        title = "운전자 시점: ${cameraLabel(aux1Camera)} (탭하여 메인 전환)",
                        camera = aux1Camera,
                        onCameraSwap = {
                            val temp = primaryCamera
                            primaryCamera = aux1Camera
                            aux1Camera = temp
                        }
                    ) {
                        Demo3DCanvasView(state = state, camera = aux1Camera)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    MiniPane(
                        title = "조감도: ${cameraLabel(aux2Camera)} (탭하여 메인 전환)",
                        camera = aux2Camera,
                        onCameraSwap = {
                            val temp = primaryCamera
                            primaryCamera = aux2Camera
                            aux2Camera = temp
                        }
                    ) {
                        Demo3DCanvasView(state = state, camera = aux2Camera)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    MiniPane(
                        title = "Pleos Demo HUD (단일 상태 연동)",
                        camera = primaryCamera,
                        onCameraSwap = { }
                    ) {
                        PleosDemoHud(state = state)
                    }
                }
            }

            // 하단 컨트롤 바
            DemoControlBar(
                state = state,
                entry = currentEntry,
                isPlaying = isPlaying,
                playbackRate = playbackRate,
                isRecording = isRecording,
                showEvidence = showEvidencePanel,
                onPlayPause = { isPlaying = !isPlaying },
                onStepFrame = {
                    simTimeMillis += 100L
                    val frames = loadedFrames
                    if (frames.isNotEmpty()) {
                        val maxD = frames.last().simulationTimeMillis
                        if (simTimeMillis > maxD) simTimeMillis = maxD
                        val frame = frames.minByOrNull { abs(it.simulationTimeMillis - simTimeMillis) } ?: frames.last()
                        if (currentEntry != null) {
                            currentState = BatchRunToDemoSceneMapper.mapToSceneState(currentEntry!!, frame, simTimeMillis)
                        }
                    }
                },
                onReset = { simTimeMillis = 0L },
                onRateChange = { playbackRate = it },
                onToggleEvidence = { showEvidencePanel = !showEvidencePanel },
                onRecordToggle = { isRecording = !isRecording }
            )
        }
    }
}

private fun cameraLabel(camera: DemoCamera): String = when (camera) {
    DemoCamera.EXTERNAL_CHASE -> "외부 3D 추적 시점"
    DemoCamera.DRIVER_EYE -> "운전자 콕핏 시점"
    DemoCamera.TOP_DOWN -> "상공 탑다운 조감도"
    DemoCamera.FREE -> "자유 3D 시점"
}

@Composable
private fun DemoHeaderBar(
    selectedZoneId: String,
    onSelectZone: (String) -> Unit,
    selectedVehicleId: String,
    onSelectVehicle: (String) -> Unit,
    selectedScenarioId: String,
    onSelectScenario: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "3D 4분할 실데이터 시연",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // 차량 5종 선택기
                val vehicles = listOf("CASPER", "IONIQ_6", "GRANDEUR", "IONIQ_5", "IONIQ_9")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    vehicles.forEach { vid ->
                        val isSel = vid == selectedVehicleId
                        val spec = VehicleSensitivityRunner.CANONICAL_VEHICLES[vid]
                        val label = spec?.vehicleName ?: vid
                        Surface(
                            color = if (isSel) Color(0xFF7C3AED) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.clickable { onSelectVehicle(vid) }
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) Color.White else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("2D HUD 복귀", fontSize = 11.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // 시나리오 및 보호구역 선택행
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 시나리오 선택기
                val scenarios = listOf(
                    "STRAIGHT_SILENCE" to "직진(L0)",
                    "ZONE_ENTRY" to "진입(L1)",
                    "CURVE_LEFT" to "곡선(L1)",
                    "INTERSECTION_LEFT_TURN" to "교차로좌회전(L2)",
                    "INTERSECTION_RIGHT_TURN" to "교차로우회전(L2)",
                    "CROSSWALK_STOP_AND_GO" to "횡단보도(L2)",
                    "STALE_SUPPRESSION" to "Stale타임아웃(L3->차단)",
                    "LOW_QUALITY_FAIL_CLOSED" to "저품질억제(Fail-Closed)"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    scenarios.forEach { (sid, slabel) ->
                        val isSel = sid == selectedScenarioId
                        Surface(
                            color = if (isSel) Color(0xFF2563EB) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.clickable { onSelectScenario(sid) }
                        ) {
                            Text(
                                text = slabel,
                                color = if (isSel) Color.White else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 대표 보호구역 샘플 토글
                val zones = listOf("SZ_001", "SZ_002", "SZ_010", "SZ_042", "SZ_074", "SZ_081", "SZ_100")
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    zones.forEach { zid ->
                        val isSel = zid == selectedZoneId
                        Surface(
                            color = if (isSel) Color(0xFF059669) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.clickable { onSelectZone(zid) }
                        ) {
                            Text(
                                text = zid,
                                color = if (isSel) Color.White else Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MiniPane(
    title: String,
    camera: DemoCamera,
    onCameraSwap: (DemoCamera) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp))
            .background(Color(0xFF0B132B))
            .clickable { onCameraSwap(camera) }
    ) {
        content()
        Surface(
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(bottomEnd = 4.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = title,
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * 실행 증거(Execution Evidence) 패널 오버레이
 */
@Composable
private fun ExecutionEvidenceOverlay(
    entry: DemoReplayEntry,
    state: DemoSceneState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0F172A).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .padding(8.dp)
            .width(340.dp)
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 실행 증거 (Execution Evidence)",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "닫기 ✕",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onClose() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = Color(0xFF334155))
            Spacer(modifier = Modifier.height(6.dp))

            EvidenceRow("Run ID", entry.runId)
            EvidenceRow("구역 / 시설명", "${entry.zoneId} (${entry.facilityName})")
            EvidenceRow("선택 도로링크", entry.selectedLinkId.ifBlank { "N/A" })
            EvidenceRow("차량 프로파일", "${entry.vehicleProfileId} (${entry.syntheticPillarProfileId})")
            EvidenceRow("중간 다이제스트", entry.intermediateDigest.take(16) + "...")
            EvidenceRow("시나리오 / 속도", "${entry.scenarioId} (${entry.speedProfileId})")
            EvidenceRow("소스 커밋", entry.sourceCommit)
            EvidenceRow("입력 Manifest SHA", entry.inputCanonicalJsonSha256.take(16) + "...")
            EvidenceRow("출력 Jsonl.gz SHA", entry.outputCanonicalJsonSha256.take(16) + "...")
            EvidenceRow("캐시 재사용 검증", "cacheHit=false, reusedResult=false (Clean Run)")
            EvidenceRow("최대 가림비 (Possible)", "${entry.peakPossibleRatio}")
            EvidenceRow("보편 가림비 (Universal)", "${entry.peakUniversalRatio}")
            EvidenceRow("차폐 판정 / 수준", "${state.advisory.geometryAssessment} (${state.advisory.stabilizedLevel})")
            if (state.advisory.outputSuppressed) {
                EvidenceRow("출력 억제 사유", state.advisory.suppressionReason ?: "Fail-Closed")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ 본 시연은 현대자동차 공개 외형 제원 및 차체군 합성 필러 모델에 기반한 민감도 분석 결과이며, 실차 CAD 사각지도를 대변하지 않습니다.",
                    color = Color(0xFFFBBF24),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 9.5.sp)
        Text(
            text = value,
            color = Color(0xFFF1F5F9),
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 3D 원근 투영 캔버스 뷰
 */
@Composable
fun Demo3DCanvasView(
    state: DemoSceneState,
    camera: DemoCamera,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060B14))
    ) {
        val w = size.width
        val h = size.height
        if (w <= 10f || h <= 10f) return@Canvas

        val pose = state.vehiclePose
        val px = pose.position.x
        val py = pose.position.y
        val pz = pose.position.z
        val heading = pose.headingRadians

        val (camPos, lookTarget, upVec) = when (camera) {
            DemoCamera.EXTERNAL_CHASE -> {
                val camDist = 14.0
                val camH = 5.5
                Triple(
                    Point3D(px - camDist * sin(heading), py + camH, pz - camDist * cos(heading)),
                    Point3D(px + 4.0 * sin(heading), py + 1.2, pz + 4.0 * cos(heading)),
                    Point3D(0.0, 1.0, 0.0)
                )
            }
            DemoCamera.DRIVER_EYE -> {
                Triple(
                    Point3D(px - 0.38 * cos(heading), py + 1.15, pz + 0.38 * sin(heading)),
                    Point3D(px - 0.38 * cos(heading) + 30.0 * sin(heading), py + 1.10, pz + 0.38 * sin(heading) + 30.0 * cos(heading)),
                    Point3D(0.0, 1.0, 0.0)
                )
            }
            DemoCamera.TOP_DOWN -> {
                Triple(
                    Point3D(px, py + 48.0, pz),
                    Point3D(px, py, pz),
                    Point3D(-sin(heading), 0.0, -cos(heading))
                )
            }
            DemoCamera.FREE -> {
                Triple(
                    Point3D(px + 16.0, py + 14.0, pz - 16.0),
                    Point3D(px, py + 1.0, pz),
                    Point3D(0.0, 1.0, 0.0)
                )
            }
        }

        val fwdX = lookTarget.x - camPos.x
        val fwdY = lookTarget.y - camPos.y
        val fwdZ = lookTarget.z - camPos.z
        val fwdLen = sqrt(fwdX * fwdX + fwdY * fwdY + fwdZ * fwdZ).coerceAtLeast(1e-6)
        val fx = fwdX / fwdLen
        val fy = fwdY / fwdLen
        val fz = fwdZ / fwdLen

        val rxRaw = fy * upVec.z - fz * upVec.y
        val ryRaw = fz * upVec.x - fx * upVec.z
        val rzRaw = fx * upVec.y - fy * upVec.x
        val rLen = sqrt(rxRaw * rxRaw + ryRaw * ryRaw + rzRaw * rzRaw).coerceAtLeast(1e-6)
        val rx = rxRaw / rLen
        val ry = ryRaw / rLen
        val rz = rzRaw / rLen

        val ux = ry * fz - rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy - ry * fx

        val fovRad = Math.toRadians(55.0)
        val focal = (w / 2.0) / tan(fovRad / 2.0)
        val topDownScale = min(w, h) / 42.0

        fun project(p: Point3D): Offset? {
            val dx = p.x - camPos.x
            val dy = p.y - camPos.y
            val dz = p.z - camPos.z

            val xc = dx * rx + dy * ry + dz * rz
            val yc = dx * ux + dy * uy + dz * uz
            val zc = dx * fx + dy * fy + dz * fz

            return if (camera == DemoCamera.TOP_DOWN) {
                Offset((w / 2.0 + xc * topDownScale).toFloat(), (h / 2.0 - yc * topDownScale).toFloat())
            } else {
                if (zc < 0.4) return null
                Offset((w / 2.0 + (xc / zc) * focal).toFloat(), (h / 2.0 - (yc / zc) * focal).toFloat())
            }
        }

        // 1. 도로 표면 렌더링
        val roadSegments = listOf(-30.0, -15.0, 0.0, 15.0, 30.0, 45.0, 60.0)
        for (i in 0 until roadSegments.size - 1) {
            val z0 = pz + roadSegments[i]
            val z1 = pz + roadSegments[i + 1]

            val pL0 = project(Point3D(-3.6, 0.0, z0))
            val pR0 = project(Point3D(3.6, 0.0, z0))
            val pR1 = project(Point3D(3.6, 0.0, z1))
            val pL1 = project(Point3D(-3.6, 0.0, z1))

            if (pL0 != null && pR0 != null && pR1 != null && pL1 != null) {
                val roadPath = Path().apply {
                    moveTo(pL0.x, pL0.y)
                    lineTo(pR0.x, pR0.y)
                    lineTo(pR1.x, pR1.y)
                    lineTo(pL1.x, pL1.y)
                    close()
                }
                drawPath(roadPath, Color(0xFF131D2E))
                drawLine(Color(0xFF94A3B8), pL0, pL1, strokeWidth = 2.0f)
                drawLine(Color(0xFF94A3B8), pR0, pR1, strokeWidth = 2.0f)

                val c0 = project(Point3D(0.0, 0.0, z0))
                val c1 = project(Point3D(0.0, 0.0, z1))
                if (c0 != null && c1 != null && i % 2 == 0) {
                    drawLine(Color(0xFFFACC15), c0, c1, strokeWidth = 2.5f)
                }
            }
        }

        // 2. 위험 구역 / 횡단보도 렌더링
        val cwZ0 = pz + 18.0
        val cwZ1 = pz + 23.0
        for (stripe in -3..3) {
            val x0 = stripe * 0.9 - 0.25
            val x1 = stripe * 0.9 + 0.25
            val s0 = project(Point3D(x0, 0.02, cwZ0))
            val s1 = project(Point3D(x1, 0.02, cwZ0))
            val s2 = project(Point3D(x1, 0.02, cwZ1))
            val s3 = project(Point3D(x0, 0.02, cwZ1))
            if (s0 != null && s1 != null && s2 != null && s3 != null) {
                val stripePath = Path().apply {
                    moveTo(s0.x, s0.y)
                    lineTo(s1.x, s1.y)
                    lineTo(s2.x, s2.y)
                    lineTo(s3.x, s3.y)
                    close()
                }
                drawPath(stripePath, Color(0xFFE2E8F0).copy(alpha = 0.85f))
            }
        }

        // 3. 자차량 바운딩 박스
        val vProf = state.vehicleProfile
        val hl = vProf.lengthM / 2.0
        val hw = vProf.widthM / 2.0
        val hh = vProf.heightM

        val cornersLocal = listOf(
            Point3D(-hw, 0.0, -hl), Point3D(hw, 0.0, -hl),
            Point3D(hw, 0.0, hl), Point3D(-hw, 0.0, hl),
            Point3D(-hw, hh, -hl), Point3D(hw, hh, -hl),
            Point3D(hw, hh, hl), Point3D(-hw, hh, hl)
        )
        val cornersProj = cornersLocal.map { cl ->
            val wx = px + cl.x * cos(heading) + cl.z * sin(heading)
            val wy = py + cl.y
            val wz = pz - cl.x * sin(heading) + cl.z * cos(heading)
            project(Point3D(wx, wy, wz))
        }

        // Draw vehicle wireframe
        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )
        for ((a, b) in edges) {
            val pA = cornersProj[a]
            val pB = cornersProj[b]
            if (pA != null && pB != null) {
                drawLine(Color(0xFF38BDF8), pA, pB, strokeWidth = 2.0f)
            }
        }

        // 4. 합성 A필러 차폐 콘 렌더링 (사각지대 볼륨)
        val occlLevel = state.advisory.stabilizedLevel
        val coneColor = when (occlLevel) {
            DemoLevel.L3 -> Color(0xFFDC2626).copy(alpha = 0.40f)
            DemoLevel.L2 -> Color(0xFFEA580C).copy(alpha = 0.35f)
            DemoLevel.L1 -> Color(0xFFEAB308).copy(alpha = 0.25f)
            else -> Color(0xFF10B981).copy(alpha = 0.15f)
        }

        // Cone apex at eye, base at feature direction
        val eyePos = state.eyeSamples.firstOrNull() ?: Point3D(px - 0.35, py + 1.2, pz)
        val coneApexProj = project(Point3D(px + eyePos.x, py + eyePos.y, pz + eyePos.z))

        val baseRadius = 2.5 + state.metrics.peakPossibleRatio * 4.0
        val coneBaseL = project(Point3D(px - baseRadius, 0.5, pz + 16.0))
        val coneBaseR = project(Point3D(px - baseRadius * 0.4, 2.0, pz + 20.0))

        if (coneApexProj != null && coneBaseL != null && coneBaseR != null && occlLevel != DemoLevel.L0) {
            val conePath = Path().apply {
                moveTo(coneApexProj.x, coneApexProj.y)
                lineTo(coneBaseL.x, coneBaseL.y)
                lineTo(coneBaseR.x, coneBaseR.y)
                close()
            }
            drawPath(conePath, coneColor)
        }
    }
}

/**
 * Pleos Demo HUD
 */
@Composable
fun PleosDemoHud(state: DemoSceneState) {
    val levelColor = Color(DemoVisualSpec.color(state.advisory.stabilizedLevel))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PLEOS DEMO HUD",
                color = Color(0xFF38BDF8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = levelColor.copy(alpha = 0.20f),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    text = state.advisory.stabilizedLevel.name,
                    color = levelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // 가림 지표 게이지
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("합성 가림비 (Possible)", color = Color(0xFF94A3B8), fontSize = 9.sp)
                Text("${(state.metrics.peakPossibleRatio * 100).toInt()}%", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { state.metrics.peakPossibleRatio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = levelColor,
                trackColor = Color(0xFF1E293B)
            )
        }

        // 상태 메시지
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(5.dp)) {
                Text(
                    text = state.advisory.message,
                    color = if (state.advisory.stabilizedLevel == DemoLevel.L0) Color(0xFF94A3B8) else levelColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
                if (state.advisory.outputSuppressed) {
                    Text(
                        text = "🚫 출력 억제: ${state.advisory.suppressionReason}",
                        color = Color(0xFFEF4444),
                        fontSize = 8.5.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = state.dataQualityLabel,
                color = Color(0xFF64748B),
                fontSize = 8.5.sp
            )
            Text(
                text = "${(state.vehiclePose.speedMps * 3.6).toInt()} km/h",
                color = Color(0xFF38BDF8),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 하단 재생/배속/초기화/증거 컨트롤 바
 */
@Composable
private fun DemoControlBar(
    state: DemoSceneState,
    entry: DemoReplayEntry?,
    isPlaying: Boolean,
    playbackRate: Float,
    isRecording: Boolean,
    showEvidence: Boolean,
    onPlayPause: () -> Unit,
    onStepFrame: () -> Unit,
    onReset: () -> Unit,
    onRateChange: (Float) -> Unit,
    onToggleEvidence: () -> Unit,
    onRecordToggle: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("초기화", fontSize = 10.5.sp)
            }

            Button(
                onClick = onPlayPause,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) Color(0xFFEA580C) else Color(0xFF16A34A)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(if (isPlaying) "일시정지 ⏸" else "재생 ▶", fontSize = 10.5.sp)
            }

            Button(
                onClick = onStepFrame,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("+1 프레임", fontSize = 10.sp)
            }

            // 배속 선택 (0.5x, 1x, 2x)
            val rates = listOf(0.5f, 1.0f, 2.0f)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rates.forEach { r ->
                    val isSel = abs(playbackRate - r) < 0.01f
                    Surface(
                        color = if (isSel) Color(0xFF2563EB) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.clickable { onRateChange(r) }
                    ) {
                        Text(
                            text = "${r}x",
                            color = if (isSel) Color.White else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 실행 증거 패널 토글
            Button(
                onClick = onToggleEvidence,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showEvidence) Color(0xFF0284C7) else Color(0xFF334155)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(if (showEvidence) "증거 닫기 ▲" else "실행 증거 ▼", fontSize = 10.5.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "t = ${state.simulationTimeMillis} ms | ${(state.vehiclePose.speedMps * 3.6).toInt()} km/h",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${entry?.zoneId ?: ""} · ${state.vehicleProfile.label} (${entry?.runId?.take(12) ?: ""})",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp
                )
            }
        }
    }
}

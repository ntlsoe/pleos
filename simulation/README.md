# Pleos Safety Window - Binocular Occlusion Reference Simulation Engine

## 1. Overview
This directory contains the Python standalone reference engine for calculating binocular A-pillar occlusion over dynamic vehicle trajectories and parameterized risk spaces.

## 2. Coordinate System (ISO 8855 Standard)
Vehicle-fixed right-handed coordinate system strictly conforming to ISO 8855:
- **Origin (0,0,0)**: Ground level at vehicle **rear axle center projection to ground plane (Z=0)**.
- **+X**: Longitudinal forward along vehicle centerline.
- **+Y**: Lateral left (driver side in LHD vehicles).
- **+Z**: Upward perpendicular to road plane.
- **Heading ($\psi$)**: Rotation about +Z axis (counter-clockwise positive, 0 = forward along +X, left turn > 0, right turn < 0).

### Geometric Sanity Constraints
- Rear Axle Center: $X = 0.0\text{ m}, Y = 0.0\text{ m}, Z = 0.0\text{ m}$
- Front Axle Center: $X = L_{\text{wheelbase}} > 0\text{ m}, Y = 0.0\text{ m}, Z = 0.0\text{ m}$
- Front Bumper: $X = L_{\text{wheelbase}} + L_{\text{front\_overhang}} \approx 3.65\text{ m}$
- Rear Bumper: $X = -L_{\text{rear\_overhang}} \approx -0.85\text{ m}$
- Driver Nominal Seating: $0 < X_{\text{eye}} < L_{\text{wheelbase}}$ (Cabin interior: $X \approx 1.30\text{ m}, Y \approx +0.38\text{ m}, Z \approx 1.20\text{ m}$)
- A-Pillars: Located physically ahead of driver eye ($X_{\text{base}} \approx 2.15\text{ m} > X_{\text{top}} \approx 1.52\text{ m} > X_{\text{eye}} = 1.30\text{ m}$)
- Risk Surface: Positioned ahead of front bumper ($X \ge 6.0\text{ m} \sim 15.0\text{ m}$)

## 3. Ray-Triangle Intersection Parameter ($t$)
The Möller–Trumbore algorithm calculates the line-segment parameter $t$ along the ray:
$$\vec{R}(t) = \vec{E} + t \cdot (\vec{P} - \vec{E})$$
where $\vec{E}$ is the eye point, $\vec{P}$ is the target risk sample point, and $\vec{D} = \vec{P} - \vec{E}$ is the directional segment vector.
- **Physical Meaning**: $t$ is a dimensionless line-segment fraction representing relative position between eye ($t=0$) and target ($t=1$).
- **Occlusion Condition**: A triangle occludes the line of sight if and only if:
  $$\epsilon < t < (1.0 - \epsilon), \quad \epsilon = 10^{-5}$$
- **Exclusions**:
  * $t < 0$: Triangle is located behind the driver's eye (no occlusion).
  * $t > 1$: Triangle is located behind the target sample point (no occlusion).
  * $|\det| < 10^{-7}$: Ray is coplanar/parallel to triangle (degenerate, no occlusion).

## 4. Vehicle Profiles
All profiles use synthetic representative parameters for algorithm verification only:
- `RepresentativeSedanV0`: Sedan geometry ($L=2.80\text{ m}, W=1.82\text{ m}, H=1.45\text{ m}$)
- `RepresentativeSuvV0`: Elevated SUV geometry ($L=2.85\text{ m}, W=1.88\text{ m}, H=1.65\text{ m}$)
- `RepresentativeMpvV0`: MPV cab-forward geometry ($L=3.00\text{ m}, W=1.92\text{ m}, H=1.78\text{ m}$)
- `SymmetricVehicleTestProfile`: Centerline-seated driver ($Y_{\text{eye}} = 0$) and perfectly mirrored A-pillars for left-right symmetry testing
- `SyntheticCenterProfile`: Single eye pair on nominal seating center
- `RepresentativePopulationApproximation`: 9-sample synthetic eye distribution ($N=9$, $\sum w_i = 1.0$)

> [!NOTE]
> Parameter Source: `SYNTHETIC_TEST_ONLY` and `J941_CONCEPT_APPROXIMATION`.
> Not calibrated to specific production vehicles nor official SAE J941 proprietary tables.

## 5. Metrics & Truncation Semantics
- **$T_{\text{occ}}$ (Max Per-Point Continuous Duration)**: Maximum consecutive duration a single sample point $k$ is continuously occluded.
- **$T_{\text{any}}$ (Any-Point Continuous Duration)**: Maximum consecutive duration during which at least one sample point is occluded.
- **Truncation Tracking**:
  * `wasDurationTruncated`: True if occlusion was actively ongoing at the final simulation timestep ($t = t_{\text{end}}$).
  * `trajectoryEndReason`: `"SIMULATION_TIME_LIMIT"` if truncated; `"TARGET_PASSED_OR_OUT_OF_BOUNDS"` if occlusion cleanly terminated.

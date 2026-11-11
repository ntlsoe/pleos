# Pleos Safety Window — v48 verified source baseline

## Source

- Commit: `7110eb42057c4404dcbc0034aecb08a65bd18721`
- Tag: `v48-verified-source-baseline`

## Assets

- `PleosSafetyWindowTeam_PleosSafetyWindow_release.apk`
- SHA-256: `0c3da53fe4a5ea508b9fd4a0d55e552ee630ed030dee1cc9fd1fb1467929d741`
- Size: 40,098,338 bytes

## Verification

- Product JVM: 492/492 passing in each of two clean ASCII-path clones.
- Advisory focused JVM: 10/10 passing in each clone.
- Android instrumented: 1/1 passing in each clone.
- Debug and Release builds succeeded in each clone.
- Release APK ZIP entries match across clones; Debug DEX decoded semantic comparison passed 18/18.

## Limitations

SET-07 uses synthetic geometry/pose input for behavior verification only. The result is not vehicle safety validation, pedestrian detection, collision prediction, or an accident-prevention claim. Native Car UX, real-vehicle operation, and acoustic TTS were not validated. The Unicode project path remains a known Gradle test-worker limitation; run verification from an ASCII path.

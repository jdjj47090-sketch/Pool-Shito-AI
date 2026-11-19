# POOL SHITO AI

Android pool-table analysis using the device camera, OpenCV, and a local Kotlin analysis core. Camera frames remain on-device; the app does not upload images or require a service account.

## What works

- CameraX live preview with runtime permission handling, rear/front camera switching, lifecycle rebinding, and keep-only-latest frame analysis.
- Reused YUV/RGB buffers, portrait/landscape frame rotation, adjustable 5/8 fps analysis and 360p/720p processing resolution, and a responsive preview while processing runs on a single background executor.
- OpenCV table segmentation for green/blue felt and quadrilateral boundary detection. Manual four-corner calibration is available for other felt colors or difficult viewpoints.
- Hough-circle ball candidates, center/radius/confidence output, conservative white cue-ball detection, solid classification from broad color coverage, and stripe classification only when color coverage forms a spatial band from center-to-edge samples; ambiguous balls remain `UNKNOWN`. Frame-to-frame IDs with a constant-velocity Kalman filter.
- Perspective mapping to a 2:1 table plane, real-frame ball/table overlays, IDs, confidence, movement vectors, and estimated paths.
- Equal-mass collision and cushion estimates for observed cue-ball movement. For stationary layouts, the app can draw one unobstructed cue-to-ball-to-pocket candidate. Candidate lines are guidance; cue aim is not observed and pocketing is not guaranteed.

## Analysis model and limits

The app uses classical OpenCV segmentation and circle detection, not a hidden or bundled neural model. It never draws synthetic balls or fills in missing detections. Ball classification samples HSV saturation at the ball center and outer disk. This is a heuristic, not a learned or tournament-validated classifier; stripes without a visible band return `UNKNOWN`. Cue-ball identity can be missed under glare, shadows, colored markings, or low resolution. Overlapping balls may merge into one circle or be missed. Automatic table finding currently targets green and blue felt. Manual calibration establishes table geometry when color segmentation fails, but it cannot recover occluded or out-of-frame rails.

The physics model treats balls as equal-mass disks on a flat 2D surface with a configurable restitution approximation. It does not model spin, cloth friction, throw, cue impact, pocket geometry in detail, or calibrated table speed. Estimated lines are not guaranteed shot outcomes. A physical camera view of the table is required; the earlier screen-capture stub was removed because it did not produce frames and is not part of this CameraX pipeline.

## Build

Use Android Studio with JDK 17, Android SDK Platform 35, and the SDK build tools installed. Then run:

```sh
./gradlew :core:test
./gradlew :app:assembleDebug
```

The app uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, CameraX 1.5.2, and OpenCV 4.13.0. Gradle wrapper is included. Android builds need a local SDK path (`ANDROID_HOME` or an untracked `local.properties`); no machine-specific SDK path is committed.

## Tests

- `:core:test` covers YUV plane packing with row/pixel strides, perspective transforms in both directions, calibration degeneracy, track identity/loss recovery, frame throttling, collision paths, rebound behavior, and candidate pocket routes.
- `:app:connectedDebugAndroidTest` includes a synthetic pixel-frame test for native OpenCV table and ball detection. It requires an emulator or Android device with a supported ABI. Hosted instrumentation is not yet verified: API 35 AOSP produced an app startup ANR under high host CPU pressure (0/0 tests), while API 30 failed to start its emulator console and ADB reported API level 1 (also 0/0 tests). CI now retries API 35 with four emulator cores and 4 GB RAM. Native OpenCV still needs a completed device test.
- Real-camera testing requires a table with the full playing surface visible, even lighting, focus, and the camera mounted or held steady above the table. Grant camera permission, tap **Calibrate table** if automatic calibration is not confident, then tap the four corners clockwise beginning along the longest rail. Keep balls separated while checking detection and ID stability; move the cue ball to verify vectors and collision paths. Try both orientations and front/rear camera before relying on measurements.

## Project structure

- `app/`: CameraX lifecycle, YUV-to-OpenCV adapter, injectable analysis engine, felt boundary detector, ball-circle detector, and overlay UI.
- `core/`: Android-free calibration, ball-color classifier, tracking, geometry, physics, analysis data, and unit tests.

# POOL SHITO AI implementation roadmap

This roadmap reflects the Android scaffold that was inspected before implementation: a programmatic launcher screen, an inactive MediaProjection service, a small reflection-geometry helper, AGP 8.7.3, Kotlin 2.0.21, and no camera, CV, or test modules.

## Implemented in this pass

1. **CameraX capture:** lifecycle-bound preview and analysis, camera permission flow, rear/front selection, orientation handling, latest-frame backpressure, and 360p/720p plus 5/8 fps settings.
2. **Frame processing:** stride-aware YUV_420_888 to NV21 packing, OpenCV RGB conversion, frame rotation/mirroring, reusable full-frame Mats, and end-to-end latency reporting.
3. **Table and ball vision:** green/blue felt segmentation and quadrilateral detection; manual four-corner fallback; Hough-circle centers/radii; felt-region rejection; conservative HSV cue/solid/stripe classification with `UNKNOWN` for ambiguous colors.
4. **Tracking:** gated nearest-neighbor association, constant-velocity Kalman filtering, stable IDs, missed-frame retention, and pixel-space velocity estimates.
5. **Calibration:** long-edge-aware 2:1 table coordinates and forward/reverse projective homographies.
6. **Physics:** equal-mass collision approximation, radius-aware cushion rebounds, clear stationary cue-to-ball-to-pocket candidate geometry, and text that distinguishes suggestions from predictions.
7. **Visualization and UI:** real frame-derived outlines, IDs, confidence, motion arrows, table perimeter, trajectory lines, calibration status, permission/error guidance, and working camera/calibration/performance controls.
8. **Verification:** Android-free unit tests and an Android instrumentation test that draws a test frame and checks real OpenCV table/ball detections.

## Remaining verification and data-driven follow-up

1. Hosted CI now passes `:core:test`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; it uploads the debug APK as a workflow artifact rather than committing compiled output.
2. Run `./gradlew :app:connectedDebugAndroidTest` on a physical Android device or a stable local emulator. GitHub Actions API 35 AOSP instrumentation passed 1/1 OpenCV pixel-frame test with four emulator cores and 4 GB RAM. Earlier emulator startup failures were superseded by this successful run.
3. Test on real green and blue tables in portrait and landscape. Record missed/false circles, cue classification rate, latency, and track-ID continuity at both processing resolutions.
4. Tune HSV, Hough, and association thresholds from recorded real frames. For red/black felt, use manual calibration; automatic cloth-color segmentation is currently limited to green/blue.
5. Add learned ball classification only if field recordings show the classical classifier is insufficient and a redistributable model plus its license can be verified. No model is currently required or bundled.

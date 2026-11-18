# Pool Shito AI — Android starter

This is the initial Android Studio project scaffold for a consent-based pool-table screen analysis assistant.

## Included
- Kotlin Android application shell
- User-approved MediaProjection permission flow
- Foreground service foundation for screen capture
- Overlay permission entry point
- Initial 2D geometry helpers for angles and cushion reflection

## Not implemented yet
- ImageReader / frame pipeline
- OpenCV integration and table calibration
- Ball detection, classification, and tracking
- Cue-ball / object-ball collision simulation
- Trajectory overlay rendering
- Device and game-specific validation

## Build
Open this folder in Android Studio (JDK 17, Android Gradle Plugin 8.7.3), allow Gradle sync, then run on an Android device.

The app does not bypass game protections or interact with game servers. Screen capture requires the Android system consent prompt.

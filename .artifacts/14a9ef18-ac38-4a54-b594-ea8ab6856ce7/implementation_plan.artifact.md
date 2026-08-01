# Implementation Plan - Fix MainActivity Crash on Startup

The application is experiencing a `FATAL EXCEPTION: main` during startup, specifically at `MainActivity.onCreate` when calling `enableEdgeToEdge()`. The root cause is `java.lang.RuntimeException: Window couldn't find content container view`, which indicates an incompatibility between the Activity's theme and modern Android window decor features.

## Proposed Changes

### [Component] Build Configuration
#### [MODIFY] [build.gradle.kts](file:///Users/luxor/dev/android/LeanAngleTracker/app/build.gradle.kts)
- Add `com.google.android.material:material:1.12.0` dependency to provide modern Material3 themes for the Android Activity.

### [Component] Themes & Styles
#### [MODIFY] [themes.xml](file:///Users/luxor/dev/android/LeanAngleTracker/app/src/main/res/values/themes.xml)
- Change the parent of `Theme.LeanAngleTracker` from `@android:style/Theme.Material.Light.NoActionBar` to `Theme.Material3.DayNight.NoActionBar`.
- This ensures the Activity has a modern window setup compatible with `enableEdgeToEdge()` and the `core-splashscreen` library.

## Verification Plan

### Automated Tests
- I will attempt a gradle build to ensure the new dependency is correctly resolved.
- `gradlew assembleDebug`

### Manual Verification
- Deploy the app to the connected device `46211FDAP000U7`.
- Check `logcat` to ensure no fatal exceptions occur during startup.
- Verify the splash screen appears and correctly transitions to the main Compose UI.

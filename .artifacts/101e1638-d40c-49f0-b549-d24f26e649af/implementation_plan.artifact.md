# Package Name Change

Rename the package from `com.example.leanangletracker` to `de.hasselmeyer.leanangle`.

## User Review Required

> [!IMPORTANT]
> This change involves moving almost all source files and updating every package declaration and import. It is a large-scale refactoring.
>
> After execution, a clean build and Gradle sync will be required.

## Proposed Changes

### Build Configuration

#### [MODIFY] [app/build.gradle.kts](file:///Users/luxor/dev/android/LeanAngleTracker/app/build.gradle.kts)
- Update `namespace` to `de.hasselmeyer.leanangle`.
- Update `applicationId` to `de.hasselmeyer.leanangle`.

#### [MODIFY] [AndroidManifest.xml](file:///Users/luxor/dev/android/LeanAngleTracker/app/src/main/AndroidManifest.xml)
- Update any hardcoded package names if they exist (though `namespace` usually handles it).

#### [MODIFY] [proguard-rules.pro](file:///Users/luxor/dev/android/LeanAngleTracker/app/proguard-rules.pro)
- Update class references in keep rules.

### Source Code Refactoring

#### [RENAME] Directory Structure
- `app/src/main/java/com/example/leanangletracker` -> `app/src/main/java/de/hasselmeyer/leanangle`
- `app/src/androidTest/java/com/example/leanangletracker` -> `app/src/androidTest/java/de/hasselmeyer/leanangle`
- `app/src/test/java/com/example/leanangletracker` -> `app/src/test/java/de/hasselmeyer/leanangle`

#### [MODIFY] All Kotlin Files
- Update `package` declarations from `com.example.leanangletracker` to `de.hasselmeyer.leanangle`.
- Update `import` statements from `com.example.leanangletracker` to `de.hasselmeyer.leanangle`.
- Update hardcoded string constants that contain the old package name (e.g., Intent actions).

## Verification Plan

### Automated Tests
- Run `gradle build` to ensure the project compiles and the new R class is generated correctly.
- Run all unit tests: `./gradlew test`.
- Run all instrumentation tests: `./gradlew connectedCheck` (if a device is available).

### Manual Verification
- Verify that the app can be deployed and run on a device.
- Check if resources (strings, layouts, etc.) are still correctly referenced.
